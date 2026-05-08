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

package com.google.ai.edge.gallery.customtasks.localapi

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.endpoint.TASK_ID_LOCAL_API_ENDPOINT
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class LocalApiEndpointTask @Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = TASK_ID_LOCAL_API_ENDPOINT,
      label = "Local API Endpoint",
      category = Category.EXPERIMENTAL,
      icon = Icons.Outlined.Widgets,
      description = "Run a local OpenAI-compatible API endpoint backed by an on-device model.",
      shortDescription = "Serve a local API",
      models = mutableListOf(),
      experimental = true,
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    onDone("")
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    onDone()
  }

  @Composable
  override fun MainScreen(data: Any) {}
}
