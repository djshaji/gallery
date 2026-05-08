# Copilot instructions

## Build, test, and lint

Most code changes in this repo target the Android app in `Android/src`. Run Gradle from there:

| Purpose | Command |
| --- | --- |
| Build debug APK | `cd Android/src && ./gradlew assembleDebug` |
| Build release APK | `cd Android/src && ./gradlew assembleRelease` |
| Lint | `cd Android/src && ./gradlew lintDebug` |
| Unit tests | `cd Android/src && ./gradlew testDebugUnitTest` |
| Single unit test | `cd Android/src && ./gradlew testDebugUnitTest --tests "com.google.ai.edge.gallery.ExampleTest"` |
| Instrumentation tests | `cd Android/src && ./gradlew connectedDebugAndroidTest` |

There are currently no committed test sources under `Android/src/app/src/test` or `Android/src/app/src/androidTest`, even though the Gradle module already declares unit-test and androidTest dependencies.

For local app builds that exercise Hugging Face model download/auth flows, configure both of these before running from Android Studio:

1. Replace `ProjectConfig.clientId` and `ProjectConfig.redirectUri` in `Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt`.
2. Keep `manifestPlaceholders["appAuthRedirectScheme"]` in `Android/src/app/build.gradle.kts` aligned with that redirect URI scheme.

## High-level architecture

- The main product code is the single Android app module at `Android/src/app`. The repo also contains `skills/`, which is separate static content deployed to GitHub Pages by `.github/workflows/static.yml`.
- App startup flows through `MainActivity` -> `GalleryApp` -> `GalleryNavHost`. `MainActivity` eagerly calls `ModelManagerViewModel.loadModelAllowlist()`, and navigation is centered on task selection, model selection, task execution, the global model manager, and benchmarks.
- Features are task-driven. Each capability is implemented as a `CustomTask`, registered into a Hilt multibinding set with `@IntoSet`, then surfaced on the home screen through `ModelManagerViewModel`. This is how built-in features such as AI Chat, Ask Image, Agent Skills, Tiny Garden, and Mobile Actions are wired in.
- `ModelManagerViewModel` is the hub that merges task definitions with model allowlist JSON, filters model availability, attaches models to tasks, groups tasks by category, tracks download/init state, and chooses the runtime backend.
- Persistence is built around Proto DataStore, not Room. `di/AppModule` provides serializers and stores for settings, user data, cutouts, benchmark results, and skills; `DataStoreRepository` and `SystemPromptRepository` are the main entry points.
- Model downloads are not handled directly in screens. `DownloadRepository` schedules `DownloadWorker` jobs through WorkManager, and model files live under the app external files directory using the model's normalized name and version.
- Runtime dispatch is metadata-driven: `Model.runtimeHelper` selects LiteRT-LM or AI Core based on `Model.runtimeType`.

## Key conventions

- Add new end-user capabilities as `CustomTask` implementations plus a Hilt `@IntoSet` provider. Do not hardcode new task screens directly into navigation without going through the task system.
- Treat `Task` and `Model` metadata as the source of truth for feature wiring. If a model should appear for a capability, update the allowlist/task metadata rather than manually patching UI lists.
- Built-in task IDs in `BuiltInTaskId` are reserved. New task IDs should be unique, and `isLegacyTasks()` affects which task-screen scaffold path is used.
- Use the existing repository layer for persisted state: `DataStoreRepository` for app/user state, `SystemPromptRepository` for task-specific prompt overrides, and `SystemPromptHelper.getEffectiveSystemPrompt()` when you need the resolved prompt with fallback behavior.
- Keep model download logic inside `DownloadRepository` / `DownloadWorker` and preserve the existing external-files directory layout from `Model.kt`; UI/viewmodels are expected to observe status, not reimplement file transfer behavior.
- When editing model allowlist behavior, check both the app code and the repository-level allowlist JSON files (`model_allowlist.json`, `model_allowlists/`). `ModelManagerViewModel` can fetch allowlists remotely and also cache/load them from disk.
- The `skills/` tree is content for hosted agent skills, not Android source. Changes there affect the GitHub Pages deployment path and the skill-sharing flow rather than the Android Gradle build.
