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

import com.google.ai.edge.gallery.endpoint.DEFAULT_ENDPOINT_HOST
import com.google.ai.edge.gallery.endpoint.DEFAULT_ENDPOINT_MAX_QUEUE_SIZE
import com.google.ai.edge.gallery.endpoint.DEFAULT_ENDPOINT_PORT
import com.google.ai.edge.gallery.endpoint.DEFAULT_ENDPOINT_REQUEST_TIMEOUT_MS
import com.google.ai.edge.gallery.data.RuntimeType

enum class EndpointServerState {
  STOPPED,
  STARTING,
  RUNNING,
  STOPPING,
  ERROR,
}

data class EndpointAvailableModel(
  val name: String,
  val displayName: String,
  val runtimeType: RuntimeType,
)

data class EndpointUiState(
  val serverState: EndpointServerState = EndpointServerState.STOPPED,
  val host: String = DEFAULT_ENDPOINT_HOST,
  val port: Int = DEFAULT_ENDPOINT_PORT,
  val allowStreaming: Boolean = true,
  val maxQueueSize: Int = DEFAULT_ENDPOINT_MAX_QUEUE_SIZE,
  val requestTimeoutMs: Long = DEFAULT_ENDPOINT_REQUEST_TIMEOUT_MS,
  val availableModels: List<EndpointAvailableModel> = listOf(),
  val selectedModelName: String = "",
  val isSelectedModelReady: Boolean = false,
  val authToken: String = "",
  val tokenCreatedAtMs: Long = 0,
  val totalRequestCount: Int = 0,
  val failedRequestCount: Int = 0,
  val cancelledRequestCount: Int = 0,
  val timedOutRequestCount: Int = 0,
  val activeRequestCount: Int = 0,
  val queueDepth: Int = 0,
  val averageLatencyMs: Long = 0,
  val serverStartedAtMs: Long = 0,
  val readySinceAtMs: Long = 0,
  val lastRequestId: String = "",
  val lastError: String = "",
) {
  val baseUrl: String
    get() = "http://$host:$port"
}
