# Video Background Remover

[![CI](https://github.com/YOUR_USERNAME/video-bg-remover-android/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/video-bg-remover-android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A production-ready Android app for removing backgrounds from videos. Runs fully on-device using MediaPipe's selfie segmentation model.

## Features

- **Video Import**: Select videos via Storage Access Framework (SAF)
- **AI Segmentation**: On-device ML using MediaPipe Tasks Vision
- **Transparency Export**: 
  - PNG sequence with alpha (ZIP)
  - Grayscale mask video (MP4) for compositing
- **Live Preview**: Checkered background shows transparency
- **Background Processing**: WorkManager with progress notifications

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ ImportScreen│  │PreviewScreen│  │ ProcessingScreen    │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ImportVideo  │  │SegmentFrame │  │ ExportVideo         │ │
│  │UseCase      │  │UseCase      │  │ UseCase             │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │VideoMetadata│  │Segmentation │  │ VideoExporter       │ │
│  │Extractor    │  │Repository   │  │ (PNG/ZIP/MP4)       │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│               Framework (WorkManager)                       │
│           VideoProcessingWorker (Foreground)                │
└─────────────────────────────────────────────────────────────┘
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM with Clean Architecture
- **Background Processing**: WorkManager + Coroutines
- **ML**: MediaPipe Tasks Vision (Selfie Segmentation)
- **Navigation**: Jetpack Navigation Compose

## Requirements

- Android 8.0+ (API 26)
- Camera permission (for future camera input feature)
- Storage permission (for video import/export)

## Building

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/video-bg-remover-android.git
cd video-bg-remover-android

# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew test

# Run lint checks
./gradlew lint

# Run code quality checks
./gradlew ktlintCheck detekt
```

## Project Structure

```
app/src/main/java/com/videobgremover/app/
├── core/           # Utilities, logging, extensions
├── data/           # Video I/O, segmentation, export implementations
├── domain/         # Use cases, repository interfaces, models
└── ui/             # Compose screens, ViewModels, theme
```

## Development

### Code Style

This project uses:
- **ktlint** for code formatting
- **detekt** for static analysis

Run checks before committing:
```bash
./gradlew ktlintCheck detekt lint
```

### Pre-commit Hook (Optional)

```bash
#!/bin/sh
# .git/hooks/pre-commit
./gradlew ktlintCheck detekt
```

## Export Formats

### PNG ZIP Export
- Each frame saved as PNG with alpha channel
- True transparency for direct use in editors
- Includes metadata JSON with processing settings

### Mask MP4 Export  
- Grayscale H.264 video
- White = opaque, Black = transparent
- Use as luma matte in:
  - DaVinci Resolve
  - Adobe After Effects
  - Premiere Pro
  - Final Cut Pro

## Roadmap

- [x] Project bootstrap & CI setup
- [ ] Video import with metadata
- [ ] Single-frame segmentation preview
- [ ] Background processing pipeline
- [ ] PNG ZIP export
- [ ] Mask MP4 export
- [ ] Performance optimizations
- [ ] Settings/preferences
- [ ] Google Play release

## Limitations (MVP)

- Maximum video duration: 60 seconds (configurable)
- Processing resolution: Up to 1080p
- Output frame rate: Up to 30fps
- Person/human segmentation only

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- MediaPipe by Google
- Jetpack Compose by Android Team
