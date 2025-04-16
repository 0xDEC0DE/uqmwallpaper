# Agent Rules & Instructions

You are an expert Android Developer assistant. This file contains the primary constraints and operational rules for this repository.

## 1. Project Identity
- **Package Name:** `net.submedia.android.uqmlivewallpaper`
- **Tech Stack:** Java 17, Min SDK 34, Target SDK 36.
- **Domain:** Android Live Wallpaper rendering UQM (The Ur-Quan Masters) animations.

## 2. Core Technical Constraints
- **Bitmap Config:** Always use `Bitmap.Config.RGB_565` for opaque assets to minimize heap pressure.
- **ZIP Access:** Access assets directly from the APK via `AssetFileDescriptor` and `FileChannel`. This avoids a slow "Copy-to-Cache" step. Assets must be stored uncompressed in the APK (via `noCompress` in `build.gradle`).
- **Threading:** The render loop must never block the Main thread. Use the "WallpaperWorker" `HandlerThread`.
- **Memory:** Explicitly `.recycle()` bitmaps and close file handles in `onDestroy`. Use deferred initialization (load only when surface is visible).

## 3. Architecture & Logic
- **Settings Lifecycle:** Strictly follow the **Staging and Commitment** lifecycle defined in `SPEC.md`.
    - Disk writes are *only* permitted from a Live engine during the Commitment step.
    - `WallpaperSettings` must throw `IllegalStateException` if `save()` is called on non-LIVE settings.
- **Baton Pass Pattern:** The transition from `STAGED` to `COMMITTED` (the "Baton Pass") is the trigger for adoption. It must be executed by:
    - A `Live` engine during `onCreate()`, if `sStagedSettings` exists in a `STAGED` state.
    - Any engine during `onCommand()` when receiving `android.wallpaper.reapply`.
- **Animation Logic:** Ported from UQM C-engine (Circular, Yo-Yo, Random types). Use `BlockMask` frame-locking.

## 4. Quality Standards
- **Test Coverage:** 100% line coverage required for all production logic. Branch coverage is not strictly mandated but should be maximized for critical paths.
- **Test Stack:** JUnit 4 + Robolectric + Mockito.
- **Visibility:** Use `@VisibleForTesting` for members that must be exposed for testing.
- **Constructor Policy:** Prefer testing the "real" production constructor path via fixtures and mocks. Test-only constructors are discouraged and should be removed if the production path can be reasonably exercised.
- **Logging:** Use `INFO` for lifecycle/persistence and `DEBUG` for frame-by-frame or surface events.

## 5. Workflow
- **Verification:** Run unit tests after every code change to ensure errors are surfaced and dealt with quickly.
- Refer to `SPEC.md` for the authoritative technical design.
- Refer to `PLAN.md` for current task status and roadmap.
- When proposing changes, ensure they align with the `STAGED -> COMMITTED -> LIVE` flow.
