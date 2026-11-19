# Project Replay

**A mobile-first gaming capture and clips community for Android.**

Project Replay is a native Kotlin + Jetpack Compose app. The long-term goal is to press **Clip That**, keep the gameplay moment that just happened, then edit, save or share it.

## Implemented in this build

- Native app with Home, Discover, Capture, Library and Profile tabs.
- Vice City, Christmas, Midnight and Graphite visual themes.
- Theme, replay-duration and quality preferences persist between launches.
- User-approved Android MediaProjection rolling screen capture.
- Foreground recording service with Clip That and Stop notification actions.
- H.264 MP4 segments rotate into a bounded temporary replay buffer.
- Clip That assembles the most recent 15/30/60/120-second window into a saved MP4 without re-encoding.
- Data Saver, Balanced and High presets adjust recording bitrate.
- Clip segments are pinned during export so buffer cleanup cannot delete them mid-assembly.
- Recording resolution is capped at 1920 pixels on the longest edge.
- Capture refuses to start when free storage is below 500 MB and displays an actionable error.
- Local Library supports playback, sharing, deletion, search, favourites, renaming and refresh.
- Android Quick Settings tile can launch capture or stop an active session.
- GitHub Actions builds a debug APK and publishes the project-replay-debug artifact.

## Important distinction

Capture now starts a rolling, user-approved MediaProjection buffer. It rotates short H.264 MP4 segments, retains a bounded recent window and uses Clip That to assemble the selected replay duration into the local library. This first implementation uses MediaRecorder segment rotation and may have brief gaps between segments; it needs device testing across Android versions, games and display modes. Recording is video-only; microphone and game audio are not enabled.

The Home and Discover feeds still contain sample content. Accounts, uploads, a live social feed, creator interactions, thumbnails and a clip editor are not connected yet.

## Build an APK

1. Open the repository in Android Studio.
2. Use JDK 17 and install Android SDK 35.
3. Run `gradle assembleDebug`.
4. GitHub Actions also builds a debug APK on pushes to main. Download the `project-replay-debug` artifact from a successful Actions run.

## Technical direction

See [the engineering roadmap](docs/ENGINEERING_ROADMAP.md) for the segment-based MediaCodec replay buffer, storage management, compression strategy, social milestones and privacy requirements.

Screen capture requires explicit Android MediaProjection consent and a visible foreground service. Project Replay does not silently record gameplay.
