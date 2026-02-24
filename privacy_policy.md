# Privacy Policy for Video Background Remover

**Last Updated:** February 24, 2026

## Introduction

Video Background Remover ("we", "our", or "us") is committed to protecting your privacy. This Privacy Policy explains how our app handles your data.

## Key Point: We Don't Collect Your Data

**Video Background Remover operates entirely on your device. We do not collect, transmit, or store any of your personal data or video content on external servers.**

## What Data We Handle

### 1. Video Content
- **What:** Videos you select for processing
- **Where:** Processing happens entirely on your device
- **Storage:** Temporary files in app cache, deleted automatically
- **Transmission:** None - videos never leave your device

### 2. Processed Output
- **What:** PNG frames or mask videos you export
- **Where:** Saved to your chosen location (device storage)
- **Transmission:** None - you control where files go

### 3. App Settings
- **What:** User preferences (FPS, resolution, etc.)
- **Where:** Stored locally on your device using Android DataStore
- **Transmission:** None

### 4. App Logs
- **What:** Technical logs for debugging (if enabled)
- **Where:** Stored locally on your device
- **Transmission:** None (unless you manually share them)

## Permissions We Request

### Required Permissions
- **READ_MEDIA_VIDEO** (Android 13+) / **READ_EXTERNAL_STORAGE** (Android 12 and below)
  - Purpose: To let you select videos from your device
  - Data Access: Only videos you explicitly select

- **FOREGROUND_SERVICE** / **FOREGROUND_SERVICE_DATA_SYNC**
  - Purpose: To process videos in the background
  - Data Access: None - just allows background processing

- **POST_NOTIFICATIONS**
  - Purpose: Show processing progress
  - Data Access: None

### Not Required (Future Feature)
- **INTERNET**
  - Currently: App works completely offline
  - Future: May be used for cloud processing (opt-in only)

## Third-Party Services

### MediaPipe (Google)
- **What:** On-device machine learning library for segmentation
- **Data Handling:** Runs entirely offline, no data sent to Google
- **Privacy Policy:** https://developers.google.com/mediapipe/solutions/privacy

## Data Retention

- **Processing Cache:** Automatically cleared after processing or on app restart
- **Export Files:** You control where exports are saved
- **Settings:** Stored until you clear app data or uninstall
- **Logs:** Rotated automatically, max 7 days

## Your Rights

You have the right to:
- **Access:** All your data is on your device
- **Delete:** Clear app cache or uninstall to remove all data
- **Export:** Export your processed videos as you choose
- **Control:** All processing happens locally under your control

## Children's Privacy

Our app is not specifically designed for children under 13. We do not knowingly collect any data from children.

## Changes to This Policy

We may update this Privacy Policy. Changes will be posted here with an updated date.

## Contact Us

If you have questions about this Privacy Policy, please open an issue on our GitHub repository:
https://github.com/YOUR_USERNAME/video-bg-remover-android

## Summary

| Aspect | Our Practice |
|--------|--------------|
| Data Collection | None - app works offline |
| Cloud Processing | None - all on-device |
| Data Sharing | None - no third parties receive your data |
| Data Storage | Local only, you control exports |
| Internet Required | No |
| Account Required | No |

**Bottom Line:** Your videos never leave your device unless *you* choose to share them.
