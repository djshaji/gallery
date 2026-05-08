# Phase 3 Implementation Plan: Advanced Endpoint Features and Ecosystem Expansion

## 0. Current Status

Phase 3 remains future work. It should be read as building on the implemented Phase 1 endpoint slice plus the planned Phase 2 hardening work.

Current baseline status:

- the Phase 1 endpoint slice is implemented in code
- the current implementation already includes a feature tile, endpoint dashboard, loopback service, bearer auth, `/healthz`, `/v1/models`, and non-streaming chat completions
- Phase 2 work such as streaming, bounded queueing, timeout handling, deeper cancellation, richer metrics, and client validation is still pending

Current blocker:

- local build/runtime validation is still blocked in this environment by the Android SDK/tooling failure (`25.0.3`)

File reference note:

- file targets in this document intentionally mix **existing files** and **proposed new files**
- `endpoint/data/DefaultEndpointRepository.kt` and `endpoint/data/EndpointModels.kt` already exist
- `endpoint/data/EndpointRepository.kt`, `endpoint/orchestration/EndpointMetrics.kt`, diagnostics/preset/profile screens, and tool-registry files are proposed additions unless implemented later

## 1. Purpose

Phase 1 establishes a working local endpoint. Phase 2 makes it reliable and usable. Phase 3 expands the endpoint into a broader on-device inference platform with richer protocol coverage, safer advanced capabilities, optional networking expansion, and stronger client ecosystem support.

This phase assumes:

- the Phase 1 endpoint slice is implemented
- Phase 2 hardening is complete enough to provide a stable base for advanced feature work
- the local endpoint is already validated and stable for single-model chat serving before Phase 3 starts landing

## 2. Phase 3 Goals

Phase 3 should add the remaining advanced features that were intentionally deferred:

- broader OpenAI-compatible API coverage
- tool/function calling
- multimodal external API support
- optional LAN-accessible mode
- model profiles and more flexible serving behavior
- richer diagnostics and exportable support information
- client presets and compatibility improvements

## 3. Out of Scope for Phase 3

Do not include these unless there is an explicit product decision later:

- public internet exposure
- cloud relay or remote tunneling
- account sync
- unrestricted arbitrary code execution through tools
- multi-device orchestration
- distributed inference or cluster-style load balancing

## 4. Architectural Principles

Phase 3 adds power, so the architecture must become more strict, not less.

### 4.1 Preserve existing boundaries

- **Service:** lifecycle only
- **Server/API layer:** HTTP routes, request parsing, response writing
- **Session/orchestration layer:** request execution, tool loop, multimodal bridging, profile routing
- **Repository/data layer:** settings, profiles, persisted feature flags, observable state
- **UI/ViewModel:** user configuration and status presentation only

### 4.2 Security-first defaults

- all new advanced features must remain disabled by default until explicitly enabled
- loopback-only remains the default binding mode
- LAN mode is opt-in and clearly marked as higher risk
- tool execution must be allowlisted and capability-scoped

### 4.3 Feature gating

Every Phase 3 major capability should be individually feature-gated:

- responses API
- embeddings
- tools/function calling
- multimodal input
- LAN mode
- advanced serving profiles

This allows controlled rollout and easier debugging.

## 5. Phase 3 Workstreams

## 5.1 Protocol expansion

### Goal

Support more OpenAI-compatible endpoints and payload shapes so a wider set of SDKs and IDE agents can use the local endpoint without custom adaptation.

### Scope

- `/v1/responses`
- embeddings endpoint
- broader field compatibility for existing chat endpoints
- more consistent OpenAI-style error shapes

### Implementation tasks

1. Audit the current request/response DTOs and split them by endpoint type if needed.
2. Add endpoint-specific DTO models for:
   - `responses`
   - `embeddings`
3. Implement route handlers in `LocalApiServer.kt` for the new endpoints.
4. Extend request parsing and validation so unsupported fields fail clearly.
5. Map errors into stable endpoint-specific error payloads.
6. Add compatibility notes describing what is:
   - fully supported
   - partially supported
   - explicitly unsupported

### File targets

- `endpoint/api/OpenAiDtos.kt`
- `endpoint/api/RequestParsers.kt`
- `endpoint/api/ResponseMappers.kt`
- `endpoint/api/LocalApiServer.kt`
- `endpoint/data/EndpointModels.kt`

### Exit criteria

- `/v1/responses` works for a minimal supported request shape
- embeddings endpoint works for supported models or fails clearly when unsupported
- unsupported parameters are rejected with stable errors

## 5.2 Tool and function calling

### Goal

Allow clients to send tool definitions and receive tool call results in an OpenAI-compatible flow, while keeping execution constrained and safe.

### Scope

- tool definition parsing
- tool call serialization
- orchestration loop from model -> tool -> result -> model
- allowlisted tool execution only
- timeout and failure behavior per tool call

### Safety model

Tool execution must not become arbitrary command execution.

Recommended rules:

- support only explicitly allowlisted tools
- require each tool to declare:
  - name
  - schema
  - timeout
  - permission/scope
- separate internal tool registry from external request DTOs
- reject tool calls when the selected model/profile does not support them

### Implementation tasks

1. Add tool-capable DTOs for inbound client requests.
2. Create a tool registry abstraction such as:
   - `endpoint/tools/ToolRegistry.kt`
   - `endpoint/tools/ToolDefinition.kt`
   - `endpoint/tools/ToolExecutor.kt`
3. Add tool execution loop inside `EndpointSessionManager`.
4. Support one tool-call cycle first before adding more complex iterative loops.
5. Define failure behavior for:
   - unknown tool
   - invalid arguments
   - tool timeout
   - tool runtime error
6. Add UI state and diagnostics for recent tool-call failures.

### File targets

- `endpoint/tools/ToolRegistry.kt`
- `endpoint/tools/ToolDefinition.kt`
- `endpoint/tools/ToolExecutor.kt`
- `endpoint/api/OpenAiDtos.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/data/EndpointModels.kt`

### Exit criteria

- at least one safe allowlisted tool works end to end
- unsupported or unsafe tool requests fail explicitly
- tool timeouts and failures do not wedge the request loop

## 5.3 Multimodal external API

### Goal

Expose image and audio input over the API for compatible models while reusing the app’s existing multimodal preprocessing paths where possible.

### Scope

- image input handling
- audio input handling
- validation and size limits
- mapping external input into runtime-compatible structures

### Implementation tasks

1. Define supported payload forms for multimodal input:
   - URL references if safe and local-only
   - base64 payloads
   - multipart/form-data if needed
2. Add explicit size limits for image and audio payloads.
3. Reuse existing media preprocessing helpers where possible instead of reimplementing them.
4. Add capability checks so only compatible models accept multimodal requests.
5. Return `400` or `422` style failures for invalid media payloads.
6. Surface preprocessing errors clearly in logs and diagnostics.

### File targets

- `endpoint/api/RequestParsers.kt`
- `endpoint/api/OpenAiDtos.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- existing shared media helpers under `common/` or multimodal runtime bridge files

### Exit criteria

- image requests work for at least one compatible model path
- audio requests work for at least one compatible model path
- invalid or oversized payloads fail safely

## 5.4 LAN mode

### Goal

Allow the endpoint to be exposed on the local network as an explicit, advanced, opt-in capability.

### Scope

- configurable binding mode
- stronger auth and user warnings
- network exposure visibility in UI
- security review of non-loopback serving

### Security requirements

LAN mode should require more than just copying Phase 1 local behavior.

Recommended additions:

- explicit enable toggle
- warning/confirmation flow
- stronger token requirements and rotation prompt
- visible “LAN mode active” status in the dashboard
- optional IP allowlist if practical

### Implementation tasks

1. Extend settings to include binding mode:
   - loopback
   - LAN
2. Update the server binding logic to respect the selected binding mode.
3. Detect and display the current device IP address when LAN mode is active.
4. Add UX warnings explaining the increased exposure risk.
5. Require re-confirmation when enabling LAN mode for the first time.
6. Add tests verifying loopback remains the default even after upgrades.

### File targets

- `endpoint/service/EndpointForegroundService.kt`
- `endpoint/api/LocalApiServer.kt`
- `endpoint/data/EndpointRepository.kt`
- `endpoint/ui/EndpointDashboardScreen.kt`
- `endpoint/ui/EndpointSecuritySettings.kt` if a new settings component is added
- `proto/settings.proto`

### Exit criteria

- LAN mode is disabled by default
- users can explicitly opt into LAN mode
- UI clearly shows whether the server is loopback-only or LAN-accessible

## 5.5 Model profiles and advanced serving behavior

### Goal

Move beyond one static serving model and support richer serving configurations without breaking the current simple path.

### Scope

- named model profiles
- per-profile settings
- profile switching
- future-proofing for multi-model routing

### Recommended model

Do **not** jump directly to true multi-model concurrent serving unless device constraints prove it is safe.

Start with:

- named profiles
- one active profile at a time
- faster switching between profiles
- separate config per profile

### Profile fields

Each profile may include:

- profile name
- target model name
- endpoint defaults such as temperature, top_p, max_tokens
- allowed advanced features
- tool support toggle
- multimodal support toggle if model supports it

### Implementation tasks

1. Add a persisted profile model.
2. Add CRUD operations for profiles in the repository.
3. Update the dashboard to:
   - create profile
   - edit profile
   - select active profile
4. Update routing/session logic to resolve the active profile first, then the model.
5. Keep the existing single-default-model path as a backward-compatible fallback until migration is complete.

### File targets

- `endpoint/data/EndpointRepository.kt`
- `endpoint/data/DefaultEndpointRepository.kt`
- `endpoint/data/EndpointModels.kt`
- `endpoint/ui/EndpointProfileEditor.kt` *(proposed new file)*
- `endpoint/ui/EndpointProfilePicker.kt` *(proposed new file)*
- `proto/settings.proto`

### Exit criteria

- users can create and switch named serving profiles
- profile changes do not destabilize the running endpoint

## 5.6 Diagnostics and support tooling

### Goal

Provide enough information to debug advanced failures and collect support data without exposing sensitive prompt content.

### Scope

- richer diagnostics screen
- exportable diagnostics bundle
- compatibility report
- non-sensitive request/session history

### Implementation tasks

1. Add a diagnostics view showing:
   - server mode
   - active profile
   - current model
   - recent failures
   - uptime
   - queue stats
   - route usage
2. Add exportable diagnostics bundle generation with:
   - version info
   - endpoint settings summary
   - profile summary
   - recent non-sensitive errors
   - recent route activity metadata
3. Exclude prompt bodies, completions, and tokens from exported bundles.
4. Add compatibility summary indicating which advanced capabilities are enabled.

### File targets

- `endpoint/diagnostics/EndpointDiagnosticsBuilder.kt` *(proposed new file)*
- `endpoint/ui/EndpointDiagnosticsScreen.kt` *(proposed new file)*
- `endpoint/orchestration/EndpointMetrics.kt` *(proposed new file if metrics are extracted)*
- `endpoint/data/EndpointRepository.kt` *(proposed abstraction if repository interface is introduced)*

### Exit criteria

- a user can export a non-sensitive diagnostics bundle
- advanced failures are diagnosable without direct adb access

## 5.7 Client presets and ecosystem support

### Goal

Make the endpoint easier to adopt by real clients and reduce manual configuration friction.

### Scope

- client presets
- compatibility matrix
- ready-to-copy config snippets
- known limitations per client type

### Implementation tasks

1. Define a client preset model, such as:
   - display name
   - expected base URL path
   - auth style
   - recommended endpoint type
   - notes
2. Add built-in presets for a small initial set of OpenAI-compatible clients.
3. Add UI snippets showing how to configure a supported client.
4. Maintain a compatibility table in docs:
   - fully working
   - partially working
   - unsupported

### File targets

- `endpoint/data/ClientPreset.kt` *(proposed new file)*
- `endpoint/ui/EndpointClientPresets.kt` *(proposed new file)*
- docs files in `primordial/` or repository docs once stabilized

### Exit criteria

- users can choose from preset client guidance instead of configuring everything manually
- at least two client flows are documented and validated

## 6. Data Model Changes

Phase 3 likely requires the endpoint settings model to expand.

### 6.1 Suggested additions

- `binding_mode`
- `advanced_features_enabled`
- `selected_profile_id`
- `profiles`
- `lan_mode_acknowledged`
- `tool_calling_enabled`
- `multimodal_api_enabled`
- `responses_api_enabled`
- `embeddings_enabled`

### 6.2 Storage guidance

- non-sensitive flags and profiles go in `Settings`
- secrets remain in `UserData.secrets`
- diagnostics exports should be generated on demand, not stored indefinitely

## 7. Recommended Delivery Order

Implement Phase 3 in this order:

1. protocol expansion
2. tool/function calling
3. multimodal API
4. model profiles
5. LAN mode
6. diagnostics
7. client presets and ecosystem support

Reasoning:

- protocol and tool support unlock the biggest capability gains first
- multimodal and profile work build on the orchestration/data model
- LAN mode is more security-sensitive and should land after the advanced local path is stable
- diagnostics and client presets benefit from the completed feature surface

## 8. Testing Plan

## 8.1 Unit tests

Add tests for:

- new DTO parsing and validation
- tool registry allowlist enforcement
- tool timeout/error mapping
- multimodal payload validation
- profile resolution and switching
- LAN binding selection
- diagnostics redaction

## 8.2 Integration tests

Add integration coverage for:

- `responses` endpoint flow
- embeddings flow
- one safe tool-call round-trip
- image/audio request path
- profile switching while idle and while active
- loopback vs LAN binding mode

## 8.3 Manual validation matrix

Run at minimum:

1. `/v1/responses` success path
2. embeddings success path or unsupported-model path
3. one allowlisted tool call
4. unknown tool rejection
5. invalid tool arguments
6. image request with supported model
7. audio request with supported model
8. oversized media payload rejection
9. profile creation and switching
10. enable LAN mode and verify binding + warning UX
11. export diagnostics bundle
12. configure at least two real clients using presets/docs

## 9. Acceptance Criteria

Phase 3 is complete when:

- at least one additional OpenAI-compatible endpoint beyond chat is implemented and stable
- at least one safe allowlisted tool-call path works end to end
- multimodal API support works for supported models
- users can create and switch serving profiles
- LAN mode exists as an explicit, opt-in advanced feature
- diagnostics export works without leaking sensitive prompt/token data
- client presets and compatibility guidance are available for multiple client types

## 10. Nice-to-Have Items If Time Remains

Only consider these after all Phase 3 acceptance criteria are met:

- per-client saved profiles
- richer route analytics
- profile import/export
- optional limited request history metadata
- more than one tool-call round-trip per request

## 11. Recommended End State After Phase 3

After this phase, the Android local API endpoint should no longer be just a chat-serving utility. It should function as an advanced on-device endpoint platform with:

- richer protocol compatibility
- constrained tool execution
- multimodal request support
- configurable serving profiles
- optional advanced networking mode
- practical diagnostics and client onboarding support

At that point, any future work should likely move into Phase 4 and focus on productization, deeper ecosystem integrations, and carefully scoped remote-access capabilities rather than basic endpoint feature expansion.
