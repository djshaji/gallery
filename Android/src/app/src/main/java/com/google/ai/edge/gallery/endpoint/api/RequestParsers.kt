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

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import java.io.File

object RequestParsers {
  private val gson = Gson()

  fun readRequestBody(session: IHTTPSession): String {
    val files = mutableMapOf<String, String>()
    session.parseBody(files)
    val postData = files["postData"] ?: return ""
    val tempFile = File(postData)
    return if (tempFile.exists()) tempFile.readText() else postData
  }

  fun parseChatCompletionRequest(body: String): ChatCompletionRequest {
    if (body.isBlank()) {
      throw EndpointApiException(
        statusCode = 400,
        message = "Request body must not be empty.",
        errorType = "invalid_request_error",
      )
    }
    return try {
      gson.fromJson(body, ChatCompletionRequest::class.java)
    } catch (_: JsonSyntaxException) {
      throw EndpointApiException(
        statusCode = 400,
        message = "Request body must be valid JSON.",
        errorType = "invalid_request_error",
      )
    }
  }

  fun extractSystemPrompt(messages: List<ChatMessageRequest>): String {
    return messages
      .filter { it.role == "system" && !it.content.isNullOrBlank() }
      .joinToString("\n\n") { it.content!!.trim() }
  }

  fun buildPrompt(messages: List<ChatMessageRequest>): String {
    return messages
      .filter { it.role != "system" && !it.content.isNullOrBlank() }
      .joinToString("\n\n") { "${it.role?.uppercase()}: ${it.content!!.trim()}" }
  }
}
