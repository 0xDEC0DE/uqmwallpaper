# Software Design Document
## UQM Live Wallpaper — Android Application

---

## 1. Introduction

### 1.1 Purpose
This document describes the software design for the **UQM Live Wallpaper** Android application. It defines the architecture, component responsibilities, data flows, interface contracts, and quality requirements sufficient to guide implementation and verification.

### 1.2 Scope
The application renders animated alien communication screens from *The Ur-Quan Masters (UQM)* as an Android Live Wallpaper. It supports independent configurations for the Home Screen and Lock Screen, and provides a settings UI for user customization.

### 1.3 Definitions
| Term                | Definition                                                                           |
|---------------------|--------------------------------------------------------------------------------------|
| **Content Pack**    | A `.uqm` file (ZIP archive) containing game bitmaps and `.ani` animation descriptors |
| **Staged Settings** | Volatile in-memory settings associated with a Preview engine; never persisted        |
| **Live Settings**   | Persisted settings associated with an active wallpaper surface                       |
| **Commitment**      | The act of a Live engine accepting staged settings and persisting their values       |

### 1.4 References
- Android `WallpaperService` API documentation
- *The Ur-Quan Masters* open-source repository (animation engine logic)
- Material Design 3 guidelines

---

## 2. System Overview

The application is an Android Live Wallpaper service. Its primary responsibilities are accessing assets from a bundled ZIP, rendering frame-accurate UQM animations, and managing independent configurations per surface.

### 2.1 Context Diagram
```
┌────────────────────────────────────────────────────────┐
│                    Android System                      │
│ ┌──────────────┐    ┌────────────────────────────────┐ │
│ │ Wallpaper    │───▶│       UQMWallpaper Service     │ │
│ │ Picker / WM  │    │  ┌──────────┐  ┌────────────┐  │ │
│ └──────────────┘    │  │ Preview  │  │   Live     │  │ │
│                     │  │ Engine(s)│  │ Engine(s)  │  │ │
│ ┌──────────────┐    │  └──────────┘  └────────────┘  │ │
│ │ Settings UI  │───▶│                                │ │
│ └──────────────┘    └────────────────────────────────┘ │
│                             │                          │
│                   ┌─────────▼──────────┐               │
│                   │    Content Source  │               │
│                   │ (Cached ZIP File)  │               │
│                   └────────────────────┘               │
└────────────────────────────────────────────────────────┘
```

---

## 3. Constraints and Assumptions

### 3.1 Technical Constraints
- **Language:** Java 17 | **Min SDK:** 34 | **Target SDK:** 36
- **Package Name:** `net.submedia.android.uqmlivewallpaper`
- **Asset Layout:** ZIP entry paths and animation timings are defined by XML files in `res/values/`.
- **ZIP Strategy (Baseline):** Copy `.uqm` from assets to `getExternalCacheDir()` on first run to enable random-access `ZipFile` performance via `FileChannel`.
- **ZIP Strategy (Research):** Evaluate if `AssetManager` or `AssetFileDescriptor` can provide performant `seek()`-capable random access directly from the APK to eliminate the need for a cache copy.

---

## 4. Architecture Design

### 4.1 Architectural Style
**Service-driven, observer pattern:**
- `UQMWallpaper` (Service) manages all engine instances and global staged settings.
- Each engine delegates rendering state to a `WallpaperViewModel`.
- `WallpaperSettings` (DataStore) acts as a reactive DTO between UI and Engine.

### 4.2 Settings Lifecycle: Staging and Commitment

#### States
`STAGED ────▶ COMMITTED ────▶ [nulled]`

#### Sequence
| Step                                 | Actor          | Action                                                                                |
|--------------------------------------|----------------|---------------------------------------------------------------------------------------|
| 1. Preview created                   | Service        | Clones Live Settings -> Staged Settings (State: `STAGED`)                             |
| 2. User interacts                    | Preview Engine | Mutates Staged Settings in-memory; no I/O permitted                                   |
| 3. "Set Wallpaper"                   | Android System | Launches new Engine, or issues `android.wallpaper.reapply` command to existing engine |
| 4. Engine start, OR Command received | Any engine     | Transitions Staged Settings to `COMMITTED` state                                      |
| 5. Adoption                          | Live Engine    | Copies committed values -> Live Settings, persists to disk, clears staged             |

**Invariant:** Disk writes are only permitted from a Live engine during the Commitment step.

---

## 5. Component Design

### 5.1 `UQMWallpaper` (Service)
- Transitions staged settings to `COMMITTED`, either from new Engine starts, or `android.wallpaper.reapply` commands from the system.
- Manages an `ExecutorService` for background asset loading.

### 5.2 `CommsEngine` (Engine)
- **Deferred initialization:** Bitmaps are not loaded until the engine surface becomes visible.
- **Loading UX:** Renders a status message while assets are being initialized.
- **Cleanup:** Explicitly recycles bitmaps and closes file handles in `onDestroy`.

### 5.3 `WallpaperViewModel` (Logic)
- **Threading:** Owns a `HandlerThread` ("WallpaperWorker") to drive the render loop off the main thread.
- **Positioning:** Calculates `DestRect` for the animation and manages parallax background offsets.

### 5.4 `WallpaperSettings` (DataStore)
- Implements `PreferenceDataStore`.
- **I/O Guard:** Throws `IllegalStateException` if `save()` is called on non-LIVE settings.

### 5.5 `Animation` (Port)
- **Task:** Composites frames by "stamping" animation parts onto a background via `Canvas`.
- **Logic:** Ported from UQM C-engine (Circular, Yo-Yo, Random animation types).
- **Frame Blocking:** Prevents concurrent conflicting animations via a frame-lock (`BlockMask`) mechanism.

---

## 6. Data Design

### 6.1 Resource Mapping (XML)
`race_name` -> `string-array` (Index 0: directory; Index 1-N: 8-field descriptors for timing/flags).

### 6.2 Migration Logic
**Legacy Scaling:** If legacy key `scaling` exists, map to modern `scalingfactor` (0.0 or 100.0) and remove legacy key.

---

## 8. Error Handling and Logging

### 8.1 Logging Levels
| Level   | Events                                         |
|---------|------------------------------------------------|
| `ERROR` | Unrecoverable failures (e.g., missing ZIP).    |
| `WARN`  | Unexpected state or non-fatal exceptions.      |
| `INFO`  | Lifecycle, commitment, and persistence events. |
| `DEBUG` | Surface changes, animation loads.              |

---

## 9. Non-Functional Requirements

### 9.1 Performance
- Render loop must not block the main thread.
- `RGB_565` color depth used for opaque assets to reduce heap pressure.

### 9.2 Memory Management
- Bitmaps and ZIP handles must be released in `onDestroy`.
- Deferred initialization prevents LMK (Low-Memory Killer) restart loops.

---

## 10. Quality Assurance

### 10.1 Coverage Standards
- **100% Line Coverage:** All production code must be covered by unit tests.
- **Maximized Branch Coverage:** Maximize coverage for state machine and animation logic.

### 10.2 User Acceptance Testing (Manual)
- First-run default race rendering.
- Preview -> Apply -> Commitment flow verification.
- Independent Home/Lock configurations.
- Persistence across process restart.
