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

package com.google.ai.edge.gallery.endpoint.ui

import androidx.lifecycle.ViewModel
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.endpoint.data.DefaultEndpointRepository
import com.google.ai.edge.gallery.endpoint.service.EndpointServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EndpointDashboardViewModel
@Inject
constructor(
  private val endpointRepository: DefaultEndpointRepository,
  private val serviceController: EndpointServiceController,
) : ViewModel() {
  val uiState = endpointRepository.uiState

  fun syncAvailableModels(models: List<Model>) {
    endpointRepository.syncAvailableModels(models)
  }

  fun selectModel(modelName: String) {
    endpointRepository.selectModel(modelName)
  }

  fun startServer() {
    if (uiState.value.selectedModelName.isEmpty()) {
      endpointRepository.updateServerState(
        com.google.ai.edge.gallery.endpoint.data.EndpointServerState.ERROR,
        "Select a downloaded model before starting the endpoint.",
      )
      return
    }
    serviceController.startService()
  }

  fun stopServer() {
    serviceController.stopService()
  }

  fun rotateToken() {
    endpointRepository.rotateAuthToken()
  }
}
