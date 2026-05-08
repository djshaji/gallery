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

package com.google.ai.edge.gallery.endpoint.api

import com.google.ai.edge.gallery.data.Model
import com.google.gson.Gson

object ResponseMappers {
  private val gson = Gson()

  fun toJson(value: Any): String {
    return gson.toJson(value)
  }

  fun errorJson(message: String, type: String): String {
    return toJson(OpenAiErrorBody(error = OpenAiError(message = message, type = type)))
  }

  fun healthJson(
    serverState: String,
    selectedModelName: String,
    ready: Boolean,
    queueDepth: Int,
    activeRequests: Int,
    streamingEnabled: Boolean,
    requestTimeoutMs: Long,
  ): String {
    return toJson(
      HealthzResponse(
        status =
          when {
            ready -> "ok"
            serverState == "stopped" -> "stopped"
            serverState == "error" -> "error"
            else -> "starting"
          },
        server_state = serverState,
        selected_model = selectedModelName,
        ready = ready,
        queue_depth = queueDepth,
        active_requests = activeRequests,
        streaming_enabled = streamingEnabled,
        request_timeout_ms = requestTimeoutMs,
      )
    )
  }

  fun modelsJson(models: List<Model>): String {
    return toJson(
      ModelsResponse(
        data =
          models.map {
            ModelResponseItem(id = it.name)
          }
      )
    )
  }

  fun chatCompletionJson(modelName: String, content: String, requestId: String): String {
    return toJson(
      ChatCompletionResponse(
        id = "chatcmpl-$requestId",
        created = System.currentTimeMillis() / 1000,
        model = modelName,
        choices =
          listOf(
            ChatCompletionChoice(
              index = 0,
              message = ChatCompletionMessage(role = "assistant", content = content),
              finish_reason = "stop",
            )
          ),
      )
    )
  }

  fun chatCompletionChunkJson(
    requestId: String,
    modelName: String,
    contentDelta: String? = null,
    includeRole: Boolean = false,
    finishReason: String? = null,
  ): String {
    return toJson(
      ChatCompletionChunkResponse(
        id = "chatcmpl-$requestId",
        created = System.currentTimeMillis() / 1000,
        model = modelName,
        choices =
          listOf(
            ChatCompletionChunkChoice(
              index = 0,
              delta =
                ChatCompletionChunkDelta(
                  role = if (includeRole) "assistant" else null,
                  content = contentDelta,
                ),
              finish_reason = finishReason,
            )
          ),
      )
    )
  }
}
