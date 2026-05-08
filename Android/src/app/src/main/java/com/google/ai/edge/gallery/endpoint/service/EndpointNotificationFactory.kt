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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val ENDPOINT_NOTIFICATION_CHANNEL_ID = "local_api_endpoint_channel"

@Singleton
class EndpointNotificationFactory @Inject constructor(@ApplicationContext private val context: Context) {
  fun create(): Notification {
    ensureChannel()
    val contentIntent =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    return NotificationCompat.Builder(context, ENDPOINT_NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
      .setContentTitle(context.getString(R.string.local_api_endpoint_notification_title))
      .setContentText(context.getString(R.string.local_api_endpoint_notification_text))
      .setContentIntent(contentIntent)
      .setOngoing(true)
      .build()
  }

  private fun ensureChannel() {
    val notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val existing = notificationManager.getNotificationChannel(ENDPOINT_NOTIFICATION_CHANNEL_ID)
    if (existing != null) {
      return
    }
    val channel =
      NotificationChannel(
        ENDPOINT_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.local_api_endpoint_notification_title),
        NotificationManager.IMPORTANCE_LOW,
      )
    notificationManager.createNotificationChannel(channel)
  }
}
