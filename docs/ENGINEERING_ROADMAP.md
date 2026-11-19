# Project Replay engineering notes

## Product direction

Project Replay is a native Android gaming capture and clips community. Capture controls should be reachable while gaming, clips should be quick to review, and uploads should be bandwidth-conscious.

## Implemented foundation

- Jetpack Compose navigation: Home, Discover, Capture, Library, Profile.
- Vice City, Christmas, Midnight and Graphite visual themes.
- Theme, replay duration and quality preferences persist locally.
- User-approved MediaProjection rolling capture runs in a foreground service.
- H.264 MP4 segments rotate into a bounded cache buffer.
- Clip That selects the latest configured replay window and assembles it into a local MP4 using MediaExtractor and MediaMuxer without re-encoding.
- Segments selected for export are pinned until assembly completes.
- Data Saver, Balanced and High presets control recording bitrate.
- Capture resolution is capped at 1920 pixels on the longest display edge.
- Recording is blocked below 500 MB available storage and an error is surfaced in the Capture UI.
- Local Library lists, plays, shares, deletes, searches, favourites and renames MP4 files.
- Android Quick Settings tile can launch the consent flow or stop an active recording.
- Feed entries remain labelled sample content.
- GitHub Actions builds a debug APK and uploads it as an artifact.

## Capture architecture

The first rolling-buffer milestone is implemented using a user-approved MediaProjection session. MediaRecorder writes short H.264 MP4 segments; the service rotates the encoder surface on a timer and retains a bounded cache. Clip That finalizes the current segment, selects enough recent segments to cover the chosen 15/30/60/120-second window, pins them against cleanup and joins their encoded video samples into a single MP4 with MediaExtractor and MediaMuxer. The export avoids a full re-encode.

This is an initial implementation, not yet a verified production-grade capture engine. Encoder restarts can create small gaps, segment boundaries and timestamps need device testing, and sustained recording can affect thermal and battery performance. It has not yet been validated across Android versions, game renderers, rotation changes or protected-content scenarios.

Implementation requirements:
1. Request MediaProjection consent from the user before starting capture.
2. Run capture in a foreground service with the required notification and service type for the target Android version.
3. Use MediaCodec for hardware-accelerated encoding where supported.
4. Write short segments to app-private cache, not an unbounded full-session file.
5. Maintain a bounded segment index and delete expired segments safely.
6. On Clip That, pin selected segments so buffer rotation cannot delete them during assembly.
7. Mux segments with MediaMuxer, handling codec configuration and timestamp continuity.
8. Handle rotation, app backgrounding, permission revocation, thermal pressure, low storage and process death.
9. Make audio capture optional and respect Android playback-capture restrictions and each source app's policy.
10. Never imply capture is active unless the foreground service confirms it.

A segment-based MediaCodec pipeline is preferred over keeping a large raw video file open. The target buffer duration is configurable (15, 30, 60 or 120 seconds). Actual memory and disk use must be measured on low-, mid- and high-tier devices before choosing segment size, resolution or bitrate.

## Local library and editing

Implemented: saved recordings are listed, opened through Android's media viewer, shared through the system Sharesheet, deleted, searched, favourited, renamed and refreshed. The library stores files locally in app-private storage.

Next:
- Generate thumbnails and duration metadata.
- Add game folders, tags and sorting.
- Add trim, crop, mute, aspect-ratio presets and export.
- Add optional overlay text and a simple clip title editor.
- Keep originals until the user confirms an export or deletion.

## Compression and export

- Current manual recording uses H.264 MP4 with selectable bitrate presets.
- Prefer hardware HEVC when the device supports it and the user chooses compatible output.
- Keep H.264 as a broad-compatibility fallback.
- Consider AV1 only when both encoder and playback ecosystem support it.
- Show estimated output size and actual final size.
- Never promise a fixed file size: content complexity, resolution, frame rate and bitrate all affect it.

## Social platform milestones

1. Local capture and local clip library.
2. Clip metadata, thumbnails, tags and game folders.
3. Account identity and secure authentication.
4. Resumable uploads, transcoding and CDN delivery.
5. Creator profiles, follows, likes, comments and reporting.
6. Feed ranking, moderation queues, blocking and privacy controls.
7. Push notifications, analytics and creator tools.

The social feed must use real server data before it is presented as a live community. Sample entries remain labelled as samples.

## Privacy and safety

- Capture is opt-in and visibly indicated.
- No background recording without an active user-authorised session.
- Provide delete/export controls for local clips and account data.
- Add report, block and moderation workflows before opening public uploads.
- Avoid recording protected content where Android or the source app disallows capture.
- Request only permissions needed for the feature being used.

## Build

Open in Android Studio with JDK 17 and Android SDK 35. GitHub Actions runs Gradle assembleDebug and publishes the APK as the project-replay-debug artifact.
