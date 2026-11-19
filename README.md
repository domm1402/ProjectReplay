# Project Replay

**A mobile-first gaming capture and clips community for Android.**

Project Replay is being built as a native Kotlin + Jetpack Compose app. The product goal is simple: press **Clip That** and keep the gameplay moment that just happened, then edit it, save it or share it with the community.

## What's in the repository now

- Native Android application structure (Kotlin, Jetpack Compose, Material 3).
- Five-tab mobile shell: Home, Discover, Capture, Library and Profile.
- Four visual themes: Vice City (yellow/purple), Christmas (red/green), Midnight and Graphite.
- Theme selection persists between launches.
- Replay duration preferences: 15, 30, 60 and 120 seconds.
- Quality presets: Data Saver, Balanced and High.
- Sample feed cards are visibly marked as samples.
- GitHub Actions workflow for building a debug APK.

## Current status

This is an early app foundation. The UI and local preference controls are implemented. The rolling gameplay capture engine, real clip library, editing pipeline, accounts, uploads and live social feed are not implemented yet. The Capture screen says so rather than pretending to record.

## Build an APK

1. Open the repository in Android Studio.
2. Use JDK 17 and install the Android SDK for compileSdk 35.
3. Run the app configuration, or run `gradle assembleDebug`.
4. GitHub Actions also builds a debug APK on pushes to main. Download the `project-replay-debug` artifact from the successful Actions run.

## Technical direction

See [the engineering roadmap](docs/ENGINEERING_ROADMAP.md) for the proposed segment-based MediaCodec replay buffer, storage management, compression strategy, social milestones and privacy requirements.

Screen capture requires explicit Android MediaProjection consent and a visible foreground service. Project Replay will never silently record gameplay.
