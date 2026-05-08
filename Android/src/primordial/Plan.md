# Detailed Implementation Plan: On-Device LLM API Endpoint on Android

## 0. Implementation Status

This document now reflects both the intended architecture and the current implementation status of the first endpoint slice.

### 0.1 Implemented in the current slice

- `CustomTask` entry point for **Local API Endpoint**
- dedicated endpoint dashboard screen
- foreground service start/stop flow
- loopback-only local HTTP server
- bearer auth enforcement for `/v1/*`
- `GET /healthz`
- `GET /v1/models`
- non-streaming `POST /v1/chat/completions`
- endpoint settings persistence for selected model, port, and token metadata
- bearer token storage in `UserData.secrets`
- model initialization and cleanup through the existing runtime helper
- basic request counters and last-error state exposed to the dashboard

### 0.2 Not implemented yet

- SSE streaming for `stream=true`
- explicit bounded queue and `429` backpressure policy
- timeout policy and cancellation hardening
- embeddings, responses API, tools/function calling, multimodal input
- LAN exposure, multi-model serving, auto-restart, broader OpenAI parity
- dedicated tests for the new endpoint feature
- end-to-end runtime validation on device/emulator

### 0.3 Current validation status

The code for the first slice has been added, but local Gradle validation is still blocked by the existing Android SDK/tooling failure (`25.0.3`) in this environment. As a result, build confirmation and runtime verification are still pending.

## 1. Goal

Add a local, OpenAI-compatible API endpoint to the Android app so coding agents and IDE tools can talk to an on-device LiteRT-backed model through a stable HTTP interface.

The endpoint must reuse the existing app architecture for:

- model discovery and selection
- model download and lifecycle management
- runtime dispatch
- app-local persistence
- Compose-based UI

## 2. Product Definition

### 2.1 v1 user experience

The user opens a new "Local API Endpoint" feature inside the app, picks a downloaded model, taps **Start Server**, copies a local base URL and bearer token, and points an IDE agent at the endpoint.

The endpoint then serves:

- `GET /healthz`
- `GET /v1/models`
- `POST /v1/chat/completions`

The user can also:

- stop the endpoint
- rotate the auth token
- inspect whether the model is ready
- see recent errors and basic counters

Status:

- implemented with a dedicated dashboard route
- model selection currently happens inside the dashboard rather than through the standard model-first task flow
- copy/share ergonomics are present in basic form and may still need UX polish

### 2.2 v1 non-goals

Do not attempt these in the first version:

- internet-facing hosting
- LAN exposure
- multi-model serving
- embeddings
- function calling / tools
- vision/audio API input
- auto-restart on boot
- full OpenAI API parity

## 3. Sane Defaults

- **Binding:** `127.0.0.1` only
- **Default port:** `8080`
- **Lifecycle:** Android foreground service
- **Auth:** required bearer token for all `/v1/*` routes
- **Model policy:** one active serving model
- **Queue policy:** serialize requests in the current slice; explicit queue limits are planned next
- **Backpressure:** `429` policy is planned, not implemented yet
- **Timeout:** fixed timeout policy is planned, not implemented yet
- **Streaming:** `stream=true` is currently rejected; SSE is planned next
- **Persistence:** store endpoint settings in `Settings`; store bearer token in `UserData.secrets`
- **App restart behavior:** endpoint stays off after process death until user explicitly starts it again

## 4. Architectural Fit with the Existing Codebase

This feature should be built on top of the app's current structure:

- `ModelManagerViewModel` remains the source of truth for tasks, models, download status, init state, and runtime selection.
- `DownloadRepository` / `DownloadWorker` continue to own model file acquisition.
- `Model.runtimeHelper` continues to choose LiteRT-LM vs AI Core.
- `CustomTask` + Hilt multibinding remain the correct way to add a new end-user capability tile and screen.
- `DataStoreRepository` / `SystemPromptRepository` remain the persistence entry points.

This must **not** introduce a second model manager or a separate persistence stack.

Current implementation notes:

- the feature is surfaced as a `CustomTask`, but navigation is intentionally special-cased to open a dedicated dashboard route
- `DefaultEndpointRepository` is a shared singleton repository used by both the dashboard and service/session layer
- available serving models are synced from `ModelManagerViewModel` while the dashboard is active; the service resolves the selected model from that shared repository

## 5. Proposed Package and File Layout

Create a new feature area under:

`Android/src/app/src/main/java/com/google/ai/edge/gallery/endpoint/`

Recommended structure:

### 5.1 API/server layer

- `endpoint/api/LocalApiServer.kt`
- `endpoint/api/RequestParsers.kt`
- `endpoint/api/ResponseMappers.kt`
- `endpoint/api/OpenAiDtos.kt`
- `endpoint/api/SseWriter.kt` *(not implemented yet)*
- `endpoint/api/AuthMiddleware.kt`

### 5.2 Service/orchestration layer

- `endpoint/service/EndpointForegroundService.kt`
- `endpoint/service/EndpointServiceController.kt`
- `endpoint/service/EndpointNotificationFactory.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/orchestration/EndpointRequestQueue.kt` *(not implemented yet)*
- `endpoint/orchestration/EndpointMetrics.kt` *(not implemented yet; basic counters currently live in repository state)*

### 5.3 Data layer

- `endpoint/data/EndpointRepository.kt` *(not implemented; current slice uses `DefaultEndpointRepository` directly)*
- `endpoint/data/DefaultEndpointRepository.kt`
- `endpoint/data/EndpointModels.kt`

### 5.4 UI layer

- `endpoint/ui/EndpointDashboardScreen.kt`
- `endpoint/ui/EndpointDashboardViewModel.kt`
- `endpoint/ui/EndpointStatusCard.kt` *(not implemented yet)*
- `endpoint/ui/EndpointServerControls.kt` *(not implemented yet)*
- `endpoint/ui/EndpointModelPicker.kt` *(not implemented yet)*
- `endpoint/ui/EndpointTokenDialog.kt` *(not implemented yet)*

### 5.5 Feature/task integration

- `customtasks/localapi/LocalApiEndpointTask.kt`
- `customtasks/localapi/LocalApiEndpointModule.kt`

This keeps the feature aligned with the existing `CustomTask` model instead of inventing separate navigation.

Current implementation deviates slightly here: the tile is still a `CustomTask`, but `GalleryNavGraph` routes it directly to the endpoint dashboard because the endpoint chooses its serving model inside that screen.

## 6. Existing Files Likely to Change

### 6.1 Android app wiring

- `AndroidManifest.xml`
  - register the foreground service
  - declare foreground service type if needed
  - **status:** implemented

- `MainActivity.kt`
  - no server logic should live here
  - may need only minimal lifecycle awareness if UI must observe service state

### 6.2 Navigation and feature surfacing

- `ui/navigation/GalleryNavGraph.kt`
  - no custom route should be needed if the feature is surfaced as a `CustomTask`
  - only update if a separate dashboard route is cleaner than a task screen
  - **status:** updated with a dedicated endpoint route and special-case navigation

- `ui/home/HomeScreen.kt`
  - should pick up the feature automatically once task wiring is correct

### 6.3 Persistence

- `proto/settings.proto`
  - add a structured message for endpoint settings
  - **status:** implemented

- `data/DataStoreRepository.kt`
  - add read/write methods for endpoint settings
  - add helper methods for selected serving model, port, token metadata, counters if persisted
  - **status:** implemented for selected model, port, and token metadata; counters remain in-memory

- `di/AppModule.kt`
  - wire any new repository/service dependencies
  - **status:** not required for the current concrete repository approach

### 6.4 Resources

- `res/values/strings.xml`
  - add endpoint labels, error messages, notification text
  - **status:** implemented

- drawables/icons as needed for the new task

## 7. Data Model Plan

### 7.1 Settings to persist

Add an `EndpointSettings` message to `settings.proto` with fields similar to:

- `enabled` or `last_enabled` (for UI only; server still does not auto-restart) *(not implemented yet)*
- `port` *(implemented)*
- `selected_model_name` *(implemented)*
- `allow_streaming` *(not implemented yet)*
- `max_queue_size` *(not implemented yet)*
- `request_timeout_ms` *(not implemented yet)*
- `token_created_at_ms` *(implemented)*

Recommended persistence split:

- `Settings`: non-sensitive endpoint configuration
- `UserData.secrets`: bearer token itself

### 7.2 In-memory state

`EndpointRepository` should expose a single UI state model containing:

- service running/stopped/starting/stopping
- host and port
- selected model
- model readiness
- queue depth
- active request count
- total request count
- failed request count
- last error
- token-present / token-rotated state

Current implementation:

- this state exists in `DefaultEndpointRepository`
- queue depth is present as state, but there is not yet a dedicated queue manager backing it
- request, success, and failure counters are tracked in-memory

Do not persist transient values such as active connections or queue depth.

## 8. API Contract Plan

### 8.1 `GET /healthz`

Response shape:

- endpoint version
- running state
- selected model name
- model readiness

Use this for simple connectivity checks and debugging.

### 8.2 `GET /v1/models`

Behavior:

- require bearer token
- return only models that are valid serving candidates
- indicate the selected serving model

Serving candidates in v1:

- downloaded
- compatible with LLM chat
- not currently in an error init state

### 8.3 `POST /v1/chat/completions`

Supported input fields in v1:

- `model`
- `messages`
- `temperature`
- `top_p`
- `max_tokens`
- `stream`

Supported message roles:

- `system`
- `user`
- `assistant`

Unsupported input behavior:

- reject unsupported critical fields with `400`
- ignore non-critical unknown fields only if doing so is safe and documented

Response behavior:

- JSON response for `stream=false`
- SSE event stream for `stream=true`

Current implementation status:

- `GET /healthz` is implemented
- `GET /v1/models` is implemented
- `POST /v1/chat/completions` is implemented for `stream=false`
- `stream=true` currently returns a client error because SSE has not been added yet

## 9. Runtime Integration Plan

### 9.1 Model selection

Use a dedicated endpoint-serving model selection, not the last model selected in another screen.

Status: implemented.

### 9.2 Initialization flow

When the user starts the server:

1. Resolve the selected model from the endpoint repository / dashboard-synced model list
2. Ensure it is downloaded
3. Initialize it using the existing runtime abstraction
4. Mark the server as ready only after runtime initialization completes

Status: implemented in `EndpointSessionManager`.

### 9.3 Inference flow

For each request:

1. Validate auth and payload
2. Resolve the target model
3. Convert OpenAI `messages` into the app/runtime input format
4. Invoke the model through the runtime helper
5. Map tokens or final output back into OpenAI-compatible output
6. Record metrics and errors

Status: implemented for non-streaming requests. Message translation is currently a straightforward flattened transcript with system prompt extraction.

### 9.4 Cleanup flow

When stopping the endpoint:

1. stop accepting new requests
2. drain or cancel queued work
3. stop the HTTP server
4. clean up the loaded model if it is not shared elsewhere
5. clear transient metrics/state

Status: partially implemented. The current slice stops the server and conditionally cleans up model initialization it owns, but queued-work draining and cancellation are still future work.

## 10. Service and Concurrency Design

### 10.1 Foreground service responsibilities

`EndpointForegroundService` should own:

- server lifecycle
- notification lifecycle
- current server state
- connection to `EndpointSessionManager`

It should **not** contain request parsing or business logic.

### 10.2 Queueing model

Use a small explicit queue object:

- one active generation request
- one pending request
- immediate `429` beyond that

This keeps resource usage predictable on-device.

Current implementation status:

- requests are currently serialized with a mutex
- no explicit bounded queue exists yet
- overload rejection is still pending

### 10.3 Cancellation

In v1:

- cancellation is best-effort
- client disconnect on streaming request should stop downstream emission
- if the runtime supports stop/cancel, invoke it
- otherwise mark the request cancelled and drop output

Status: not implemented yet beyond basic stop/cleanup behavior.

## 11. UI Plan

### 11.1 Entry point

Expose the feature as a `CustomTask`, likely under `Category.EXPERIMENTAL` first.

Reason:

- matches the existing task discovery model
- keeps the feature visible on the home screen
- avoids special-case navigation

Current implementation status:

- exposed as a `CustomTask`
- navigation is currently special-cased on purpose so the dashboard can manage its own serving-model selection

### 11.2 Dashboard contents

The dashboard should show:

- server state: stopped, starting, running, stopping, error
- selected model
- model readiness
- base URL
- bearer token status
- start/stop button
- rotate token button
- queue depth
- request counters
- last error message

Status:

- implemented in a basic Compose screen
- model selection UI is functional but still simple
- some planned subcomponents remain collapsed into one screen

### 11.3 UX rules

- disable **Start Server** when no serving model is selected
- disable **Start Server** when the model is not downloaded
- if a selected model is missing, surface the existing download path instead of inventing a new one
- make the base URL and token easy to copy

## 12. Step-by-Step Implementation Sequence

### Phase 1: persistence and contracts

1. Extend `settings.proto` with endpoint settings
2. Regenerate protobuf classes via the existing Gradle protobuf setup
3. Extend `DataStoreRepository` with endpoint settings methods
4. Create DTOs for OpenAI request/response payloads
5. Create an internal `EndpointUiState` and `EndpointServerState`

Exit criteria:

- endpoint configuration can be stored and read
- API DTOs compile
- repository contract is stable enough to build on

Status: implemented, but build confirmation is still blocked by the local SDK/tooling issue.

### Phase 2: feature shell

1. Add `LocalApiEndpointTask`
2. Register it with Hilt via `@IntoSet`
3. Add `EndpointDashboardViewModel`
4. Build the dashboard screen with fake state first
5. Add strings/resources/iconography

Exit criteria:

- feature appears on the home screen
- dashboard renders and is navigable

Status: implemented.

### Phase 3: service skeleton

1. Add `EndpointForegroundService`
2. Register it in the manifest
3. Add notification factory and service controller
4. Wire dashboard start/stop controls to the service
5. Expose service state to the viewmodel/repository

Exit criteria:

- service can be started and stopped from UI
- persistent notification works
- app reflects running/stopped state reliably

Status: implemented in code; still needs runtime verification on device/emulator.

### Phase 4: local HTTP server

1. Implement `LocalApiServer`
2. Bind to loopback only
3. Add auth middleware
4. Add `GET /healthz`
5. Return deterministic JSON errors

Exit criteria:

- local curl/client can hit `/healthz`
- unauthorized requests are rejected

Status: implemented in code; still needs runtime verification on device/emulator.

### Phase 5: model-backed `/v1/models`

1. Add serving-candidate filter logic using existing model/task metadata
2. Read selected model from endpoint settings
3. Return OpenAI-style model list output

Exit criteria:

- `/v1/models` returns correct downloaded/compatible models

Status: implemented with the currently synced downloaded-model list; candidate filtering may still need refinement after real-world validation.

### Phase 6: model lifecycle integration

1. Build `EndpointSessionManager`
2. Resolve selected model through the existing model manager/repository path
3. Initialize runtime on server start
4. Track model-ready vs model-loading vs model-error state
5. Clean up on stop

Exit criteria:

- server only transitions to ready after model init succeeds
- init failures surface in UI and logs

Status: implemented in `EndpointSessionManager`.

### Phase 7: non-streaming chat completions

1. Parse OpenAI chat request
2. Convert messages into runtime input
3. Execute one completion through the runtime helper
4. Map final output into OpenAI response JSON
5. Add request IDs, timing, and error mapping

Exit criteria:

- one local client can complete a basic chat request end-to-end

Status: implemented in code; still awaiting build/runtime confirmation.

### Phase 8: streaming chat completions

1. Add SSE writer
2. Emit OpenAI-style streaming chunks
3. Handle client disconnect and cleanup
4. Preserve final usage/stop metadata as applicable

Exit criteria:

- streaming works with a local IDE-compatible client

Status: not started.

### Phase 9: hardening

1. Add queue limits and `429`
2. Add timeout handling
3. Add token rotation
4. Add request counters and last-error state
5. Polish dashboard UX
6. Write setup docs and troubleshooting notes

Exit criteria:

- stable repeated start/stop
- predictable overload behavior
- usable developer documentation

Status: partially implemented. Token rotation, counters, and basic error state are present; queue limits, timeouts, documentation, and broader hardening remain.

## 13. Testing Plan

### 13.1 Unit tests

Focus on:

- request validation
- auth middleware
- message-to-runtime translation
- response mapping
- queue policy
- endpoint settings persistence

Status: not added yet.

### 13.2 Android/integration tests

Focus on:

- service start/stop behavior
- notification presence
- dashboard state transitions
- health endpoint reachability

Status: not added yet.

### 13.3 Manual test matrix

Run these manually on device:

1. start server with valid downloaded model
2. attempt start with missing model
3. `GET /healthz`
4. `GET /v1/models`
5. non-streaming chat request
6. streaming chat request
7. invalid token
8. queue overflow
9. stop server during active request
10. rotate token and verify old token fails

Current status:

- still pending because local build/tooling validation is blocked in this environment
- `streaming chat request` and `queue overflow` remain future-scope manual checks

## 14. Operational and Security Checks

- verify server binds only to loopback
- verify tokens are never logged
- verify prompts/completions are not logged in plaintext
- verify service shutdown cleans resources
- verify process death does not silently restart the endpoint

## 15. Documentation Deliverables

Write two docs once implementation is stable:

1. **Developer integration guide**
   - how to start the endpoint
   - how to get the base URL and token
   - sample `curl`
   - sample OpenAI-compatible client config

2. **Troubleshooting guide**
   - port in use
   - model not downloaded
   - auth failures
   - queue full
   - unsupported request fields

Status: not written yet.

## 16. Recommended First Coding Slice

Build the thinnest useful vertical slice in this order:

1. new task + dashboard shell
2. service start/stop
3. loopback `/healthz`
4. token auth
5. selected downloaded model wiring
6. `/v1/models`
7. non-streaming `/v1/chat/completions`

That slice proves the architecture before adding streaming and metrics.

Status: this recommended slice has now been implemented.

## 17. Acceptance Criteria

This implementation is successful when all of the following are true:

- the feature is visible inside the app as a normal capability
- the endpoint can be started and stopped from the UI
- the server stays alive under a foreground service while active
- `GET /healthz` works locally
- `GET /v1/models` returns valid serving models
- `POST /v1/chat/completions` works against a selected on-device model
- bearer auth is enforced
- overload is handled predictably
- the user can connect an IDE agent using documented steps

Current status summary:

- implemented in code: feature visibility, service lifecycle, loopback server, `/healthz`, `/v1/models`, non-streaming chat, bearer auth
- partially implemented: metrics/basic counters, token rotation, model lifecycle ownership cleanup
- not implemented yet: predictable overload handling, streaming, integration docs
- not yet verified: successful build, device/emulator runtime, IDE connectivity
