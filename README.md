# Project Replay

**A mobile-first gaming capture and clips community for Android.**

Project Replay is a native Kotlin + Jetpack Compose app. The long-term goal is simple: press **Clip That** and keep the gameplay moment that just happened, then edit it, save it or share it with the community.

## Implemented in this build

- Native Android application with Home, Discover, Capture, Library and Profile tabs.
- Four visual themes: Vice City (yellow/purple), Christmas (red/green), Midnight and Graphite.
- Theme, replay-duration and video-quality preferences persist between launches.
- User-approved Android screen recording through MediaProjection.
- Foreground recording service with an ongoing notification and a Stop action.
- H.264 MP4 recordings saved locally in the app's private Movies/ProjectReplay folder.
- Data Saver, Balanced and High presets change the recording bitrate.
- Local Library lists saved MP4 files and supports playback, sharing and deletion.
- GitHub Actions builds a debug APK and publishes the project-replay-debug artifact.

## Important distinction

The current Capture button records a full screen session after Android's screen-sharing consent prompt. It does **not** yet keep a rolling 15/30/60/120-second buffer, so it cannot retroactively save the moments before recording began. The duration selector is stored as the target setting for that future replay-buffer feature. Recording is video-only in this build; microphone and game-audio capture are not enabled.

The Home and Discover feeds still contain clearly labelled sample content. Accounts, uploads, a live social feed, creator interactions, thumbnails and a clip editor are not connected yet.

## Build an APK

1. Open the repository in Android Studio.
2. Use JDK 17 and install the Android SDK for compileSdk 35.
3. Run `gradle assembleDebug`.
4. GitHub Actions also builds a debug APK on pushes to main. Download the `project-replay-debug` artifact from the successful Actions run.

## Technical direction

See [the engineering roadmap](docs/ENGINEERING_ROADMAP.md) for the segment-based MediaCodec replay buffer, storage management, compression strategy, social milestones and privacy requirements.

Screen capture requires explicit Android MediaProjection consent and a visible foreground service. Project Replay does not silently record gameplay.
