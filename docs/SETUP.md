# Development Setup

## Prerequisites

- JDK 17 or later
- Android SDK API 34+ with Build Tools 34.0.0+
- Android device or emulator (API 26+)

## Local Development

### Option 1: Android Studio

1. Install [Android Studio Hedgehog (2023.1.1) or later](https://developer.android.com/studio)
2. Clone this repository
3. Open in Android Studio
4. Sync project with Gradle files
5. Run on device or emulator

### Option 2: Command Line

1. Install Android SDK Command Line Tools
2. Set environment variables:
   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
   ```
3. Build:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## VS Code / Codespaces

This repository includes devcontainer configuration for consistent development environment:

1. Open in [GitHub Codespaces](https://codespaces.new) or VS Code with Dev Containers extension
2. The container will automatically install Android SDK
3. Build and test via terminal or VS Code tasks

## GitHub Actions

CI/CD is configured to run on every push and PR:
- Lint (ktlint + detekt + Android Lint)
- Unit tests
- Debug APK build

See [.github/workflows/ci.yml](../.github/workflows/ci.yml)

## Troubleshooting

### Gradle wrapper not found

```bash
gradle wrapper --gradle-version 8.7
```

### Android SDK not found

Set `local.properties`:
```properties
sdk.dir=/path/to/android/sdk
```

### Build cache issues

```bash
./gradlew clean
rm -rf ~/.gradle/caches/
```
