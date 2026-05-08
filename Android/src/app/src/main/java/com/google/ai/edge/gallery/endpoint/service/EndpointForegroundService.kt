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

package com.google.ai.edge.gallery.endpoint.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.google.ai.edge.gallery.endpoint.orchestration.EndpointSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class EndpointForegroundService : Service() {
  @Inject lateinit var endpointSessionManager: EndpointSessionManager
  @Inject lateinit var notificationFactory: EndpointNotificationFactory

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var serviceJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        serviceJob?.cancel()
        serviceJob =
          serviceScope.launch {
            endpointSessionManager.stopServer()
            stopSelf()
          }
      }

      ACTION_START, null -> {
        startForeground(NOTIFICATION_ID, notificationFactory.create())
        serviceJob?.cancel()
        serviceJob =
          serviceScope.launch {
            val started = endpointSessionManager.startServer()
            if (!started) {
              stopSelf()
            }
          }
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    runBlocking { endpointSessionManager.stopServer() }
    serviceScope.cancel()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  companion object {
    const val ACTION_START = "com.google.ai.edge.gallery.endpoint.action.START"
    const val ACTION_STOP = "com.google.ai.edge.gallery.endpoint.action.STOP"
    private const val NOTIFICATION_ID = 4001
  }
}
