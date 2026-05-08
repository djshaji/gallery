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

import com.google.ai.edge.gallery.endpoint.DEFAULT_ENDPOINT_HOST
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response.IStatus
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LocalApiServer(
  port: Int,
  private val authTokenProvider: () -> String,
  private val healthHandler: () -> String,
  private val modelsHandler: () -> String,
  private val chatCompletionHandler: suspend (String, ChatCompletionRequest) -> String,
  private val streamingChatCompletionHandler: suspend (String, ChatCompletionRequest, SseWriter) -> Unit,
  private val cancelRequestHandler: suspend (String) -> Unit,
) : NanoHTTPD(DEFAULT_ENDPOINT_HOST, port) {
  private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun serve(session: IHTTPSession): Response {
    return try {
      when {
        session.method == Method.GET && session.uri == "/healthz" -> {
          jsonResponse(Response.Status.OK, healthHandler())
        }

        session.method == Method.GET && session.uri == "/v1/models" -> {
          if (!AuthMiddleware.isAuthorized(session, authTokenProvider())) {
            return jsonResponse(
              Response.Status.UNAUTHORIZED,
              ResponseMappers.errorJson("Missing or invalid bearer token.", "invalid_request_error"),
            )
          }
          jsonResponse(Response.Status.OK, modelsHandler())
        }

        session.method == Method.POST && session.uri == "/v1/chat/completions" -> {
          if (!AuthMiddleware.isAuthorized(session, authTokenProvider())) {
            return jsonResponse(
              Response.Status.UNAUTHORIZED,
              ResponseMappers.errorJson("Missing or invalid bearer token.", "invalid_request_error"),
            )
          }
          val body = RequestParsers.readRequestBody(session)
          val request = RequestParsers.parseChatCompletionRequest(body)
          val requestId = UUID.randomUUID().toString().replace("-", "")
          if (request.stream == true) {
            return streamResponse(requestId = requestId, request = request)
          }
          jsonResponse(Response.Status.OK, runBlocking { chatCompletionHandler(requestId, request) })
        }

        else -> {
          jsonResponse(
            Response.Status.NOT_FOUND,
            ResponseMappers.errorJson("Unknown endpoint.", "invalid_request_error"),
          )
        }
      }
    } catch (e: EndpointApiException) {
      jsonResponse(statusForCode(e.statusCode), ResponseMappers.errorJson(e.message ?: "Request failed.", e.errorType))
    } catch (e: Exception) {
      jsonResponse(
        Response.Status.INTERNAL_ERROR,
        ResponseMappers.errorJson(
          e.message ?: "Unexpected server error.",
          "server_error",
        ),
      )
    }
  }

  override fun stop() {
    serverScope.cancel()
    super.stop()
  }

  private fun jsonResponse(status: IStatus, jsonBody: String): Response {
    return newFixedLengthResponse(status, "application/json", jsonBody)
  }

  private fun streamResponse(requestId: String, request: ChatCompletionRequest): Response {
    val responseBody = StreamingResponseBody()
    val writer = SseWriter(responseBody.asOutputStream())

    serverScope.launch {
      try {
        streamingChatCompletionHandler(requestId, request, writer)
      } catch (e: EndpointApiException) {
        writer.writeError(ResponseMappers.errorJson(e.message ?: "Streaming request failed.", e.errorType))
      } catch (e: Exception) {
        writer.writeError(
          ResponseMappers.errorJson(
            e.message ?: "Unexpected streaming error.",
            "server_error",
          )
        )
      } finally {
        writer.close()
      }
    }

    return NanoHTTPD.newChunkedResponse(
        Response.Status.OK,
        "text/event-stream; charset=utf-8",
        responseBody.asInputStream(),
      )
      .apply {
        addHeader("Cache-Control", "no-cache")
        addHeader("Connection", "keep-alive")
        addHeader("X-Accel-Buffering", "no")
      }
  }

  private fun statusForCode(statusCode: Int): IStatus {
    return when (statusCode) {
      400 -> Response.Status.BAD_REQUEST
      401 -> Response.Status.UNAUTHORIZED
      404 -> Response.Status.NOT_FOUND
      408 -> Response.Status.REQUEST_TIMEOUT
      429 -> EndpointStatus(429, "Too Many Requests")
      503 -> Response.Status.SERVICE_UNAVAILABLE
      else -> Response.Status.INTERNAL_ERROR
    }
  }
}

private data class EndpointStatus(
  private val requestStatus: Int,
  private val description: String,
) : IStatus {
  override fun getRequestStatus(): Int = requestStatus

  override fun getDescription(): String = "$requestStatus $description"
}
