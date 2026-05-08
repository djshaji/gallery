# Phase 4 Implementation Plan: Productization, Rollout Safety, and Supportability

## 0. Current Status

Phase 4 remains future work. It should be read as the productization layer that follows a validated Phase 1 slice, completed Phase 2 hardening, and completed Phase 3 advanced capability work.

Current baseline status:

- the Phase 1 endpoint slice is implemented in code
- Phase 2 and Phase 3 are still planned, not completed
- productization work should not begin as if the broader advanced feature surface already exists

Current blocker:

- local build/runtime validation is still blocked in this environment by the Android SDK/tooling failure (`25.0.3`)

File reference note:

- file targets in this document intentionally mix **existing files** and **proposed new files**
- `endpoint/data/DefaultEndpointRepository.kt` and `endpoint/data/EndpointModels.kt` already exist
- `endpoint/data/EndpointRepository.kt`, `endpoint/orchestration/EndpointMetrics.kt`, diagnostics/self-test/onboarding screens, and client-preset files are proposed additions unless implemented later

## 1. Purpose

Phase 1 makes the endpoint work. Phase 2 makes it reliable and usable. Phase 3 adds advanced capability. Phase 4 turns the feature into something that can be shipped, maintained, upgraded, supported, and validated across a broader set of devices and users.

This phase assumes:

- the Phase 1 endpoint slice is implemented and validated
- Phase 2 is complete
- Phase 3 is complete
- the endpoint already supports the intended local and advanced feature surface before Phase 4 rollout work begins

## 2. Phase 4 Goals

Phase 4 should focus on productization rather than adding another wave of core endpoint capability.

The primary goals are:

- safe rollout and upgrade behavior
- configuration migration and backward compatibility
- performance tuning across device tiers
- long-run stability and graceful degradation
- governance and policy controls
- support tooling and diagnostics quality
- validated client compatibility per release
- polished onboarding and documentation

## 3. Out of Scope for Phase 4

Unless explicitly approved as a separate initiative, Phase 4 should still exclude:

- public internet exposure
- cloud relay or remote tunneling
- account-linked remote access
- shared/team endpoint management
- multi-device orchestration
- unrestricted remote tool execution

Those should be considered Phase 5+ or separate security-reviewed tracks.

## 4. Phase 4 Productization Principles

### 4.1 Compatibility first

Every new Phase 4 change must preserve compatibility for existing users where possible:

- existing settings must continue to load
- existing serving profiles must continue to resolve safely
- unsupported old states must fail gracefully and recoverably

### 4.2 Safe defaults always win

If there is tension between convenience and safety:

- keep loopback as the default
- keep risky advanced features explicitly opt-in
- recover to conservative defaults on corrupt or unknown configuration

### 4.3 Recoverability matters

Users should be able to recover from:

- broken profiles
- missing models
- corrupted settings
- upgrade mismatches
- failed service startup

without clearing all app data.

### 4.4 Release validation is part of implementation

Phase 4 is not done when code compiles. It is done when the feature can be validated, supported, and upgraded with confidence.

## 5. Phase 4 Workstreams

## 5.1 Settings migration and backward compatibility

### Goal

Make all endpoint settings, profiles, and advanced feature flags resilient across app upgrades.

### Scope

- schema migration
- default injection for new fields
- fallback handling for removed or unsupported fields
- compatibility checks for legacy saved profiles

### Implementation tasks

1. Audit all endpoint-related settings and identify fields introduced in Phases 1-3.
2. Add explicit migration logic for:
   - old settings without newer fields
   - profiles referencing missing models
   - advanced flags no longer supported on the current build
3. Define safe fallback rules such as:
   - unknown binding mode -> loopback
   - missing selected profile -> no active profile
   - removed model -> profile disabled until user reselects
4. Add version-aware migration tests.
5. Add non-destructive recovery behavior instead of silently clearing endpoint state.

### File targets

- `proto/settings.proto`
- `data/DataStoreRepository.kt`
- `endpoint/data/DefaultEndpointRepository.kt`
- `endpoint/data/EndpointModels.kt`
- migration helper files if a dedicated migration layer is introduced

### Exit criteria

- upgraded installs preserve compatible settings
- incompatible saved state recovers to safe defaults with clear user-visible explanation

## 5.2 Performance tuning and device-tier strategy

### Goal

Tune endpoint behavior so it performs reasonably across a range of Android devices, runtimes, and models.

### Scope

- queue and timeout tuning
- warm-start optimization
- per-device recommendations
- memory-aware behavior
- benchmark-based defaults

### Implementation tasks

1. Define a benchmark matrix across:
   - low-memory devices
   - mid-tier devices
   - high-end devices
   - LiteRT-LM and AI Core paths where applicable
2. Measure:
   - cold start latency
   - warm start latency
   - average first-token latency
   - total completion latency
   - memory usage under idle and active load
3. Tune:
   - request timeout defaults
   - queue size defaults
   - profile defaults by device tier
4. Add device-tier recommendation logic for:
   - preferred serving profile
   - safer model selection suggestions
   - recommended advanced feature availability
5. Add graceful degradation rules under memory pressure.

### File targets

- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/orchestration/EndpointMetrics.kt` *(proposed new file if metrics are extracted)*
- `endpoint/data/EndpointRepository.kt` *(proposed abstraction if repository interface is introduced)*
- `endpoint/ui/EndpointDashboardScreen.kt`
- profile recommendation helpers if added

### Exit criteria

- the endpoint behaves predictably across representative devices
- defaults are no longer one-size-fits-all when device characteristics clearly differ

## 5.3 Reliability and long-run stability

### Goal

Make the endpoint durable across long sessions, repeated state changes, and Android lifecycle pressure.

### Scope

- repeated start/stop cycles
- long-running service stability
- repeated profile switching
- memory pressure handling
- background/foreground interruption resilience

### Implementation tasks

1. Add stress scenarios covering:
   - repeated server start/stop
   - repeated profile switching
   - repeated token rotation
   - long-lived server uptime
2. Add watchdog-style internal state validation so stuck states can be detected.
3. Improve cleanup for:
   - failed model init
   - request timeout storm
   - service interruption
4. Add user-visible recovery actions such as:
   - reset active endpoint session
   - disable broken profile
   - restore safe defaults
5. Validate behavior when:
   - app is backgrounded
   - process is killed
   - resources are constrained

### File targets

- `endpoint/service/EndpointForegroundService.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/data/DefaultEndpointRepository.kt`
- `endpoint/ui/EndpointDiagnosticsScreen.kt`

### Exit criteria

- the endpoint survives normal long-running usage without state drift
- recovery actions exist for the most common failure modes

## 5.4 Governance and policy controls

### Goal

Provide safe administrative and product-level control over advanced features.

### Scope

- global feature switches
- per-profile capability restrictions
- policy enforcement for risky capabilities
- stronger gating for LAN mode and tools

### Implementation tasks

1. Add a policy model covering:
   - LAN mode allowed
   - tools allowed
   - multimodal API allowed
   - advanced protocol endpoints allowed
2. Enforce policy in orchestration and server layers, not just UI.
3. Add global capability checks before:
   - starting LAN mode
   - allowing tool execution
   - enabling advanced feature profiles
4. If enterprise/device-owner use matters, identify where managed configuration hooks would fit.
5. Expose effective policy state in diagnostics.

### File targets

- `endpoint/data/EndpointModels.kt`
- `endpoint/data/DefaultEndpointRepository.kt`
- `endpoint/orchestration/EndpointSessionManager.kt`
- `endpoint/api/LocalApiServer.kt`
- `endpoint/ui/EndpointSecuritySettings.kt`

### Exit criteria

- risky capabilities can be disabled globally
- policy is enforced consistently even if a client sends a technically valid request

## 5.5 Support tooling and diagnostics maturity

### Goal

Make it practical to troubleshoot user reports and reproduce failures without exposing sensitive model inputs.

### Scope

- richer diagnostics bundle
- error taxonomy
- remediation hints
- self-test / readiness validation

### Implementation tasks

1. Define an explicit error taxonomy:
   - configuration error
   - auth error
   - policy error
   - model availability error
   - runtime error
   - timeout/backpressure error
   - client compatibility error
2. Attach remediation hints to common failure classes.
3. Expand diagnostics export to include:
   - app/build version
   - endpoint feature flags
   - active profile summary
   - compatibility mode summary
   - recent non-sensitive route/error metadata
4. Add a self-test flow in the UI, such as:
   - verify model selected
   - verify model downloaded
   - verify service start
   - verify local auth
   - verify `/healthz`
5. Ensure prompt contents, completions, and secrets remain excluded.

### File targets

- `endpoint/diagnostics/EndpointDiagnosticsBuilder.kt`
- `endpoint/ui/EndpointDiagnosticsScreen.kt` *(proposed new file)*
- `endpoint/ui/EndpointSelfTestScreen.kt` if separated *(proposed new file)*
- `endpoint/data/EndpointRepository.kt` *(proposed abstraction if repository interface is introduced)*
- `endpoint/orchestration/EndpointMetrics.kt` *(proposed new file if metrics are extracted)*

### Exit criteria

- support artifacts are actionable
- the app can guide the user through common failure diagnosis without adb

## 5.6 Client certification and release validation

### Goal

Define what “supported client” means and validate it per release.

### Scope

- supported client list
- certification checklist
- compatibility matrix
- release validation workflow

### Implementation tasks

1. Define supported client tiers:
   - officially supported
   - best-effort compatible
   - experimental
2. For each supported client, validate:
   - auth
   - model discovery
   - non-streaming behavior
   - streaming behavior
   - advanced capabilities if applicable
3. Version the compatibility matrix with app releases.
4. Add regression checks for known supported configurations.
5. Keep client presets aligned with certified behavior.

### File targets

- `endpoint/data/ClientPreset.kt` *(proposed new file)*
- docs or compatibility files in `primordial/` / repository docs
- release validation notes if maintained alongside docs

### Exit criteria

- each release has a defined compatibility statement
- supported clients are validated intentionally, not assumed

## 5.7 Onboarding and first-run setup polish

### Goal

Reduce friction for new users adopting the feature.

### Scope

- first-run setup flow
- setup wizard/checklist
- clearer advanced feature explanations
- quick-copy config snippets

### Implementation tasks

1. Add a first-run onboarding flow for the endpoint feature covering:
   - what the endpoint does
   - how local auth works
   - how to choose a model
   - how to start the service
2. Add quick-start snippets for certified clients.
3. Add contextual explanations for advanced settings like:
   - LAN mode
   - tools
   - multimodal API
   - profiles
4. Add “recommended settings” suggestions based on device tier and installed models.

### File targets

- `endpoint/ui/EndpointOnboardingScreen.kt` *(proposed new file)*
- `endpoint/ui/EndpointDashboardScreen.kt`
- `endpoint/ui/EndpointClientPresets.kt` *(proposed new file)*
- `res/values/strings.xml`

### Exit criteria

- a new user can reach a working endpoint setup with minimal trial and error

## 5.8 Documentation completion

### Goal

Bring the docs from developer-oriented to release-ready.

### Scope

- polished setup docs
- troubleshooting flow
- compatibility documentation
- advanced feature reference

### Documentation tasks

1. Create/update:
   - quick start guide
   - supported clients guide
   - troubleshooting guide
   - advanced settings reference
2. Keep examples aligned with actual certified client paths.
3. Add explicit notes on:
   - local-only default behavior
   - LAN risk model
   - feature gating
   - device-dependent performance expectations

### Exit criteria

- release-ready docs exist for the feature
- docs reflect validated behavior rather than aspirational capability

## 6. Suggested Settings and Data Model Additions

Phase 4 may require additional persisted state such as:

- `settings_schema_version`
- `selected_profile_version`
- `last_successful_self_test_at_ms`
- `last_migration_status`
- `lan_mode_warning_acknowledged`
- `certified_client_last_selected`

Recommended rule:

- keep persistent state minimal and intentional
- do not store sensitive request content
- avoid turning diagnostics into a hidden request log

## 7. Recommended Delivery Order

Implement Phase 4 in this order:

1. settings migration and compatibility
2. reliability and recovery
3. performance tuning and device-tier strategy
4. governance and policy controls
5. diagnostics and self-test maturity
6. client certification and release validation
7. onboarding and documentation polish

Reasoning:

- migration and recovery must land before broad rollout
- performance work is more valuable once stability is good
- certification and onboarding should build on stable, validated behavior

## 8. Testing Plan

## 8.1 Unit tests

Add tests for:

- settings migration
- profile fallback behavior
- policy enforcement
- diagnostics redaction
- self-test logic
- recommendation/fallback logic by device tier

## 8.2 Integration and endurance tests

Add coverage for:

- upgrade from older settings schema
- corrupted endpoint settings recovery
- repeated start/stop across many cycles
- repeated profile switch across many cycles
- long-running active service
- self-test success/failure paths

## 8.3 Release validation checklist

Per release, validate at minimum:

1. clean install setup flow
2. upgrade from previous version
3. loopback local serving
4. advanced enabled features still behave as documented
5. diagnostics export
6. supported client configuration paths
7. safe fallback when a configured model is missing

## 9. Acceptance Criteria

Phase 4 is complete when:

- endpoint settings and profiles survive upgrades safely
- broken or outdated saved state recovers gracefully
- the endpoint is stable under long-run and repeated-use conditions
- advanced capabilities can be governed and restricted safely
- diagnostics and self-test features are good enough for real support workflows
- the supported client matrix is validated per release
- onboarding and docs are polished enough for broader adoption

## 10. Nice-to-Have Items If Time Remains

Only consider these after the acceptance criteria are met:

- profile import/export
- richer recommendation engine for models/profiles
- optional lightweight, non-sensitive request history metadata
- issue-report prefill using diagnostics bundle summary

## 11. Recommended End State After Phase 4

After this phase, the endpoint should be considered a mature product feature rather than an experimental engineering capability. It should be safe to roll out more broadly, resilient to upgrades and failures, diagnosable in the field, and supported by validated client guidance.

At that point, the remaining work should likely move into a future phase focused on explicitly higher-risk areas such as remote access, broader ecosystem distribution, or deeply managed enterprise scenarios.
