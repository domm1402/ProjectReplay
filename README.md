# Project Replay

A mobile-first gaming capture and clips community for Android.

## Current milestone

This repository starts with a native Android app foundation and a polished Compose interface. The first UI milestone includes a five-tab navigation shell, clip-feed presentation, capture setup screen, library, profile, and selectable seasonal themes (Vice City-inspired yellow/purple, Christmas, and Midnight).

The feed currently uses clearly labelled sample content. Real rolling gameplay capture, upload infrastructure, accounts, and social interactions are subsequent engineering milestones; they are not represented as working features yet.

## Build

Open this repository in Android Studio and run the `app` configuration. JDK 17 is required.

## Android capture notes

Capturing another app's screen requires the user's explicit Android MediaProjection consent. A replay buffer also needs a foreground service and careful storage/encoder management. Project Replay will request capture access only when the user starts capture, and will not silently record.
