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

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class StreamingResponseBody {
  private object EndOfStream

  private val closed = AtomicBoolean(false)
  private val chunks = LinkedBlockingQueue<Any>()

  fun asInputStream(): InputStream {
    return object : InputStream() {
      private var currentChunk: ByteArray? = null
      private var currentOffset = 0

      override fun read(): Int {
        val singleByte = ByteArray(1)
        val read = read(singleByte, 0, 1)
        return if (read == -1) -1 else singleByte[0].toInt() and 0xFF
      }

      override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
          return 0
        }

        while (currentChunk == null || currentOffset >= currentChunk!!.size) {
          val nextChunk = chunks.take()
          currentOffset = 0
          if (nextChunk === EndOfStream) {
            return -1
          }
          currentChunk = nextChunk as ByteArray
        }

        val chunk = currentChunk!!
        val bytesToCopy = minOf(length, chunk.size - currentOffset)
        System.arraycopy(chunk, currentOffset, buffer, offset, bytesToCopy)
        currentOffset += bytesToCopy
        return bytesToCopy
      }
    }
  }

  fun asOutputStream(): OutputStream {
    return object : OutputStream() {
      override fun write(oneByte: Int) {
        write(byteArrayOf(oneByte.toByte()), 0, 1)
      }

      override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (closed.get()) {
          return
        }
        if (length <= 0) {
          return
        }
        val payload = ByteArray(length)
        System.arraycopy(buffer, offset, payload, 0, length)
        chunks.put(payload)
      }

      override fun flush() {}

      override fun close() {
        closeWriter()
      }
    }
  }

  fun closeWriter() {
    if (closed.compareAndSet(false, true)) {
      chunks.put(EndOfStream)
    }
  }
}
