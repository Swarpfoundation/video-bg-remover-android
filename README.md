# Video Background Remover

[![CI](https://github.com/YOUR_USERNAME/video-bg-remover-android/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/video-bg-remover-android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com/)

A production-ready Android app for removing backgrounds from videos. Runs entirely on-device using MediaPipe's selfie segmentation model.

<p align="center">
  <img src="docs/screenshots/import_screen.png" width="200" />
  <img src="docs/screenshots/preview_screen.png" width="200" />
  <img src="docs/screenshots/processing_screen.png" width="200" />
  <img src="docs/screenshots/export_screen.png" width="200" />
</p>

## Features

🎬 **Video Import**
- Select videos via Storage Access Framework (SAF)
- Supports MP4, MKV, WebM, and more
- Automatic metadata extraction

🤖 **AI Segmentation**
- On-device ML using MediaPipe Tasks Vision
- Person/human segmentation
- Temporal smoothing for consistent results
- Configurable post-processing (threshold, morphology, feather)

👁️ **Live Preview**
- Checkerboard background shows transparency
- Toggle between Original/Mask/Composited views
- Real-time mask preview

⚙️ **Background Processing**
- WorkManager with foreground service
- Progress notifications with cancel option
- Survives configuration changes
- Batch frame processing

📤 **Export Options**
- **PNG Sequence (ZIP)**: True alpha transparency
- **Mask Video (MP4)**: Grayscale luma matte for compositing
- Share directly to any app
- Save to device or cloud storage

⚡ **Performance**
- Optimized for mobile devices
- Configurable processing resolution (360p to 1080p)
- Adjustable frame rate (10-30 FPS)
- Memory-efficient frame pooling

🔒 **Privacy**
- **100% offline** - no data leaves your device
- No account required
- No internet permission needed
- Process videos locally

## Download

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=com.videobgremover.app)

Or download the latest APK from [GitHub Releases](../../releases).

## Requirements

- Android 8.0+ (API 26)
- 2GB RAM minimum
- 200MB free storage

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  ┌─────────┐ ┌─────────┐ ┌───────────┐ ┌─────────┐        │
│  │ Import  │ │ Preview │ │ Processing│ │ Export  │        │
│  │ Screen  │ │ Screen  │ │ Screen    │ │ Screen  │        │
│  └────┬────┘ └────┬────┘ └─────┬─────┘ └────┬────┘        │
└───────┼───────────┼────────────┼────────────┼──────────────┘
        │           │            │            │
┌───────┼───────────┼────────────┼────────────┼──────────────┐
│       │      Domain Layer      │            │              │
│  ┌────┴───────────┴────────────┴────────────┴────┐        │
│  │           Use Cases (ViewModels)               │        │
│  │  ImportVideo │ Segment │ Process │ Export      │        │
│  └────┬───────────┬────────────┬────────────┬─────┘        │
└───────┼───────────┼────────────┼────────────┼──────────────┘
        │           │            │            │
┌───────┼───────────┼────────────┼────────────┼──────────────┐
│       │         Data Layer      │            │              │
│  ┌────┴────┐ ┌─────┴────┐ ┌─────┴────┐ ┌────┴────┐        │
│  │ Video   │ │Segment   │ │ Video    │ │ Export  │        │
│  │ Extract │ │ Repository│ │ Worker   │ │ Repository│       │
│  └─────────┘ └──────────┘ └──────────┘ └─────────┘        │
└─────────────────────────────────────────────────────────────┘
```

## Tech Stack

- **Language**: Kotlin 2.0.20
- **UI**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture
- **Background**: WorkManager + Coroutines
- **ML**: MediaPipe Tasks Vision
- **Storage**: DataStore (Preferences)
- **DI**: Manual (Koin/Hilt optional for scaling)

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK API 34+

### Clone and Build

```bash
git clone https://github.com/YOUR_USERNAME/video-bg-remover-android.git
cd video-bg-remover-android

# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew test

# Run lint checks
./gradlew lint ktlintCheck detekt
```

### IDE Setup

1. Open in Android Studio
2. Sync project with Gradle files
3. Run on device or emulator (API 26+)

## Project Structure

```
app/src/main/java/com/videobgremover/app/
├── core/               # Utilities, logging, extensions
│   ├── notification/
│   ├── permission/
│   └── processing/
├── data/               # Data layer (IO, DB, Network)
│   ├── encoder/        # MediaCodec encoders
│   ├── exporter/       # Export implementations
│   ├── extractor/      # Video metadata extraction
│   ├── preferences/    # DataStore settings
│   ├── repository/     # Repository implementations
│   └── worker/         # WorkManager workers
├── domain/             # Domain layer (business logic)
│   ├── model/          # Data classes
│   └── repository/     # Repository interfaces
└── ui/                 # UI layer (Compose)
    ├── components/     # Reusable UI components
    ├── contract/       # ActivityResultContracts
    ├── navigation/     # Navigation graph
    ├── screens/        # Screen composables
    ├── theme/          # Colors, typography
    └── viewmodel/      # ViewModels
```

## Export Formats

### PNG Sequence (ZIP)
- Each frame as PNG with alpha channel
- Best quality, lossless
- Ideal for: DaVinci Resolve, After Effects, Blender

### Mask Video (MP4)
- Grayscale H.264 video
- White = opaque, Black = transparent
- Smaller file size
- Universal compatibility
- Ideal for: Premiere Pro, Final Cut Pro, mobile editors

See [docs/EXPORT_FORMATS.md](docs/EXPORT_FORMATS.md) for detailed usage instructions.

## Settings

Configure processing behavior in Settings:

| Setting | Options | Default |
|---------|---------|---------|
| Frame Rate | 10-30 FPS | 15 FPS |
| Max Duration | 15-120 sec | 60 sec |
| Resolution | 360p-1080p | 512p |
| Confidence Threshold | 30-70% | 50% |
| Temporal Smoothing | On/Off | On |
| Feather Edges | On/Off | On |
| Default Format | PNG/MP4 | PNG |

## Using in Editing Software

### DaVinci Resolve
1. Import PNG sequence as image sequence
2. Or use mask video with Luma Keyer

### Adobe After Effects
1. Import PNG sequence
2. Or use mask video as Luma Track Matte

### Adobe Premiere Pro
1. Use Track Matte Key effect with mask video

See [docs/EXPORT_FORMATS.md](docs/EXPORT_FORMATS.md) for detailed instructions.

## Performance Tips

1. **Lower resolution** for faster processing
2. **Reduce frame rate** for smaller files
3. **Disable smoothing** if masks look good without it
4. **Close other apps** to free up RAM

## Limitations (MVP)

- Maximum duration: 60 seconds (configurable in settings)
- Processing resolution: Up to 1080p
- Person/human segmentation only
- Requires Android 8.0+

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) for upcoming features:

- [ ] MediaCodec frame extraction (faster)
- [ ] GPU acceleration
- [ ] General object segmentation
- [ ] Cloud processing option
- [ ] Background replacement

## Contributing

We welcome contributions! Please read our [Contributing Guidelines](CONTRIBUTING.md) first.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

This project uses:
- **ktlint** for code formatting
- **detekt** for static analysis

Run checks before committing:
```bash
./gradlew ktlintCheck detekt lint
```

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- [MediaPipe](https://developers.google.com/mediapipe) by Google
- [Jetpack Compose](https://developer.android.com/jetpack/compose) by Android Team
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for background processing

## Support

- 📧 Open an [issue](https://github.com/YOUR_USERNAME/video-bg-remover-android/issues)
- 📖 Read the [docs](docs/)
- 🌟 Star this repo if you find it useful!

---

<p align="center">Made with ❤️ for video creators</p>
