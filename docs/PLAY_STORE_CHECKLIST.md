# Google Play Store Assets Checklist

This document lists all assets required for Google Play Store submission.

## App Information

| Field | Content |
|-------|---------|
| App Name | Video Background Remover |
| Short Description | Remove video backgrounds with AI. Works offline! |
| Full Description | See `playstore_description.txt` below |
| Category | Video Players & Editors |
| Content Rating | Everyone |
| Website | (Your website or GitHub repo) |
| Email | (Your support email) |
| Privacy Policy | Link to `privacy_policy.md` |

## Text Assets

### Play Store Description (Short)
```
Remove backgrounds from your videos using on-device AI. No internet required, no watermarks, completely free!
```

### Play Store Description (Full)
```
Video Background Remover uses AI to automatically remove backgrounds from your videos. All processing happens on your device - your videos never leave your phone!

✨ KEY FEATURES

🎬 Easy Video Import
Select any video from your gallery. Supports MP4, MKV, WebM, and more.

🤖 AI-Powered Segmentation
Uses Google's MediaPipe for accurate person segmentation. No manual masking needed!

👁️ Live Preview
See results before processing with our checkerboard transparency preview.

⚙️ Background Processing
Continue using your phone while we process in the background. Get notified when done.

📤 Flexible Export
• PNG Sequence (ZIP) - True alpha transparency
• Mask Video (MP4) - Use in any editor

🔒 Privacy First
• 100% offline - no data leaves your device
• No account required
• No internet permission needed
• Open source

🎨 Pro-Quality Output
• Configurable resolution (360p to 1080p)
• Adjustable frame rate
• Temporal smoothing for consistent masks
• Edge feathering for natural results

📱 PERFECT FOR
• Content creators
• Video editors
• Social media posts
• Green screen alternative
• VFX compositing

💡 HOW TO USE
1. Select a video with a person
2. Preview the segmentation
3. Process in background
4. Export as PNG or mask video
5. Import into your favorite editor

COMPATIBLE EDITORS
• DaVinci Resolve
• Adobe After Effects
• Adobe Premiere Pro
• Final Cut Pro
• Blender
• CapCut
• And more!

⚡ PERFORMANCE
• Optimized for mobile devices
• Works offline
• Fast processing with GPU acceleration
• Low battery usage

🆓 FREE & OPEN SOURCE
No subscriptions, no watermarks, no limits. Completely free!

Note: Best results with good lighting and clear subject separation.

Learn more: https://github.com/YOUR_USERNAME/video-bg-remover-android
```

## Graphic Assets

### App Icon
- **File**: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- **Size**: 512x512px (play store), 192x192px (xxxhdpi)
- **Format**: PNG
- **Shape**: Adaptive icon (foreground + background layers)
- **Status**: ⚠️ Need to create

### Feature Graphic (Required)
- **File**: `docs/assets/feature_graphic.png`
- **Size**: 1024x500px
- **Format**: PNG or JPEG
- **Content**: App name, key feature text, background image
- **Status**: ⚠️ Need to create

### Phone Screenshots (Required: 2-8)
| # | Screen | Content |
|---|--------|---------|
| 1 | Import | Video selection screen |
| 2 | Preview | Segmentation preview with checkerboard |
| 3 | Processing | Progress indicator |
| 4 | Export | Format selection |
| 5 | Settings | Settings screen |

- **Size**: 1080x1920px (or 1920x1080px for landscape)
- **Format**: PNG or JPEG
- **Status**: ⚠️ Need to capture

### 7-inch Tablet Screenshots (Optional)
- Same screens as phone
- **Size**: 1080x1920px or 1920x1080px
- **Status**: ⚠️ Optional

### 10-inch Tablet Screenshots (Optional)
- Same screens as phone
- **Size**: 1920x1080px or 2560x1600px
- **Status**: ⚠️ Optional

### Promo Video (Optional)
- **File**: `docs/assets/promo_video.mp4`
- **Length**: 30-60 seconds
- **Format**: MP4
- **Content**: App demo, features, export examples
- **Status**: ⚠️ Optional

## Store Listing Assets

### App Icon (Store Listing)
- **Size**: 512x512px
- **Format**: PNG-32
- **Background**: Transparent or branded color
- **Status**: ⚠️ Need to create

## Screenshots Checklist

### Required Screens
- [ ] Import Screen - Show video picker
- [ ] Preview Screen - Show checkerboard preview with segmentation
- [ ] Processing Screen - Show progress
- [ ] Export Screen - Show format options
- [ ] Settings Screen - Show configuration options

### Screenshot Guidelines
- Clean device (no notifications)
- Same device model across all shots
- High quality, no compression artifacts
- Show actual app content (not mockups)

## Asset Creation Tasks

### Graphics Designer Needed
1. [ ] Design app icon (adaptive icon)
2. [ ] Design feature graphic (1024x500)
3. [ ] Create screenshot frames (optional)
4. [ ] Design promo video storyboard (optional)

### Development Tasks
1. [ ] Capture screenshots on clean device
2. [ ] Record promo video (optional)
3. [ ] Write final Play Store description
4. [ ] Set up Play Store listing

## Pre-Launch Checklist

### Code
- [ ] Version bumped in `build.gradle.kts`
- [ ] Version name updated
- [ ] Release build tested
- [ ] ProGuard/R8 working

### Assets
- [ ] All screenshots captured
- [ ] Feature graphic created
- [ ] App icon exported
- [ ] Promo video created (optional)

### Store Listing
- [ ] App name finalized
- [ ] Descriptions written
- [ ] Privacy policy linked
- [ ] Contact email set
- [ ] Content rating questionnaire completed

### Testing
- [ ] Tested on multiple devices
- [ ] Tested on different Android versions
- [ ] Memory profiling done
- [ ] No crashes in release build

### Legal
- [ ] Privacy policy published
- [ ] License file included
- [ ] Open source attributions added
- [ ] Terms of Service (if needed)

## Post-Launch

### Monitoring
- [ ] Crash reporting enabled
- [ ] Analytics set up (optional)
- [ ] Review monitoring

### Updates
- [ ] Respond to user reviews
- [ ] Monitor crash reports
- [ ] Plan first update

## Resources

### Design Tools
- **Adaptive Icon**: Android Studio Asset Studio
- **Feature Graphic**: Figma, Sketch, or Canva
- **Screenshots**: Android Studio Emulator or physical device

### Templates
- Feature Graphic: Use Play Store's recommended safe zone (text within center 720x400)
- Screenshots: Include device frame for polished look (optional)

---

**Note**: Replace `YOUR_USERNAME` with actual GitHub username before publishing.
