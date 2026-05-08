# Phase 2 Implementation Plan: Post-MVP Local API Endpoint

## 0. Current Status

Phase 2 remains a future work plan, but it now builds on a Phase 1 slice that has already been implemented in code.

Current baseline status:

- the Local API Endpoint feature tile exists
- the endpoint dashboard exists
- foreground-service-backed loopback serving exists
- bearer auth exists for `/v1/*`
- `GET /healthz` exists
- `GET /v1/models` exists
- non-streaming `POST /v1/chat/completions` exists
- selected model, port, and token metadata persistence exist
- basic token rotation, counters, and last-error state exist

Current blocker:

- local build/runtime validation is still blocked in this environment by the Android SDK/tooling failure (`25.0.3`)

File reference note:

- file targets in this document intentionally mix **existing files** and **proposed new files**
- `endpoint/data/DefaultEndpointRepository.kt` and `endpoint/data/EndpointModels.kt` already exist
- `endpoint/data/EndpointRepository.kt`, `endpoint/orchestration/EndpointRequestQueue.kt`, `endpoint/orchestration/EndpointMetrics.kt`, and `endpoint/api/SseWriter.kt` are still proposed additions unless created later

## 1. Purpose

Phase 1 proves that the Android app can expose a local, OpenAI-compatible endpoint backed by an on-device model. Phase 2 turns that MVP into a feature that is reliable, observable, and practical for day-to-day use by IDE agents.

This phase assumes Phase 1 already delivers:

- a foreground-service-backed local server
- loopback-only binding
- bearer-token auth
- one selected serving model
- `GET /healthz`
- `GET /v1/models`
- non-streaming `POST /v1/chat/completions`

More precisely, those Phase 1 capabilities are implemented in code today, but still need full build and device/emulator validation once the local tooling issue is resolved.

## 2. Phase 2 Goals

Phase 2 should deliver the rest of the feature set needed for real usage:

- streaming responses
- bounded request queueing and overload handling
- better cancellation behavior
- complete dashboard UX
- model swap and lifecycle hardening
- security polish
- metrics and observability
- compatibility validation with real IDE clients
- documentation and troubleshooting guidance

## 3. Out of Scope for Phase 2

Do not include these unless Phase 2 completes early:

- LAN exposure
- internet-facing access
- multi-model parallel serving
- embeddings
- tool/function calling
- vision/audio over the external API
- boot-time auto-start
- account sync or cloud relay

Those should be treated as Phase 3+ work.

## 4. Architectural Principles

Phase 2 must preserve the same architectural rules established in the main plan:

- no second model manager
- no UI logic inside the HTTP server
- no direct persistence access from UI composables
- no bypassing `Model.runtimeHelper`
- no ad hoc shared mutable state across service, server, and dashboard

Recommended ownership boundaries:

- **Service:** owns process-level lifecycle and notification state
- **Server:** owns HTTP route handling and request/response I/O
- **Session manager:** owns model readiness, queueing, execution, cancellation, and metrics
- **Repository:** owns persistent settings and observable UI state
- **ViewModel/UI:** owns rendering and user-triggered actions only

## 5. Phase 2 Workstreams

## 5.1 Streaming support

### Goal

Add OpenAI-style streaming behavior for `POST /v1/chat/completions` when `stream=true`.

### Scope

- SSE response writer
- chunk serialization
- graceful stream completion
- disconnect handling
- request cancellation propagation

### Implementation tasks

1. Add `endpoint/api/SseWriter.kt`.
2. Define chunk DTO helpers in `endpoint/api/OpenAiDtos.kt` or a dedicated streaming DTO file.
3. Update the request handler in `LocalApiServer.kt` to branch between:
   - non-streaming JSON response
   - streaming SSE response
4. Update `EndpointSessionManager` to expose incremental output callbacks suitable for SSE.
5. Ensure the server writes:
   - content chunks in OpenAI-compatible shape
   - completion sentinel / done event
   - error event mapping for failures that happen after the stream starts
6. Detect socket/client disconnect and stop downstream emission.
7. If runtime supports interruption, invoke the runtime stop path when the client disconnects.

### File targets

- `endpoint/api/SseWriter.kt`
- `endpoint/api/OpenAiDtos.kt`
- `endpoint/api/LocalApiServer.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/data/EndpointModels.kt` if stream-specific internal models are useful

### Exit criteria

- `stream=true` returns token-like incremental SSE events
- disconnecting the client does not leave the request hanging forever
- stream completion consistently returns a final terminal event

## 5.2 Queueing, backpressure, and timeouts

### Goal

Make request handling predictable under load on a phone-class device.

### Scope

- one active request
- one pending request
- immediate `429` after queue is full
- explicit request timeout
- clear queue metrics

### Implementation tasks

1. Create or finish `EndpointRequestQueue.kt`.
2. Represent request states explicitly:
   - queued
   - running
   - completed
   - failed
   - cancelled
   - timed_out
3. Add a queue API like:
   - `tryEnqueue(request)`
   - `markRunning(requestId)`
   - `complete(requestId)`
   - `cancel(requestId)`
4. Enforce queue capacity centrally inside the orchestration layer, not inside individual routes.
5. Add timeout handling around runtime inference.
6. Map queue-full to `429`.
7. Map timeout to a stable error shape and user-visible dashboard error.
8. Expose queue depth and active request count through `EndpointRepository`.

### File targets

- `endpoint/orchestration/EndpointRequestQueue.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/data/EndpointRepository.kt`
- `endpoint/data/DefaultEndpointRepository.kt`
- `endpoint/ui/EndpointDashboardViewModel.kt`

### Exit criteria

- concurrent client calls do not race unpredictably
- extra load is rejected explicitly, not by crash or hang
- timeout behavior is deterministic and visible

## 5.3 Cancellation and disconnect handling

### Goal

Ensure aborted requests do not keep consuming device resources unnecessarily.

### Scope

- client disconnect detection
- best-effort cancellation
- runtime stop bridging
- queue cleanup

### Implementation tasks

1. Add a request identifier to every inbound request as early as possible.
2. Thread the request ID through orchestration, metrics, and logs.
3. Add a best-effort `cancelRequest(requestId)` path in `EndpointSessionManager`.
4. Connect disconnect/cancel events to `Model.runtimeHelper.stopResponse()` if the runtime supports it.
5. Ensure cancelled requests are removed from queue/accounting state.
6. Prevent duplicate completion/error handling after cancellation.

### File targets

- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/orchestration/EndpointRequestQueue.kt`
- `endpoint/api/LocalApiServer.kt`
- runtime bridge files if additional cancellation adapter code is needed

### Exit criteria

- client disconnects do not leak active requests
- cancelled streaming requests stop producing output
- queue and metrics recover to a clean state afterward

## 5.4 Dashboard completion

### Goal

Complete and polish the endpoint UI so it is fully operable from the app without adb/manual internals.

### Scope

- model selection polish
- base URL display and copy UX
- token visibility/copy/rotation polish
- server controls and transition states
- clearer status/error/metrics surface

### Implementation tasks

1. Refine `EndpointDashboardScreen.kt` so the existing dashboard cleanly supports:
   - start/stop button
   - selected model label / picker
   - host + port display
   - token section with reveal/copy/rotate actions
   - queue and request counters
   - last error card
2. Add a model picker component that uses the same serving-candidate rules as `/v1/models`.
3. Disable start when:
   - no model is selected
   - selected model is not downloaded
   - service is in starting/stopping transition
4. Add copy actions for:
   - base URL
   - bearer token
   - sample curl command if useful
5. Add a token rotation confirmation dialog.
6. Surface model readiness states distinctly:
   - selected but not downloaded
   - downloaded but not initialized
   - initializing
   - ready
   - error

### File targets

- `endpoint/ui/EndpointDashboardScreen.kt`
- `endpoint/ui/EndpointDashboardViewModel.kt`
- `endpoint/ui/EndpointStatusCard.kt` *(proposed new file if the screen is split into smaller components)*
- `endpoint/ui/EndpointServerControls.kt` *(proposed new file if the screen is split into smaller components)*
- `endpoint/ui/EndpointModelPicker.kt` *(proposed new file if the screen is split into smaller components)*
- `endpoint/ui/EndpointTokenDialog.kt` *(proposed new file if the screen is split into smaller components)*
- `res/values/strings.xml`

### Exit criteria

- a user can configure and operate the endpoint entirely from the dashboard
- the dashboard clearly explains why the endpoint cannot be started

## 5.5 Model swap and lifecycle hardening

### Goal

Support stable repeated use and controlled serving-model changes.

### Scope

- start/stop hardening
- model swap while server is running
- cleanup reliability
- service death recovery behavior

### Implementation tasks

1. Add explicit server/model lifecycle states such as:
   - stopped
   - starting_server
   - loading_model
   - ready
   - swapping_model
   - stopping
   - error
2. Implement controlled model swap flow:
   - reject incoming requests with `409` during swap
   - stop accepting new work
   - drain or cancel current work as policy dictates
   - clean old model
   - initialize new model
   - reopen readiness
3. Add cleanup guarantees on:
   - normal stop
   - service destruction
   - unexpected server init failure
4. Verify that the endpoint does not silently auto-restart after process death.
5. Reset transient metrics/state between service sessions as appropriate.

### File targets

- `endpoint/service/EndpointForegroundService.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/data/EndpointModels.kt`
- `endpoint/data/DefaultEndpointRepository.kt`

### Exit criteria

- repeated start/stop cycles are stable
- serving model changes do not leave the app in a broken state
- shutdown always releases the serving runtime cleanly

## 5.6 Auth and local security polish

### Goal

Keep the endpoint safe by default while remaining practical for local development use.

### Scope

- token lifecycle
- token rotation
- loopback verification
- log redaction
- stable auth failure behavior

### Implementation tasks

1. Add a token rotation flow in `EndpointRepository`.
2. Ensure token creation time is persisted separately from the token itself if needed for UI.
3. Verify all `/v1/*` endpoints require auth, not just chat routes.
4. Standardize auth failure responses:
   - missing header
   - malformed header
   - wrong token
5. Review logging paths and redact:
   - prompt bodies
   - completion contents
   - bearer tokens
6. Add a loopback binding assertion or test coverage so accidental future LAN exposure is caught.

### File targets

- `endpoint/api/AuthMiddleware.kt`
- `endpoint/data/EndpointRepository.kt`
- `endpoint/data/DefaultEndpointRepository.kt`
- `endpoint/service/EndpointForegroundService.kt`
- logging utilities if a shared helper is introduced

### Exit criteria

- rotated tokens invalidate prior credentials
- sensitive values never appear in normal logs
- all protected routes enforce auth consistently

## 5.7 Metrics and observability

### Goal

Make the endpoint diagnosable without attaching a debugger.

### Scope

- request counters
- average latency
- queue depth
- last error
- uptime / ready-since timestamp

### Implementation tasks

1. Add `EndpointMetrics.kt` or keep metrics inside repository/session-manager state if that remains the simpler design.
2. Track at minimum:
   - total requests
   - failed requests
   - cancelled requests
   - timed out requests
   - average latency
   - currently active request count
   - current queue depth
   - server start timestamp
   - ready timestamp
3. Publish metrics into repository-backed UI state.
4. Add structured logs with:
   - request ID
   - route
   - model
   - latency
   - failure class
5. Surface last error text in the dashboard, but keep full sensitive context out of UI if not needed.

### File targets

- `endpoint/orchestration/EndpointMetrics.kt` *(proposed new file if metrics are extracted from repository/session state)*
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/data/EndpointRepository.kt` *(proposed abstraction if the repository is formalized behind an interface)*
- `endpoint/ui/EndpointDashboardScreen.kt`

### Exit criteria

- the dashboard shows enough information to debug common failures
- request logs can be correlated by request ID

## 5.8 IDE/client compatibility pass

### Goal

Validate that real OpenAI-compatible clients can use the endpoint, not just synthetic curl requests.

### Scope

- one or more real IDE agent clients
- OpenAI-compatible client libraries
- streaming and non-streaming behavior
- field compatibility validation

### Implementation tasks

1. Pick a primary validation target first, such as:
   - VS Code extension flow using OpenAI-compatible endpoint configuration
   - a simple OpenAI SDK client
2. Verify:
   - auth header handling
   - `/v1/models` model discovery
   - non-streaming completions
   - streaming completions
   - graceful error handling for unsupported fields
3. Document unsupported fields explicitly rather than silently misbehaving.
4. Add compatibility notes to the dashboard docs if some clients require specific settings.

### Deliverables

- a tested client matrix
- a short list of known compatible and known-partial client flows

### Exit criteria

- at least one real IDE/client flow works end to end
- the known limitations are documented

## 5.9 Documentation and troubleshooting

### Goal

Make the feature usable by developers without reading the source code.

### Scope

- setup guide
- sample requests
- sample client configuration
- troubleshooting guide

### Documentation tasks

1. Create a developer usage guide covering:
   - where to find the feature in the app
   - how to pick a model
   - how to start the server
   - how to copy the base URL and token
   - example `curl` requests
   - example client configuration snippets
2. Create troubleshooting content covering:
   - model not downloaded
   - invalid token
   - queue full
   - timeout
   - unsupported request fields
   - service not staying active
3. Add a short note about security defaults:
   - loopback-only
   - local bearer token
   - no LAN access in current version

### Exit criteria

- a developer can connect a client successfully from documentation alone

## 6. Data and Settings Changes for Phase 2

If not already covered in Phase 1, Phase 2 should finalize the settings model with:

- `selected_model_name`
- `port`
- `max_queue_size`
- `request_timeout_ms`
- `allow_streaming`
- `token_created_at_ms`

Possible Phase 2 additions if the dashboard benefits from them:

- `last_used_at_ms`
- `last_selected_client_name` if a future client profile UX is added

Do not persist transient counters unless there is a concrete UX need.

## 7. Recommended File Touch Map

### Endpoint feature area

- `endpoint/api/LocalApiServer.kt`
- `endpoint/api/OpenAiDtos.kt`
- `endpoint/api/SseWriter.kt` *(proposed new file)*
- `endpoint/api/AuthMiddleware.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/orchestration/EndpointRequestQueue.kt` *(proposed new file)*
- `endpoint/orchestration/EndpointMetrics.kt` *(proposed new file)*
- `endpoint/data/EndpointRepository.kt` *(proposed new interface/abstraction)*
- `endpoint/data/DefaultEndpointRepository.kt`
- `endpoint/data/EndpointModels.kt`
- `endpoint/ui/EndpointDashboardScreen.kt`
- `endpoint/ui/EndpointDashboardViewModel.kt`
- `endpoint/ui/EndpointStatusCard.kt` *(proposed new file)*
- `endpoint/ui/EndpointServerControls.kt` *(proposed new file)*
- `endpoint/ui/EndpointModelPicker.kt` *(proposed new file)*
- `endpoint/ui/EndpointTokenDialog.kt` *(proposed new file)*

### Existing app surfaces

- `data/DataStoreRepository.kt`
- `di/AppModule.kt`
- `proto/settings.proto`
- `res/values/strings.xml`
- `customtasks/localapi/LocalApiEndpointTask.kt`
- `customtasks/localapi/LocalApiEndpointModule.kt`

## 8. Recommended Delivery Order

Implement Phase 2 in this order:

1. streaming support
2. queueing and timeout handling
3. cancellation/disconnect cleanup
4. dashboard completion
5. model lifecycle hardening
6. auth and security polish
7. metrics and observability
8. IDE/client compatibility pass
9. documentation

This order delivers the highest-value client-facing improvements first while leaving docs and compatibility validation until the technical behavior has stabilized.

## 9. Testing Plan

## 9.1 Unit tests

Add or expand tests for:

- SSE chunk formatting
- auth middleware behavior
- queue overflow behavior
- timeout mapping
- cancellation cleanup
- serving-model eligibility filtering
- token rotation behavior

## 9.2 Integration tests

Add Android-side integration coverage for:

- service start/stop
- dashboard state transitions
- server readiness reporting
- model swap behavior
- loopback health endpoint and protected route behavior

## 9.3 Manual test matrix

Run at minimum:

1. non-streaming completion success
2. streaming completion success
3. invalid token
4. missing token
5. queue overflow
6. timeout path
7. client disconnect during streaming
8. rotate token and retry with old token
9. stop server during in-flight request
10. switch serving model while server is active
11. repeated start/stop cycles
12. connect a real IDE/client flow

## 10. Acceptance Criteria

Phase 2 is complete when:

- streaming works reliably for supported models
- concurrent request behavior is predictable and bounded
- the dashboard is complete enough to operate the endpoint fully
- token rotation works and invalidates old credentials
- model swap and repeated start/stop cycles are stable
- the dashboard exposes actionable health and failure information
- at least one real IDE/client flow works end to end
- the setup and troubleshooting docs are sufficient for external developers

## 11. Nice-to-Have Items If Time Remains

Only consider these after all acceptance criteria are met:

- richer per-request history in the dashboard
- exportable diagnostics bundle
- client presets/config templates in the UI
- optional persisted lightweight request history without prompt bodies

## 12. Recommended End State After Phase 2

After this phase, the local API endpoint should be considered a **usable experimental feature** rather than a proof of concept. It should be realistic for developers to launch the Android app, start the local server, point a compatible IDE agent at it, and use it repeatedly with predictable behavior and clear operational feedback.
