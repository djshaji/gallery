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

import com.google.gson.annotations.SerializedName

data class ChatMessageRequest(val role: String? = null, val content: String? = null)

data class ChatCompletionRequest(
  val model: String? = null,
  val messages: List<ChatMessageRequest> = listOf(),
  val temperature: Float? = null,
  @SerializedName("top_p") val topP: Float? = null,
  @SerializedName("max_tokens") val maxTokens: Int? = null,
  val stream: Boolean? = null,
)

data class OpenAiErrorBody(
  val error: OpenAiError,
)

data class OpenAiError(
  val message: String,
  val type: String,
)

data class HealthzResponse(
  val status: String,
  val server_state: String,
  val selected_model: String,
  val ready: Boolean,
  val queue_depth: Int,
  val active_requests: Int,
  val streaming_enabled: Boolean,
  val request_timeout_ms: Long,
)

data class ModelsResponse(
  @SerializedName("object") val objectType: String = "list",
  val data: List<ModelResponseItem>,
)

data class ModelResponseItem(
  val id: String,
  @SerializedName("object") val objectType: String = "model",
  val owned_by: String = "google-ai-edge-gallery",
)

data class ChatCompletionResponse(
  val id: String,
  @SerializedName("object") val objectType: String = "chat.completion",
  val created: Long,
  val model: String,
  val choices: List<ChatCompletionChoice>,
)

data class ChatCompletionChoice(
  val index: Int,
  val message: ChatCompletionMessage,
  val finish_reason: String,
)

data class ChatCompletionMessage(
  val role: String,
  val content: String,
)

data class ChatCompletionChunkResponse(
  val id: String,
  @SerializedName("object") val objectType: String = "chat.completion.chunk",
  val created: Long,
  val model: String,
  val choices: List<ChatCompletionChunkChoice>,
)

data class ChatCompletionChunkChoice(
  val index: Int,
  val delta: ChatCompletionChunkDelta,
  val finish_reason: String? = null,
)

data class ChatCompletionChunkDelta(
  val role: String? = null,
  val content: String? = null,
)
