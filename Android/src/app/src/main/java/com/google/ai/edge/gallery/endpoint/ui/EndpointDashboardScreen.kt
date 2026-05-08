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

import android.content.ClipData
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.NumberSliderConfig
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.endpoint.data.EndpointServerState
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointDashboardScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  viewModel: EndpointDashboardViewModel = hiltViewModel(),
) {
  val endpointUiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val context = LocalContext.current
  val notAvailableLabel = stringResource(R.string.local_api_endpoint_not_available)
  val canStart =
    (endpointUiState.serverState == EndpointServerState.STOPPED ||
      endpointUiState.serverState == EndpointServerState.ERROR) &&
      endpointUiState.selectedModelName.isNotEmpty()
  val canStop =
    endpointUiState.serverState == EndpointServerState.RUNNING ||
      endpointUiState.serverState == EndpointServerState.STARTING
  val showStartProgress =
    endpointUiState.serverState == EndpointServerState.STARTING &&
      !endpointUiState.isSelectedModelReady
  val selectedModel =
    remember(
      endpointUiState.selectedModelName,
      modelManagerUiState.modelDownloadStatus,
      modelManagerUiState.modelImportingUpdateTrigger,
      modelManagerUiState.loadingModelAllowlist,
      modelManagerUiState.configValuesUpdateTrigger,
    ) {
      modelManagerViewModel
        .getAllDownloadedModels()
        .find { it.name == endpointUiState.selectedModelName }
    }
  val tokenLimitSummary =
    remember(selectedModel, notAvailableLabel) {
      selectedModel?.toEndpointTokenLimitSummary(notAvailableLabel)
    }

  LaunchedEffect(
    modelManagerUiState.modelDownloadStatus,
    modelManagerUiState.modelImportingUpdateTrigger,
    modelManagerUiState.loadingModelAllowlist,
  ) {
    viewModel.syncAvailableModels(modelManagerViewModel.getAllDownloadedModels())
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.local_api_endpoint_label)) },
        navigationIcon = {
          IconButton(onClick = navigateUp) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
          }
        },
      )
    }
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(R.string.local_api_endpoint_description),
        style = MaterialTheme.typography.bodyMedium,
      )

      Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.local_api_endpoint_status),
            style = MaterialTheme.typography.titleMedium,
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_status),
            value =
              when (endpointUiState.serverState) {
                EndpointServerState.STOPPED -> stringResource(R.string.local_api_endpoint_not_running)
                EndpointServerState.STARTING -> stringResource(R.string.local_api_endpoint_starting)
                EndpointServerState.RUNNING -> stringResource(R.string.local_api_endpoint_running)
                EndpointServerState.STOPPING -> stringResource(R.string.local_api_endpoint_stopping)
                EndpointServerState.ERROR -> stringResource(R.string.local_api_endpoint_error)
              },
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_base_url),
            value = endpointUiState.baseUrl,
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_selected_model),
            value =
              endpointUiState.selectedModelName.ifEmpty {
                stringResource(R.string.local_api_endpoint_not_available)
              },
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_runtime),
            value = tokenLimitSummary?.runtimeLabel ?: notAvailableLabel,
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_configured_max_tokens),
            value = tokenLimitSummary?.configuredMaxTokens ?: notAvailableLabel,
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_known_context_window),
            value = tokenLimitSummary?.knownContextWindow ?: notAvailableLabel,
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_ready),
            value =
              if (endpointUiState.isSelectedModelReady) {
                stringResource(R.string.local_api_endpoint_ready)
              } else {
                stringResource(R.string.local_api_endpoint_model_not_ready)
              },
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_streaming),
            value =
              if (endpointUiState.allowStreaming) {
                stringResource(R.string.local_api_endpoint_enabled)
              } else {
                stringResource(R.string.local_api_endpoint_disabled)
              },
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_queue_capacity),
            value = endpointUiState.maxQueueSize.toString(),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_request_timeout),
            value = "${endpointUiState.requestTimeoutMs}ms",
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_server_started),
            value = formatTimestamp(context, endpointUiState.serverStartedAtMs),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_ready_since),
            value = formatTimestamp(context, endpointUiState.readySinceAtMs),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_requests),
            value = endpointUiState.totalRequestCount.toString(),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_failed_requests),
            value = endpointUiState.failedRequestCount.toString(),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_cancelled_requests),
            value = endpointUiState.cancelledRequestCount.toString(),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_timed_out_requests),
            value = endpointUiState.timedOutRequestCount.toString(),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_active_requests),
            value = endpointUiState.activeRequestCount.toString(),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_queue_depth),
            value = endpointUiState.queueDepth.toString(),
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_avg_latency),
            value = "${endpointUiState.averageLatencyMs}ms",
          )
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_last_request_id),
            value =
              endpointUiState.lastRequestId.ifEmpty {
                stringResource(R.string.local_api_endpoint_not_available)
              },
          )
          Text(
            text = stringResource(R.string.local_api_endpoint_token_limit_note),
            style = MaterialTheme.typography.bodySmall,
          )
          if (endpointUiState.lastError.isNotEmpty()) {
            Text(
              endpointUiState.lastError,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::startServer, enabled = canStart) {
          Text(stringResource(R.string.local_api_endpoint_start))
        }
        OutlinedButton(onClick = viewModel::stopServer, enabled = canStop) {
          Text(stringResource(R.string.local_api_endpoint_stop))
        }
      }

      if (showStartProgress) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.model_is_initializing_msg),
              style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
        }
      }

      Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.local_api_endpoint_select_model),
            style = MaterialTheme.typography.titleMedium,
          )
          if (endpointUiState.availableModels.isEmpty()) {
            Text(stringResource(R.string.local_api_endpoint_no_models))
          } else {
            endpointUiState.availableModels.forEach { model ->
              val isSelected = endpointUiState.selectedModelName == model.name
              val label =
                if (isSelected) {
                  stringResource(R.string.local_api_endpoint_selected_prefix, model.displayName)
                } else {
                  model.displayName
                }
              if (isSelected) {
                Button(
                  onClick = { viewModel.selectModel(model.name) },
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(label)
                }
              } else {
                OutlinedButton(
                  onClick = { viewModel.selectModel(model.name) },
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(label)
                }
              }
            }
          }
        }
      }

      Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.local_api_endpoint_auth_token),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(endpointUiState.authToken.ifEmpty { stringResource(R.string.local_api_endpoint_no_token) })
          StatusRow(
            label = stringResource(R.string.local_api_endpoint_token_created),
            value = formatTimestamp(context, endpointUiState.tokenCreatedAtMs),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyToClipboard(context, "endpoint-url", endpointUiState.baseUrl) }) {
              Icon(Icons.Outlined.ContentCopy, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.local_api_endpoint_copy_url))
            }
            OutlinedButton(
              onClick = { copyToClipboard(context, "endpoint-token", endpointUiState.authToken) },
              enabled = endpointUiState.authToken.isNotEmpty(),
            ) {
              Icon(Icons.Outlined.ContentCopy, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.local_api_endpoint_copy_token))
            }
          }
          OutlinedButton(onClick = viewModel::rotateToken) {
            Text(stringResource(R.string.local_api_endpoint_rotate_token))
          }
        }
      }
    }
  }
}

@Composable
private fun StatusRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(text = label, style = MaterialTheme.typography.labelMedium)
    Text(text = value, style = MaterialTheme.typography.bodyMedium)
  }
}

private fun copyToClipboard(context: Context, label: String, content: String) {
  val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
}

private fun formatTimestamp(context: Context, timestampMs: Long): String {
  if (timestampMs <= 0L) {
    return context.getString(R.string.local_api_endpoint_not_available)
  }
  return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampMs))
}

private data class EndpointTokenLimitSummary(
  val runtimeLabel: String,
  val configuredMaxTokens: String,
  val knownContextWindow: String,
)

private fun Model.toEndpointTokenLimitSummary(notAvailableLabel: String): EndpointTokenLimitSummary {
  val maxTokens =
    getIntConfigValue(ConfigKeys.MAX_TOKENS, defaultValue = 0)
      .takeIf { it > 0 }
      ?.toString()
      ?: notAvailableLabel
  val contextWindow =
    (configs.firstOrNull { it.key == ConfigKeys.MAX_TOKENS } as? NumberSliderConfig)
      ?.sliderMax
      ?.toInt()
      ?.toString()
      ?: notAvailableLabel
  val runtimeLabel =
    when (runtimeType) {
      RuntimeType.LITERT_LM -> "LiteRT LM"
      RuntimeType.AICORE -> "AI Core"
      RuntimeType.UNKNOWN -> notAvailableLabel
    }
  return EndpointTokenLimitSummary(
    runtimeLabel = runtimeLabel,
    configuredMaxTokens = maxTokens,
    knownContextWindow = contextWindow,
  )
}
