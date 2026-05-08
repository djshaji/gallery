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

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class SseWriter(
  private val outputStream: OutputStream,
  private val onClosed: () -> Unit = {},
) {
  private val closed = AtomicBoolean(false)
  private val onClosedInvoked = AtomicBoolean(false)

  fun writeData(payload: String) {
    writeEvent("data: $payload\n\n")
  }

  fun writeDone() {
    writeData("[DONE]")
  }

  fun writeError(payload: String) {
    writeData(payload)
  }

  fun close() {
    if (closed.compareAndSet(false, true)) {
      try {
        outputStream.flush()
      } finally {
        outputStream.close()
      }
    }
  }

  private fun writeEvent(event: String) {
    if (closed.get()) {
      return
    }
    synchronized(this) {
      if (closed.get()) {
        return
      }
      try {
        outputStream.write(event.toByteArray(Charsets.UTF_8))
        outputStream.flush()
      } catch (_: IOException) {
        if (closed.compareAndSet(false, true)) {
          try {
            outputStream.close()
          } finally {
            invokeOnClosed()
          }
        }
      }
    }
  }

  private fun invokeOnClosed() {
    if (onClosedInvoked.compareAndSet(false, true)) {
      onClosed()
    }
  }
}
