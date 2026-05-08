/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.endpoint.orchestration

import android.content.Context
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.endpoint.api.ChatCompletionRequest
import com.google.ai.edge.gallery.endpoint.api.EndpointApiException
import com.google.ai.edge.gallery.endpoint.api.LocalApiServer
import com.google.ai.edge.gallery.endpoint.api.RequestParsers
import com.google.ai.edge.gallery.endpoint.api.ResponseMappers
import com.google.ai.edge.gallery.endpoint.api.SseWriter
import com.google.ai.edge.gallery.endpoint.data.DefaultEndpointRepository
import com.google.ai.edge.gallery.endpoint.data.EndpointServerState
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

@Singleton
class EndpointSessionManager
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val endpointRepository: DefaultEndpointRepository,
  private val requestQueue: EndpointRequestQueue,
) {
  private data class CompletionResult(val modelName: String, val content: String)

  private data class ActiveRequestExecution(
    val requestId: String,
    val model: Model,
    val completed: CompletableDeferred<String>,
    val cancelled: AtomicBoolean = AtomicBoolean(false),
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val activeExecutionMutex = Mutex()
  private var activeExecution: ActiveRequestExecution? = null
  private var localApiServer: LocalApiServer? = null
  private var initializedModelName: String? = null
  private var ownsModelInitialization = false

  suspend fun startServer(): Boolean {
    if (localApiServer != null) {
      return true
    }

    val selectedModel = endpointRepository.getSelectedModel() ?: run {
      endpointRepository.updateServerState(
        EndpointServerState.ERROR,
        "Select a downloaded model before starting the endpoint.",
      )
      return false
    }

    endpointRepository.resetRuntimeStats()
    endpointRepository.updateServerState(EndpointServerState.STARTING)
    endpointRepository.ensureAuthToken()
    endpointRepository.clearLastError()

    return try {
      initializeModel(selectedModel)
      localApiServer =
        LocalApiServer(
          port = endpointRepository.uiState.value.port,
          authTokenProvider = { endpointRepository.ensureAuthToken() },
          healthHandler = {
            val uiState = endpointRepository.uiState.value
            ResponseMappers.healthJson(
              serverState = uiState.serverState.name.lowercase(),
              selectedModelName = endpointRepository.getSelectedModelName(),
              ready = uiState.isSelectedModelReady,
              queueDepth = uiState.queueDepth,
              activeRequests = uiState.activeRequestCount,
              streamingEnabled = uiState.allowStreaming,
              requestTimeoutMs = uiState.requestTimeoutMs,
            )
          },
          modelsHandler = {
            ResponseMappers.modelsJson(endpointRepository.getAvailableModels())
          },
          chatCompletionHandler = { requestId, request ->
            runChatCompletion(requestId, request)
          },
          streamingChatCompletionHandler = { requestId, request, writer ->
            streamChatCompletion(requestId, request, writer)
          },
          cancelRequestHandler = { requestId ->
            cancelRequest(requestId)
          },
        )
      localApiServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
      endpointRepository.updateServerState(EndpointServerState.RUNNING)
      true
    } catch (e: Exception) {
      endpointRepository.setSelectedModelReady(false)
      endpointRepository.updateServerState(
        EndpointServerState.ERROR,
        e.message ?: "Failed to start endpoint server.",
      )
      false
    }
  }

  suspend fun stopServer() {
    if (
      localApiServer == null &&
        initializedModelName == null &&
        endpointRepository.uiState.value.serverState == EndpointServerState.ERROR
    ) {
      return
    }

    endpointRepository.updateServerState(EndpointServerState.STOPPING)
    cancelActiveRequest("Endpoint stopped.")
    requestQueue.clear()
    localApiServer?.stop()
    localApiServer = null

    val model = endpointRepository.getSelectedModel()
    if (model != null && initializedModelName == model.name && ownsModelInitialization) {
      cleanUpModel(model)
    }
    initializedModelName = null
    ownsModelInitialization = false
    endpointRepository.setSelectedModelReady(false)
    endpointRepository.resetRuntimeStats()
    endpointRepository.updateServerState(EndpointServerState.STOPPED)
  }

  suspend fun cancelRequest(requestId: String): Boolean {
    if (requestQueue.cancel(requestId)) {
      return true
    }
    return cancelActiveRequest(requestId = requestId, reason = "Request cancelled by client disconnect.")
  }

  private suspend fun initializeModel(model: Model) {
    if (model.instance != null) {
      initializedModelName = model.name
      ownsModelInitialization = false
      endpointRepository.setSelectedModelReady(true)
      return
    }

    val initialized = CompletableDeferred<Unit>()
    model.runtimeHelper.initialize(
      context = context,
      model = model,
      taskId = BuiltInTaskId.LLM_CHAT,
      supportImage = false,
      supportAudio = false,
      onDone = { error ->
        if (error.isEmpty()) {
          initialized.complete(Unit)
        } else {
          initialized.completeExceptionally(IllegalStateException(error))
        }
      },
      systemInstruction = Contents.of(""),
      coroutineScope = scope,
    )
    initialized.await()
    initializedModelName = model.name
    ownsModelInitialization = true
    endpointRepository.setSelectedModelReady(true)
  }

  private suspend fun cleanUpModel(model: Model) {
    val cleaned = CompletableDeferred<Unit>()
    model.runtimeHelper.cleanUp(model = model) {
      cleaned.complete(Unit)
    }
    cleaned.await()
  }

  suspend fun runChatCompletion(requestId: String, request: ChatCompletionRequest): String {
    val result = executeChatCompletion(requestId = requestId, request = request, streamWriter = null)
    return ResponseMappers.chatCompletionJson(
      modelName = result.modelName,
      content = result.content,
      requestId = requestId,
    )
  }

  suspend fun streamChatCompletion(
    requestId: String,
    request: ChatCompletionRequest,
    writer: SseWriter,
  ) {
    executeChatCompletion(requestId = requestId, request = request, streamWriter = writer)
  }

  private suspend fun executeChatCompletion(
    requestId: String,
    request: ChatCompletionRequest,
    streamWriter: SseWriter?,
  ): CompletionResult {
    val selectedModel = validateRequest(request)
    val maxQueueSize = endpointRepository.uiState.value.maxQueueSize
    val requestTimeoutMs = endpointRepository.uiState.value.requestTimeoutMs

    val acquired =
      try {
        requestQueue.acquire(requestId = requestId, maxQueueSize = maxQueueSize)
      } catch (_: CancellationException) {
        throw EndpointApiException(
          statusCode = 503,
          message = "Endpoint stopped before the request could start.",
          errorType = "server_error",
        )
      }

    if (!acquired) {
      throw EndpointApiException(
        statusCode = 429,
        message = "The endpoint queue is full. Try again after the current request finishes.",
        errorType = "rate_limit_error",
      )
    }

    endpointRepository.recordRequestStarted(requestId)
    val startedAtMs = System.currentTimeMillis()
    val previousConfigValues = selectedModel.configValues.toMap()
    val responseText = StringBuilder()
    val completed = CompletableDeferred<String>()
    val execution =
      ActiveRequestExecution(
        requestId = requestId,
        model = selectedModel,
        completed = completed,
      )

    return try {
      activeExecutionMutex.withLock {
        activeExecution = execution
      }

      if (request.maxTokens != null) {
        selectedModel.configValues =
          selectedModel.configValues + (ConfigKeys.MAX_TOKENS.label to request.maxTokens)
      }
      if (request.temperature != null) {
        selectedModel.configValues =
          selectedModel.configValues + (ConfigKeys.TEMPERATURE.label to request.temperature)
      }
      if (request.topP != null) {
        selectedModel.configValues =
          selectedModel.configValues + (ConfigKeys.TOPP.label to request.topP)
      }

      val systemPrompt = RequestParsers.extractSystemPrompt(request.messages)
      selectedModel.runtimeHelper.resetConversation(
        model = selectedModel,
        supportImage = false,
        supportAudio = false,
        systemInstruction = Contents.of(systemPrompt),
      )

      val prompt = RequestParsers.buildPrompt(request.messages)
      if (prompt.isBlank()) {
        throw EndpointApiException(
          statusCode = 400,
          message = "At least one non-system message is required.",
          errorType = "invalid_request_error",
        )
      }

      if (streamWriter != null) {
        streamWriter.writeData(
          ResponseMappers.chatCompletionChunkJson(
            requestId = requestId,
            modelName = selectedModel.name,
            includeRole = true,
          )
        )
      }

      val resultListener = resultListener@{ partialResult: String, done: Boolean, _: String? ->
        if (execution.cancelled.get()) {
          if (!completed.isCompleted) {
            completed.completeExceptionally(CancellationException("Request cancelled."))
          }
          return@resultListener
        }

        if (partialResult.isNotEmpty()) {
          responseText.append(partialResult)
          if (streamWriter != null) {
            streamWriter.writeData(
              ResponseMappers.chatCompletionChunkJson(
                requestId = requestId,
                modelName = selectedModel.name,
                contentDelta = partialResult,
              )
            )
          }
        }

        if (done && !completed.isCompleted) {
          completed.complete(responseText.toString())
        }
      }

      val cleanUpListener = {
        if (!completed.isCompleted) {
          if (execution.cancelled.get()) {
            completed.completeExceptionally(CancellationException("Request cancelled."))
          } else {
            completed.completeExceptionally(
              IllegalStateException("Inference stopped before completion."),
            )
          }
        }
      }

      val errorListener: (String) -> Unit = { errorMessage ->
        if (!completed.isCompleted) {
          completed.completeExceptionally(
            IllegalStateException(errorMessage.ifBlank { "Inference failed." }),
          )
        }
      }

      val finalResponse =
        withTimeout(requestTimeoutMs) {
          selectedModel.runtimeHelper.runInference(
            model = selectedModel,
            input = prompt,
            resultListener = resultListener,
            cleanUpListener = cleanUpListener,
            onError = errorListener,
            coroutineScope = scope,
          )
          completed.await()
        }

      if (streamWriter != null) {
        streamWriter.writeData(
          ResponseMappers.chatCompletionChunkJson(
            requestId = requestId,
            modelName = selectedModel.name,
            finishReason = "stop",
          )
        )
        streamWriter.writeDone()
      }

      endpointRepository.recordRequestSucceeded(System.currentTimeMillis() - startedAtMs)
      endpointRepository.clearLastError()
      CompletionResult(modelName = selectedModel.name, content = finalResponse)
    } catch (e: TimeoutCancellationException) {
      execution.cancelled.set(true)
      selectedModel.runtimeHelper.stopResponse(selectedModel)
      endpointRepository.recordRequestTimedOut(
        "Request timed out after ${requestTimeoutMs}ms.",
      )
      throw EndpointApiException(
        statusCode = 408,
        message = "Request timed out after ${requestTimeoutMs}ms.",
        errorType = "timeout_error",
      )
    } catch (e: CancellationException) {
      endpointRepository.recordRequestCancelled("Request was cancelled.")
      throw EndpointApiException(
        statusCode = 503,
        message = "Request was cancelled.",
        errorType = "server_error",
      )
    } catch (e: EndpointApiException) {
      endpointRepository.recordRequestFailed(e.message ?: "Request failed.")
      throw e
    } catch (e: Exception) {
      endpointRepository.recordRequestFailed(e.message ?: "Chat completion failed.")
      throw EndpointApiException(
        statusCode = 500,
        message = e.message ?: "Chat completion failed.",
        errorType = "server_error",
      )
    } finally {
      selectedModel.configValues = previousConfigValues
      activeExecutionMutex.withLock {
        if (activeExecution?.requestId == requestId) {
          activeExecution = null
        }
      }
      requestQueue.release(requestId)
    }
  }

  private fun validateRequest(request: ChatCompletionRequest): Model {
    val uiState = endpointRepository.uiState.value
    if (request.stream == true && !uiState.allowStreaming) {
      throw EndpointApiException(
        statusCode = 400,
        message = "Streaming is disabled for this endpoint.",
        errorType = "invalid_request_error",
      )
    }

    val selectedModel =
      endpointRepository.getSelectedModel()
        ?: throw EndpointApiException(
          statusCode = 503,
          message = "No serving model is selected.",
          errorType = "server_error",
        )

    if (!uiState.isSelectedModelReady) {
      throw EndpointApiException(
        statusCode = 503,
        message = "Selected model is not ready yet.",
        errorType = "server_error",
      )
    }

    if (!request.model.isNullOrEmpty() && request.model != selectedModel.name) {
      throw EndpointApiException(
        statusCode = 400,
        message =
          "The endpoint serves one selected model at a time. Select '${request.model}' in the dashboard first.",
        errorType = "invalid_request_error",
      )
    }

    if (request.messages.isEmpty()) {
      throw EndpointApiException(
        statusCode = 400,
        message = "At least one chat message is required.",
        errorType = "invalid_request_error",
      )
    }

    request.messages.forEach { message ->
      val role = message.role ?: ""
      if (role != "system" && role != "user" && role != "assistant") {
        throw EndpointApiException(
          statusCode = 400,
          message = "Unsupported message role '$role'.",
          errorType = "invalid_request_error",
        )
      }
      if (message.content.isNullOrBlank()) {
        throw EndpointApiException(
          statusCode = 400,
          message = "Chat message content must be a non-empty string.",
          errorType = "invalid_request_error",
        )
      }
    }

    return selectedModel
  }

  private suspend fun cancelActiveRequest(reason: String): Boolean {
    return cancelActiveRequest(requestId = null, reason = reason)
  }

  private suspend fun cancelActiveRequest(requestId: String?, reason: String): Boolean {
    val execution =
      activeExecutionMutex.withLock {
        val current = activeExecution
        if (current != null && (requestId == null || current.requestId == requestId)) {
          current.cancelled.set(true)
          if (!current.completed.isCompleted) {
            current.completed.completeExceptionally(CancellationException(reason))
          }
          current
        } else {
          null
        }
      } ?: return false

    execution.model.runtimeHelper.stopResponse(execution.model)
    return true
  }
}
