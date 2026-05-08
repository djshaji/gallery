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

package com.google.ai.edge.gallery.endpoint.data

import com.google.ai.edge.gallery.endpoint.DEFAULT_ENDPOINT_MAX_QUEUE_SIZE
import com.google.ai.edge.gallery.endpoint.DEFAULT_ENDPOINT_PORT
import com.google.ai.edge.gallery.endpoint.DEFAULT_ENDPOINT_REQUEST_TIMEOUT_MS
import com.google.ai.edge.gallery.endpoint.ENDPOINT_AUTH_TOKEN_SECRET_KEY
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.proto.EndpointSettings
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class DefaultEndpointRepository @Inject constructor(private val dataStoreRepository: DataStoreRepository) {
  private val availableModelsByName = linkedMapOf<String, Model>()
  private val initialSettings = dataStoreRepository.readEndpointSettings()
  private val _uiState =
    MutableStateFlow(
      EndpointUiState(
        port = initialSettings.port.takeIf { it > 0 } ?: DEFAULT_ENDPOINT_PORT,
        allowStreaming = true,
        maxQueueSize = initialSettings.maxQueueSize.takeIf { it > 0 } ?: DEFAULT_ENDPOINT_MAX_QUEUE_SIZE,
        requestTimeoutMs =
          initialSettings.requestTimeoutMs.takeIf { it > 0 } ?: DEFAULT_ENDPOINT_REQUEST_TIMEOUT_MS,
        selectedModelName = initialSettings.selectedModelName,
        authToken = dataStoreRepository.readSecret(ENDPOINT_AUTH_TOKEN_SECRET_KEY) ?: "",
        tokenCreatedAtMs = initialSettings.tokenCreatedAtMs,
      )
    )

  val uiState = _uiState.asStateFlow()

  init {
    persistEndpointSettings()
  }

  @Synchronized
  fun syncAvailableModels(models: List<Model>) {
    availableModelsByName.clear()
    for (model in models) {
      availableModelsByName[model.name] = model
    }

    var selectedModelName = uiState.value.selectedModelName
    if (selectedModelName.isNotEmpty() && !availableModelsByName.containsKey(selectedModelName)) {
      selectedModelName = ""
      dataStoreRepository.saveEndpointSelectedModelName("")
    }

    _uiState.update {
      it.copy(
        availableModels =
          models.map { model ->
            EndpointAvailableModel(
              name = model.name,
              displayName = model.displayName.ifEmpty { model.name },
              runtimeType = model.runtimeType,
            )
          },
        selectedModelName = selectedModelName,
        isSelectedModelReady = selectedModelName.isNotEmpty() && it.isSelectedModelReady,
      )
    }
  }

  @Synchronized
  fun selectModel(modelName: String) {
    dataStoreRepository.saveEndpointSelectedModelName(modelName)
    _uiState.update { it.copy(selectedModelName = modelName, isSelectedModelReady = false) }
  }

  @Synchronized
  fun getSelectedModel(): Model? {
    return availableModelsByName[uiState.value.selectedModelName]
  }

  @Synchronized
  fun getAvailableModels(): List<Model> {
    return availableModelsByName.values.toList()
  }

  @Synchronized
  fun getSelectedModelName(): String {
    return uiState.value.selectedModelName
  }

  fun ensureAuthToken(): String {
    val existing = dataStoreRepository.readSecret(ENDPOINT_AUTH_TOKEN_SECRET_KEY)
    if (!existing.isNullOrEmpty()) {
      _uiState.update {
        it.copy(
          authToken = existing,
          tokenCreatedAtMs = dataStoreRepository.readEndpointTokenCreatedAt(),
        )
      }
      return existing
    }
    return rotateAuthToken()
  }

  fun rotateAuthToken(): String {
    val newToken = UUID.randomUUID().toString().replace("-", "")
    dataStoreRepository.saveSecret(ENDPOINT_AUTH_TOKEN_SECRET_KEY, newToken)
    val createdAtMs = System.currentTimeMillis()
    dataStoreRepository.saveEndpointTokenCreatedAt(createdAtMs)
    _uiState.update { it.copy(authToken = newToken, tokenCreatedAtMs = createdAtMs) }
    persistEndpointSettings()
    return newToken
  }

  fun updateServerState(serverState: EndpointServerState, lastError: String = uiState.value.lastError) {
    val now = System.currentTimeMillis()
    _uiState.update {
      it.copy(
        serverState = serverState,
        serverStartedAtMs =
          when (serverState) {
            EndpointServerState.STARTING -> now
            EndpointServerState.STOPPED -> 0
            else -> it.serverStartedAtMs
          },
        readySinceAtMs =
          when (serverState) {
            EndpointServerState.STOPPED, EndpointServerState.ERROR -> 0
            else -> it.readySinceAtMs
          },
        lastError =
          if (serverState == EndpointServerState.ERROR) {
            lastError
          } else if (serverState == EndpointServerState.RUNNING || serverState == EndpointServerState.STOPPED) {
            ""
          } else {
            lastError
          },
      )
    }
  }

  fun setSelectedModelReady(ready: Boolean) {
    val now = System.currentTimeMillis()
    _uiState.update {
      it.copy(
        isSelectedModelReady = ready,
        readySinceAtMs = if (ready) now else 0,
      )
    }
  }

  fun setPort(port: Int) {
    dataStoreRepository.saveEndpointPort(port)
    _uiState.update { it.copy(port = port) }
    persistEndpointSettings()
  }

  fun setQueueState(queueDepth: Int, activeRequestCount: Int) {
    _uiState.update {
      it.copy(
        queueDepth = queueDepth.coerceAtLeast(0),
        activeRequestCount = activeRequestCount.coerceAtLeast(0),
      )
    }
  }

  fun resetRuntimeStats() {
    _uiState.update {
      it.copy(
        totalRequestCount = 0,
        failedRequestCount = 0,
        cancelledRequestCount = 0,
        timedOutRequestCount = 0,
        activeRequestCount = 0,
        queueDepth = 0,
        averageLatencyMs = 0,
        lastRequestId = "",
        lastError = "",
      )
    }
  }

  fun recordRequestStarted(requestId: String) {
    _uiState.update {
      it.copy(
        totalRequestCount = it.totalRequestCount + 1,
        lastRequestId = requestId,
      )
    }
  }

  fun recordRequestSucceeded(latencyMs: Long) {
    _uiState.update {
      val completedCount =
        it.totalRequestCount - it.failedRequestCount - it.cancelledRequestCount - it.timedOutRequestCount
      val nextCompletedCount = completedCount.coerceAtLeast(0)
      val nextAverageLatency =
        if (nextCompletedCount <= 0) {
          latencyMs
        } else {
          ((it.averageLatencyMs * (nextCompletedCount - 1)) + latencyMs) / nextCompletedCount
        }
      it.copy(averageLatencyMs = nextAverageLatency.coerceAtLeast(0))
    }
  }

  fun recordRequestFailed(errorMessage: String) {
    _uiState.update {
      it.copy(
        failedRequestCount = it.failedRequestCount + 1,
        lastError = errorMessage,
      )
    }
  }

  fun recordRequestCancelled(errorMessage: String) {
    _uiState.update {
      it.copy(
        cancelledRequestCount = it.cancelledRequestCount + 1,
        lastError = errorMessage,
      )
    }
  }

  fun recordRequestTimedOut(errorMessage: String) {
    _uiState.update {
      it.copy(
        timedOutRequestCount = it.timedOutRequestCount + 1,
        failedRequestCount = it.failedRequestCount + 1,
        lastError = errorMessage,
      )
    }
  }

  fun clearLastError() {
    _uiState.update { it.copy(lastError = "") }
  }

  fun getEndpointSettings(): EndpointSettings {
    val current = dataStoreRepository.readEndpointSettings()
    return current.toBuilder().normalizeFromUiState(uiState.value).build()
  }

  private fun persistEndpointSettings() {
    dataStoreRepository.saveEndpointSettings(getEndpointSettings())
  }

  private fun EndpointSettings.Builder.normalizeFromUiState(uiState: EndpointUiState): EndpointSettings.Builder {
    return this
      .setPort(uiState.port)
      .setSelectedModelName(uiState.selectedModelName)
      .setAllowStreaming(true)
      .setMaxQueueSize(uiState.maxQueueSize.coerceAtLeast(1))
      .setRequestTimeoutMs(uiState.requestTimeoutMs.coerceAtLeast(DEFAULT_ENDPOINT_REQUEST_TIMEOUT_MS))
      .setTokenCreatedAtMs(uiState.tokenCreatedAtMs)
  }
}
