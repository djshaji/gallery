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

import com.google.ai.edge.gallery.endpoint.data.DefaultEndpointRepository
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class EndpointRequestQueue @Inject constructor(private val endpointRepository: DefaultEndpointRepository) {
  private data class QueuedRequest(val requestId: String, val ready: CompletableDeferred<Unit>)

  private val stateMutex = Mutex()
  private val queuedRequests = ArrayDeque<QueuedRequest>()
  private var activeRequestId: String? = null

  suspend fun acquire(requestId: String, maxQueueSize: Int): Boolean {
    var readySignal: CompletableDeferred<Unit>? = null
    var acquiredImmediately = false
    var rejected = false
    stateMutex.withLock {
      if (activeRequestId == null) {
        activeRequestId = requestId
        acquiredImmediately = true
        publishQueueStateLocked()
        return@withLock
      }

      if (queuedRequests.size >= maxQueueSize.coerceAtLeast(0)) {
        rejected = true
        publishQueueStateLocked()
        return@withLock
      }

      readySignal = CompletableDeferred()
      queuedRequests.addLast(QueuedRequest(requestId = requestId, ready = readySignal!!))
      publishQueueStateLocked()
    }

    if (acquiredImmediately) {
      return true
    }
    if (rejected) {
      return false
    }

    return try {
      readySignal!!.await()
      true
    } catch (e: CancellationException) {
      cancel(requestId)
      throw e
    }
  }

  suspend fun release(requestId: String) {
    var nextRequest: QueuedRequest? = null
    stateMutex.withLock {
      if (activeRequestId == requestId) {
        nextRequest = if (queuedRequests.isEmpty()) null else queuedRequests.removeFirst()
        activeRequestId = nextRequest?.requestId
      } else {
        val iterator = queuedRequests.iterator()
        while (iterator.hasNext()) {
          if (iterator.next().requestId == requestId) {
            iterator.remove()
            break
          }
        }
      }
      publishQueueStateLocked()
    }
    nextRequest?.ready?.complete(Unit)
  }

  suspend fun cancel(requestId: String): Boolean {
    var cancelled = false
    var deferredToCancel: CompletableDeferred<Unit>? = null
    stateMutex.withLock {
      val iterator = queuedRequests.iterator()
      while (iterator.hasNext()) {
        val queuedRequest = iterator.next()
        if (queuedRequest.requestId == requestId) {
          iterator.remove()
          deferredToCancel = queuedRequest.ready
          cancelled = true
          break
        }
      }
      publishQueueStateLocked()
    }
    deferredToCancel?.completeExceptionally(CancellationException("Request cancelled."))
    return cancelled
  }

  suspend fun clear() {
    val queuedToCancel = mutableListOf<CompletableDeferred<Unit>>()
    stateMutex.withLock {
      while (queuedRequests.isNotEmpty()) {
        queuedToCancel.add(queuedRequests.removeFirst().ready)
      }
      activeRequestId = null
      publishQueueStateLocked()
    }
    queuedToCancel.forEach {
      it.completeExceptionally(CancellationException("Endpoint stopped."))
    }
  }

  private fun publishQueueStateLocked() {
    endpointRepository.setQueueState(
      queueDepth = queuedRequests.size,
      activeRequestCount = if (activeRequestId == null) 0 else 1,
    )
  }
}
