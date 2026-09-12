# OwnTV: End-to-End System Architecture & Build Engineering Guide
## How the App Was Built, Designed, and Scaled for Android TV & Amazon Fire TV

This document explains the complete, end-to-end architecture of **OwnTV**—from the low-level native playback engine and database pipeline to the Jetpack Compose TV UI and the Android API 25 / Fire OS 6 refactoring. 

Use this guide to explain the technical decisions, trade-offs, and implementation details during software engineering interviews.

---

## 1. System Architecture Overview

OwnTV is built using **Clean Architecture** principles and an **MVI/MVVM** reactive state model, split across three decoupled Gradle modules:

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 :app (Application Layer)               │
                  │  - Jetpack Compose for TV (Material 3)                 │
                  │  - Remote D-Pad Navigation & Focus Hierarchy           │
                  │  - ViewModels & UI State Flows                         │
                  │  - MainActivity, Glassmorphism & Themes                │
                  └───────────────────────────┬────────────────────────────┘
                                              │
                       ┌──────────────────────┴──────────────────────┐
                       ▼                                             ▼
┌──────────────────────────────────────────┐   ┌──────────────────────────────────────────┐
│        :player-core (Playback Engine)    │   │           :core (Data & Business)        │
│  - Dual-Engine: libmpv (FFmpeg) + Media3 │   │  - Room DB (SQLite WAL mode)             │
│  - Surface Lifecycle & Video Rendering   │   │  - Streaming Parsers (M3U, XMLTV, JSON)  │
│  - Live Hover Preview & Auto-Frame-Rate  │   │  - Xtream Codes & Stalker Portal Clients │
│  - Audio Focus & Hardware Volume Boost   │   │  - WorkManager Background Sync & Cache   │
│  - Decoder Watchdog & Stream Failover    │   │  - Android TV Launcher Integration       │
└──────────────────────────────────────────┘   └──────────────────────────────────────────┘
```

### Module Responsibilities

1. **`:app` (UI & Presentation)**:
   - Built 100% with **Jetpack Compose for TV**.
   - Handles remote control input (D-Pad, Center, Back, Media keys, Channel +/-).
   - Multi-pane navigation shell: Navigation Rail $\rightarrow$ Category Folders $\rightarrow$ Content Grid $\rightarrow$ Live Video Preview.
   - Screen-level features: Live TV, EPG Guide Grid, VOD Movies, Series with Seasons/Episodes, Search, Profiles, and Settings.

2. **`:player-core` (Native Video & Audio Engine)**:
   - Houses the **Dual-Engine Playback Architecture** combining `libmpv` (C/FFmpeg via JNI) and `Media3 / ExoPlayer`.
   - Manages Android `SurfaceView` lifecycles, aspect ratio calculation, and freeze-frame channel transitions.
   - Auto Frame Rate (AFR) controller to eliminate 24fps/25fps display judder.
   - Low-latency live channel preview engine and stalled video decoder watchdog.

3. **`:core` (Data, Storage, & Domain)**:
   - Ingests and parses external IPTV protocols: M3U/M3U Plus playlists, Xtream Codes REST API, and Stalker/Ministra portals.
   - Room SQLite Database with Write-Ahead Logging (WAL) and memory-bounded caching.
   - EPG matching engine linking TV channels to XMLTV scheduling data and catch-up/timeshift URLs.
   - Metadata enrichment via TMDB (The Movie Database) and OpenSubtitles.
   - Android TV Home Screen recommendations (`androidx.tvprovider`).

---

## 2. The Playback Pipeline (`:player-core`)

The most critical part of an IPTV player is handling heterogeneous, non-standardized video streams without crashing or buffering endlessly.

### The Dual-Engine Strategy: Why libmpv + ExoPlayer?

| Feature | `libmpv` (FFmpeg Native Engine) | `Media3 / ExoPlayer` (Google Engine) |
| :--- | :--- | :--- |
| **Primary Use** | Live MPEG-TS, exotic VOD codecs, interlaced 1080i/576i broadcasts | Adaptive HLS, DASH, fast in-pane preview channels |
| **Codec Support** | Plays virtually anything FFmpeg can decode (AC3, E-AC3, TrueHD, DTS, DivX, Xvid) | Restricted to hardware codecs supported by Android Stagefright |
| **Subtitles** | Embedded ASS/SSA, PGS bitmap subs, DVB subs | Text-based WebVTT, TTML, SRT |
| **Resource Weight** | Native C memory footprint; higher CPU on software decode | Very lightweight; low memory; instant instantiation |

#### How They Cooperate in `OwnTVPlayer.kt`:
1. **Live TV In-Pane Hover Preview (`LivePreviewEngine.kt`)**:
   When a user scrolls through channels in the TV Guide or Channel list, hovering over a channel starts a lightweight **ExoPlayer** preview in a sub-window. ExoPlayer connects quickly and buffers lightly.
2. **Promotion to Fullscreen**:
   When the user clicks **Select**, if the stream is standard HLS, ExoPlayer seamlessly takes the full screen. If the stream is raw MPEG-TS, interlaced, or has unsupported audio, `OwnTVPlayer` automatically promotes it to `libmpv`.
3. **Decoder Watchdog (`NoFrameWatchdog.kt`)**:
   IPTV broadcast feeds often drop packets or have clock discontinuities. The watchdog monitors video rendering timestamps. If no video frame renders for 3 seconds while audio plays (or vice versa), the player automatically triggers a demuxer flush or restarts the stream on the fallback engine.

### Auto Frame Rate (AFR) in `FrameRateController.kt`
- European TV broadcasts at **25 fps / 50 fps**; US movies broadcast at **23.976 fps / 24 fps**; TV displays run at **60 Hz**.
- 24 fps on a 60 Hz display produces noticeable stutter known as **3:2 pulldown judder**.
- `FrameRateController` uses `WindowManager.LayoutParams.preferredDisplayModeId` (API 23+) to query the TV panel's supported display modes via `DisplayManager`.
- It matches video stream FPS to display refresh rates (e.g. 24 fps $\rightarrow$ 24 Hz, 25 fps $\rightarrow$ 50 Hz, 30 fps $\rightarrow$ 60 Hz), eliminating judder while respecting user settings and cooldown timers to prevent excessive HDMI blanking handshakes.

### True Native 4K Playback Pipeline & Display Mode Resolution Switching
On Android TV and Fire OS devices (such as the Fire TV Stick 4K), achieving crisp, true native 4K (3840×2160) streaming requires managing three distinct layers of the operating system:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Android TV 4K Media Stack                       │
├────────────────────────────────────────────────────────────────────────┤
│ Layer 1: Window / System UI Plane (Rendered at 1920×1080 @ 320 dpi)    │
│  - Jetpack Compose TV UI, menus, navigation rails, and player HUD      │
├────────────────────────────────────────────────────────────────────────┤
│ Layer 2: SurfaceView Hardware Overlay Plane                            │
│  - SurfaceHolder.setFixedSize(3840, 2160) allocates native 4K buffer  │
│  - SurfaceFlinger hardware composer bypasses GPU composition           │
│  - MediaCodec / libmpv decodes directly into native 4K physical buffer │
├────────────────────────────────────────────────────────────────────────┤
│ Layer 3: HDMI Physical Output Mode (DisplayManager / Display.Mode)     │
│  - WindowManager.LayoutParams.preferredDisplayModeId = 4 (3840×2160)   │
│  - TV panel switches physical HDMI scan-out from 1080p to true 4K      │
└────────────────────────────────────────────────────────────────────────┘
```

1. **The SurfaceView Downscaling Trap (`SurfaceHolder.setFixedSize`)**:
   - On Android TV, the system UI and Compose window operate at `1920x1080`.
   - When a `SurfaceView` is rendered without an explicit buffer dimension, Android invokes `holder.setSizeFromLayout()`, capping its internal graphic buffer at `1920x1080`.
   - As a result, even if an IPTV provider sends a 4K stream (3840×2160), the hardware video decoder (MediaCodec or libmpv) was forced to downscale the frame into a 1080p buffer before displaying it—causing blurry video compared to players like IMPlayer or TiviMate.
   - **Fix**: In both `MpvVideoSurface.kt` and `ExoPreviewSurface.kt`, we explicitly invoke `holder.setFixedSize(videoSize.first, videoSize.second)` when source video dimensions are known (>0). This instructs `SurfaceFlinger` to allocate a dedicated 3840×2160 hardware overlay plane, preserving every pixel.

2. **Uncapping ExoPlayer Viewport Constraints**:
   - Media3's `DefaultTrackSelector(context)` defaults to using the display window size (`1920x1080`) as its viewport ceiling.
   - On multi-variant adaptive HLS and DASH streams, ExoPlayer actively down-ranked or excluded 4K variants because their pixel count exceeded the 1080p viewport.
   - **Fix**: In `LivePreviewEngine.kt` and `ExoSubtitleEngine.kt`, we configure `DefaultTrackSelector` with `.clearViewportSizeConstraints()` and `.clearVideoSizeConstraints()`, allowing uninhibited selection of 4K bitrates.

3. **Dynamic 4K Display Mode Resolution Switching**:
   - `FrameRateController.kt` queries `display.supportedModes`. When 4K content is playing (`width >= 3840 || height >= 2160`), it requests a 4K mode (`it.physicalWidth >= 3840 && it.physicalHeight >= 2160`) via `preferredDisplayModeId`.
   - On the Fire TV Stick 4K, this switches the physical HDMI output from Mode 1 (`1920x1080@60Hz`) to Mode 4 / Mode 6 (`3840x2160@30Hz/25Hz`), providing razor-sharp native 4K output to the television.

4. **Player HUD Video Quality Selector**:
   - Added a dedicated "Quality" dialog (`HudDialog.VIDEO`) in `PlayerHud.kt` and `PlayerHudChrome.kt` featuring an "Auto (Best)" option and manual resolution/bitrate variant selection (`3840x2160 • 60fps`, `1920x1080 • 60fps`, etc.).

---

## 3. The Data & Ingestion Engine (`:core`)

IPTV services frequently provide playlists with **20,000 to 100,000+ items** and Electronic Program Guides (EPG) with hundreds of megabytes of XML. Reading this naively into Java memory crashes TV devices with an `OutOfMemoryError` instantly.

### 1. High-Performance Streaming Parsers
- **`M3uParser.kt`**: Instead of loading an entire 80 MB `.m3u` file into a `String`, it reads directly from the network stream using a `PushbackInputStream` line by line. It parses `#EXTINF:-1 tvg-id="..." group-title="..."` attributes on the fly and streams records into database transactions in 500-item chunks.
- **`XmltvParser.kt`**: EPG data arrives in compressed `.xml` or `.xml.gz` formats. `XmltvParser` uses Android’s native `XmlPullParser`. It processes `<programme start="..." stop="..." channel="...">` events sequentially and discards past programmes older than 24 hours to conserve storage.
- **`XtreamClient.kt`**: Connects to Xtream Codes `/player_api.php?username=...&password=...&action=get_live_streams`. It uses `android.util.JsonReader` in streaming token mode, never buffering the full JSON array into memory.

### 2. Room SQLite Database Architecture
- **Entities**:
  - `ChannelEntity`: Live channels, stream URLs, stream type, category ID, sort order.
  - `ProgrammeEntity`: Title, start/end timestamps, description, poster, channel association.
  - `MovieEntity` & `SeriesEntity`: VOD metadata, TMDB ratings, cast, plots.
  - `WatchHistoryEntity`: Resume positions, progress percentage, last-played timestamps.
- **Optimizations for Low-RAM TV SoCs**:
  - **Write-Ahead Logging (WAL)**: Enabled so database reads (browsing UI) never block concurrent database writes (background EPG sync).
  - **Page Cache Budgeting**: Set `PRAGMA cache_size = -8000` (caps SQLite cache at 8 MB).
  - **Synchronous = NORMAL**: Reduces disk write barriers on slow eMMC flash memory typical of Fire TV sticks.

---

## 4. The TV User Interface: Jetpack Compose for TV (`:app`)

Traditional Android TV development relied on the legacy `Leanback` library (XML fragments and ObjectAdapters). OwnTV is built entirely with modern **Jetpack Compose for TV**.

### 1. Multi-Pane Shell Architecture (`OwnTVShell.kt`)
Android TV UX requires fast, hierarchical spatial navigation:
1. **Layer 1: Navigation Sidebar**: Compact vertical icon rail that expands on focus (Home, Live, Movies, Series, Guide, Search, Settings).
2. **Layer 2: Folder / Category Rail**: Category list (e.g. "US News", "Sports 4K", "UK Entertainment").
3. **Layer 3: Content Cards**: Horizontal or vertical focusable grids presenting channels or movie posters.
4. **Layer 4: Picture-in-Picture Docked Mini-Player**: When browsing menus while a stream is playing, the video shrinks into a corner mini-player so playback is uninterrupted.

### 2. D-Pad Focus & Remote Event Engineering
- **`FocusableSurface`**: Custom Compose component handling focus state, scale animation (1.0x $\rightarrow$ 1.08x on focus), border highlight, and audio click feedback.
- **Key Interception**: Intercepts `KeyEvent` events at the root shell:
  - `KEYCODE_DPAD_UP / DOWN / LEFT / RIGHT`: Moves spatial focus between rails and cards.
  - `KEYCODE_DPAD_CENTER / KEYCODE_ENTER / NUMPAD_ENTER`: Activates selections.
  - `KEYCODE_BACK`: Custom BackHandler hierarchy (Closes overlays $\rightarrow$ Exits fullscreen $\rightarrow$ Returns focus to sidebar $\rightarrow$ Opens Exit prompt).
  - `KEYCODE_CHANNEL_UP / DOWN`: Direct live channel zapping.
  - `KEYCODE_MEDIA_PLAY / PAUSE`: Media session transport controls.

---

## 5. Porting to Fire TV Stick 4K (API 25 / Fire OS 6)

The original codebase targeted API 26–34. Adapting it to run on the 1st Gen Fire TV Stick 4K (API 25 / Android 7.1.2) required deep platform-level changes:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        Fire OS 6 (Android 7.1.2 / API 25) Port                         │
├────────────────────────────┬───────────────────────────────────────────────────────────┤
│ Challenge                  │ Architectural Solution                                    │
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ Java 8+ APIs on Android 7  │ Enabled L8 Core Library Desugaring (desugar_jdk_libs)     │
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ PixelCopy API 26 ART Crash │ Class Verification Isolation via @RequiresApi(O) helper   │
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ libmpv minSdk 26 Conflict  │ Manifest merging via tools:overrideLibrary="dev.jdtech.mpv│
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ Adaptive Icon AAPT2 Error  │ Relocated to mipmap-anydpi-v26, fallback to raster WebP   │
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ Dropped Remote Focus       │ Deterministic focus fallback: .focusRestorer(sidebarFocus)│
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ 1.5 GB RAM Memory Limit    │ android:largeHeap="true" + 8MB SQLite cache + Stream IO   │
└────────────────────────────┴───────────────────────────────────────────────────────────┘
```

### The Dalvik/ART Class Verification Fix (Deep Dive)
When an Android app runs on Android 7.1 (API 25), the ART runtime verifies bytecode. If a method contains a direct opcode call to a method introduced in API 26 (such as `PixelCopy.request(Surface, Bitmap, ...)`), ART throws a `NoSuchMethodError` as soon as the calling method is loaded—**even if wrapped in `if (SDK_INT >= 26)`**.

**Solution**: The **Class Verification Isolation Pattern**:
```kotlin
// Inside player-core/src/main/java/tv/own/owntv/player/OwnTVPlayer.kt
if (surface == null || w <= 0 || h <= 0 || android.os.Build.VERSION.SDK_INT < 26) {
    block(); return
}
// Delegates to a separate class that ART never touches on API 25:
PixelCopyApi26.request(surface, bmp, { ... }, freezeHandler)

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
private object PixelCopyApi26 {
    fun request(surface: Surface, bmp: Bitmap, listener: (Int) -> Unit, handler: Handler) {
        PixelCopy.request(surface, bmp, { listener(it) }, handler)
    }
}
```

---

## 6. CI/CD Pipeline & Fire TV "Downloader" App Sideloading

To deliver the app to Fire TV devices without requiring a developer PC:

1. **GitHub Actions Pipeline (`.github/workflows/build-and-release.yml`)**:
   - Triggers on git pushes and tags.
   - Compiles standard ARM APKs (`arm64-v8a` + `armeabi-v7a`).
   - Packages `dist/OwnTV.apk` and creates a GitHub Release.
2. **Permanent Downloader Endpoint**:
   - GitHub Releases exposes a static redirect URL:
     `https://github.com/sagi-madhav/OwnTV/releases/latest/download/OwnTV.apk`
3. **AFTVnews Short Code & Short URL**:
   - Short URL: **[`aftv.news/3293910`](https://aftv.news/3293910)**
   - Downloader Code: **`3293910`**
   - Any user opens the **Downloader** app on their TV, enters **`3293910`**, and the APK downloads and installs automatically.


---

## 7. High-Yield Interview Questions & Answers

### Q1: "Walk me through how you designed the playback engine."
> *"I designed a dual-engine architecture: `libmpv` as our native FFmpeg workhorse and `Media3 ExoPlayer` as our adaptive live engine. In IPTV, feeds vary wildly from raw MPEG-TS satellite broadcasts to modern HLS and DASH. libmpv handles raw streams, interlaced video (1080i), and multi-channel audio (AC3/E-AC3) that stock Android decoders fail on. Meanwhile, ExoPlayer powers our hover live preview system where hovering over a channel in the TV guide immediately starts a low-latency preview in a thumbnail without lagging the main UI."*

### Q2: "How do you achieve smooth 60fps UI on a low-end TV stick using Jetpack Compose?"
> *"TV processors like the MediaTek MT8695 have weak CPU single-thread performance and limited GPU fill-rates. To maintain 60fps:
> 1. We minimized recompositions by hoisting state and marking all data models `@Immutable`.
> 2. We avoided runtime blur shaders on older devices, falling back to cached pre-tinted surfaces (`supportsBackdropBlur = false` on API < 31).
> 3. We decoupled ticking playback progress clocks from UI shells so timer ticks invalidate only small text nodes rather than the full navigation tree.
> 4. We used discrete D-Pad focus groups and explicit focus requesters to avoid costly automatic spatial search traversals."*

### Q3: "How does the app handle memory management with massive IPTV playlists on a 1.5 GB device?"
> *"IPTV playlists can have over 50,000 channels. Loading that JSON or M3U file into memory at once requires 100+ MB of RAM and triggers an OOM. We solved this at three levels:
> 1. **Streaming Parsing**: We stream bytes directly using `PushbackInputStream` for M3U and `XmlPullParser` for XMLTV, processing items in 500-item chunks.
> 2. **Database Paging**: Room database queries are surfaced to Compose via `PagingData` and `collectAsLazyPagingItems`, keeping only the visible viewport of channels in memory.
> 3. **Process Configuration**: We enabled `android:largeHeap="true"` and capped Room's SQLite WAL cache to 8 MB."*

### Q4: "What was the most challenging bug you encountered when backporting to API 25?"
> *"The hardest bug was an ART runtime crash during channel switches. The app was crashing with a `NoSuchMethodError` inside `PixelCopy.request`. The original code had a guard `SDK_INT < 24`. While `PixelCopy` for `SurfaceView` exists on API 24, `PixelCopy.request` taking a raw `Surface` was only added in API 26. In Dalvik/ART, referencing unresolvable method signatures causes bytecode verification issues. I solved it using the Class Verification Isolation pattern by moving the invocation to a dedicated `@RequiresApi(O)` static helper that ART never attempts to verify on Android 7.1."*

### Q5: "A user reported that 4K live channels played clearly in competitor apps like IMPlayer, but were blurry in your player. How did you diagnose and resolve this native 4K rendering issue?"
> *"I diagnosed this at three distinct layers of the Android media and display pipeline:
> 1. **SurfaceView Buffer Allocation**: On Android TV, the Window and Compose UI render at 1080p. Without setting explicit buffer dimensions on the `SurfaceHolder`, Android defaults to `setSizeFromLayout()`, creating a 1080p graphics buffer. The hardware decoder (`MediaCodec` or `libmpv`) downscaled the 4K stream to 1080p before `SurfaceFlinger` composited it. By calling `holder.setFixedSize(width, height)` when video dimensions are received, we forced `SurfaceFlinger` to allocate a true 3840×2160 hardware overlay plane.
> 2. **TrackSelector Viewport Constraints**: Media3's `DefaultTrackSelector` defaults to bounding track selection by the current window viewport (`1920x1080`). For adaptive HLS/DASH streams, ExoPlayer filtered out or penalized 4K representations. I applied `.clearViewportSizeConstraints()` and `.clearVideoSizeConstraints()` to the track selector parameters.
> 3. **Physical HDMI Resolution Switching**: The display controller (`FrameRateController`) previously only matched refresh rates within the current physical display resolution. I enhanced the mode-matching algorithm to detect 4K video streams (`width >= 3840`) and switch the physical display output (`preferredDisplayModeId`) to the TV's native 4K HDMI mode (e.g., Mode 4: 3840×2160 @ 30Hz / Mode 6: 3840×2160 @ 25Hz).
> 
> I verified this live on a physical Fire TV Stick 4K over ADB using `dumpsys display` and logcat, confirming that the physical TV switched to `app 3840 x 2160, real 3840 x 2160` with zero dropped frames."*

