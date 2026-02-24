# Upgrade Plan: From MVP to #1 Video Background Remover

This document outlines the implementation plan to evolve the MVP into a best-in-class video background removal tool.

---

## Executive Summary

**Current State (v1.0 MVP):**
- Basic person segmentation using MediaPipe
- Hard masks with simple post-processing
- Fixed 512p resolution, 15 FPS
- PNG ZIP and MP4 Mask exports

**Target State (v2.0):**
- AI matting with soft alpha edges
- Motion-aware temporal stability
- Adaptive resolution/FPS with GPU acceleration
- Smart UX with instant preview and one-tap fixes
- Professional-quality output rivaling desktop tools

---

## Phase 1: Foundation (v1.1) - Quality & Stability

**Goal:** Fix the biggest quality issues (flicker, hard edges)

### 1.1 Advanced Temporal Smoothing
**Current:** Simple EMA (`MaskProcessor` has basic temporal smoothing)  
**Upgrade:** Motion-aware temporal filtering

```kotlin
// New: MotionCompensatedTemporalFilter
class MotionCompensatedTemporalFilter {
    // 1. Store previous mask AND previous frame
    // 2. Use optical flow to warp previous mask to current frame
    // 3. Blend warped mask with current mask based on motion confidence
    // 4. Fall back to standard EMA when motion is too large
}
```

**Impact:** 80% reduction in edge shimmer during camera movement

**Implementation:**
- Add OpenCV or custom motion estimation
- 2-3 days engineering

### 1.2 Edge-Aware De-Contamination (De-Spill)
**New Feature:** Remove color spill from background

```kotlin
// New: EdgeDecontamination
class EdgeDecontamination {
    // 1. Detect edge band (alpha 0.1 - 0.9)
    // 2. Sample background color from outside edge
    // 3. Reduce that chroma contribution in edge pixels
    // 4. Preserve luminance
}
```

**Impact:** Eliminates green/red halos around subjects

**Implementation:**
- Post-processing step after segmentation
- 1-2 days engineering

### 1.3 Enhanced Edge Refinement UI
**Current:** Basic threshold/morphology/feather sliders in Settings  
**Upgrade:** Smart 3-slider system with visual feedback

```kotlin
// In MaskProcessor, upgrade to:
data class EdgeRefinementConfig(
    val softness: Float,        // Feather radius (0-10px)
    val detail: Float,          // Preserve hair vs smooth (0-1)
    val holeFill: Float         // Close small gaps (0-1)
)
```

**Impact:** Users can fix 80% of edge issues intuitively

**Implementation:**
- Update Settings screen
- Add real-time preview of edge settings
- 2-3 days engineering

---

## Phase 2: Core AI Upgrade (v1.2) - From Segmentation to Matting

**Goal:** Soft alpha edges that look professional

### 2.1 Upgrade to Video Matting Model
**Current:** MediaPipe person segmentation (hard mask)  
**Upgrade:** Robust Video Matting (RVM) or similar

**Options:**
1. **RVM (Robust Video Matting)** - Best quality, requires PyTorch Mobile
2. **MODNet** - Good balance, TFLite compatible
3. **MediaPipe Selfie Segmentation v2** - Easiest upgrade, some softness

**Recommendation:** Start with MediaPipe v2 (quick win), then migrate to RVM

```kotlin
// New: VideoMattingRepository
class VideoMattingRepository {
    // Uses model that outputs:
    // - Foreground RGB (pre-multiplied)
    // - Alpha matte (soft edges)
    // - Or: pha (alpha), fgr (foreground), er (error map)
}
```

**Impact:** Hair and motion blur look natural, not "sticker-like"

**Implementation:**
- 1-2 weeks for MediaPipe v2
- 3-4 weeks for full RVM integration

### 2.2 Subject Tracking & Selection
**New Feature:** Lock onto specific person, handle multi-person

```kotlin
// New: SubjectTracker
class SubjectTracker {
    // 1. Face detection to identify subjects
    // 2. Pose tracking to maintain ID across frames
    // 3. Distance + size heuristics for primary subject
    // 4. Tap-to-select UI in PreviewScreen
}
```

**Impact:** "It cut the wrong person" → solved

**Implementation:**
- Add Face Detection to pipeline
- 1 week engineering

---

## Phase 3: Performance Revolution (v1.3) - Speed & Scale

**Goal:** 3-10x faster processing, feels instant

### 3.1 Adaptive Resolution Pipeline
**Current:** Fixed 512p  
**Upgrade:** Smart resolution selection

```kotlin
// New: AdaptiveResolutionController
class AdaptiveResolutionController {
    fun calculateOptimalResolution(
        inputResolution: Size,
        videoDuration: Long,
        devicePerformance: DeviceClass
    ): ProcessingSpec {
        // Fast: 480p, 15 FPS, skip frames if >60s
        // Balanced: 720p, 24 FPS
        // Best: 1080p, 30 FPS, all frames
    }
}
```

**Impact:** Users get results in seconds, not minutes

**Implementation:**
- Add device capability detection
- Update VideoProcessingWorker
- 3-4 days engineering

### 3.2 GPU Acceleration
**Current:** CPU-based TFLite inference  
**Upgrade:** GPU delegate

```kotlin
// In SegmentationRepository:
val options = ObjectDetector.ObjectDetectorOptions.builder()
    .setBaseOptions(
        BaseOptions.builder()
            .setDelegate(Delegate.GPU) // <-- Add this
            .build()
    )
    .build()
```

**Impact:** 2-5x faster inference, less battery drain

**Implementation:**
- 1-2 days engineering
- Requires GPU compatibility testing

### 3.3 Frame Interpolation for Long Videos
**New Feature:** Process every Nth frame, interpolate masks

```kotlin
// New: MaskFrameInterpolator
class MaskFrameInterpolator {
    // 1. Process frames at 5 FPS instead of 30
    // 2. Use optical flow to interpolate masks between processed frames
    // 3. Maintain temporal coherence
}
```

**Impact:** 6x faster on long videos with minimal quality loss

**Implementation:**
- 3-4 days engineering

### 3.4 Predictive ETA System
**New Feature:** Accurate progress estimation

```kotlin
// In VideoProcessingWorker:
suspend fun estimateTotalTime(
    frameCount: Int,
    resolution: Size
): Long {
    // Sample first 10 frames
    // Calculate per-frame time
    // Extrapolate with 20% buffer
}
```

**Impact:** Users trust the app, don't force-quit

**Implementation:**
- 1 day engineering

---

## Phase 4: Seamless UX (v1.4) - Delight & Simplicity

**Goal:** Zero friction, pro results with minimal effort

### 4.1 Smart Preview with Problem Detection
**Upgrade:** Enhanced PreviewScreen with diagnostics

```kotlin
// New: VideoQualityAnalyzer
class VideoQualityAnalyzer {
    fun analyze(videoUri: Uri): List<QualityIssue> {
        // Detect:
        // - Low light (avg luminance < 30)
        // - High motion blur (edge variance)
        // - Busy background (texture complexity)
        // - Subject too small (relative size)
        // - Low contrast between subject/background
    }
}
```

**UI:** Show warning chips: "⚠️ Low Light - Results may vary"

**Impact:** Sets correct expectations, reduces bad reviews

**Implementation:**
- 2-3 days engineering

### 4.2 One-Tap "Fix Flicker"
**New Feature:** Automatic stability optimization

```kotlin
// In PreviewViewModel:
fun applyAntiFlickerPreset() {
    // Increase temporal alpha to 0.5
    // Increase morphology radius
    // Slightly increase feather
    // Re-process preview frame
}
```

**UI:** Floating action button in Preview: "✨ Fix Flicker"

**Impact:** Users self-serve 80% of stability issues

**Implementation:**
- 1 day engineering

### 4.3 Workflow-Based Export
**Current:** Format selection (PNG/MP4)  
**Upgrade:** Intent-based export presets

```kotlin
enum class ExportWorkflow {
    SOCIAL_MEDIA,      // MP4 color + alpha (WebM if supported)
    PRO_EDITING,       // PNG sequence + MP4 mask
    QUICK_SHARE,       // MP4 with baked background removal
    ARCHIVE            // Highest quality PNG
}
```

**UI:** Big buttons: "📱 Share to TikTok", "🎬 Edit in Premiere", "💾 Save Max Quality"

**Impact:** Zero confusion about export formats

**Implementation:**
- 2-3 days UI work
- Add export workflow presets

### 4.4 Contextual Editor Guides
**New Feature:** "How to use in [Editor]" one-pager

**Implementation:**
- Add "?" button in Export screen
- Show platform-specific instructions
- Deep links to open editor apps if installed

---

## Phase 5: Differentiation (v2.0) - Market Leadership

**Goal:** Features no competitor has

### 5.1 Subject Mode Presets
**New Feature:** Optimized processing profiles

```kotlin
enum class SubjectMode {
    SELFIE,      // Optimized for face/hair, tighter crop
    FULL_BODY,   // More stability, fill holes
    PET,         // Future: Cloud model
    OBJECT       // Future: General segmentation
}
```

**UI:** Icons in Import screen: "👤 Selfie", "🚶 Full Body"

**Impact:** Better results with zero user tuning

### 5.2 Background Replacement (Chroma Key Alternative)
**New Feature:** Replace background in-app

```kotlin
// New: BackgroundReplacer
class BackgroundReplacer {
    // 1. Accept background image/video
    // 2. Composite subject over background
    // 3. Match lighting/color grading
    // 4. Export final video
}
```

**UI:** "+ Add Background" button in Preview

**Impact:** Complete green screen replacement without desktop software

### 5.3 Batch Processing
**New Feature:** Queue multiple videos

```kotlin
// New: BatchProcessingWorker
class BatchProcessingWorker {
    // 1. Accept list of video URIs
    // 2. Process sequentially
    // 3. Unified notification with overall progress
    // 4. Export all to folder
}
```

**Impact:** Power user feature, content creator essential

### 5.4 Live Camera Preview (v2.1)
**Future:** Real-time background removal

- Requires efficient model + GPU
- Preview stream at 15-20 FPS
- Record with background removal

---

## Implementation Priority Matrix

| Feature | Impact | Effort | Priority |
|---------|--------|--------|----------|
| GPU Acceleration | High | Low | **P0** |
| Smart Preview (problem detection) | High | Low | **P0** |
| Adaptive Resolution | High | Low | **P0** |
| One-tap Fix Flicker | High | Low | **P0** |
| Enhanced Temporal Smoothing | High | Medium | **P1** |
| Edge De-Contamination | High | Low | **P1** |
| Workflow-based Export | High | Medium | **P1** |
| MediaPipe v2 / Matting | Very High | High | **P2** |
| Subject Tracking | Medium | Medium | **P2** |
| Frame Interpolation | High | Medium | **P2** |
| Batch Processing | Medium | Medium | **P3** |
| Background Replacement | Very High | High | **P3** |
| Live Camera | High | Very High | **P4** |

---

## Technical Architecture Changes

### New Modules Needed

```
app/src/main/java/com/videobgremover/app/
├── core/
│   ├── motion/           # Optical flow, motion estimation
│   ├── quality/          # Video quality analysis
│   └── gpu/              # GPU acceleration utilities
├── data/
│   ├── matting/          # Video matting models (RVM)
│   ├── interpolation/    # Frame interpolation
│   └── tracking/         # Subject tracking
```

### New Dependencies

```groovy
// GPU Acceleration
debugImplementation 'org.tensorflow:tensorflow-lite-gpu:2.14.0'

// Motion Estimation (choose one)
implementation 'org.opencv:opencv-android:4.8.0'
// OR custom lightweight implementation

// Video Matting (RVM)
implementation 'com.pytorch:pytorch_android_lite:2.1.0'
implementation 'com.pytorch:pytorch_android_torchvision:2.1.0'

// Face Detection (for subject tracking)
implementation 'com.google.mlkit:face-detection:16.1.5'
```

---

## Success Metrics

| Metric | Current (v1.0) | Target (v2.0) |
|--------|----------------|---------------|
| Processing time (60s video) | 3-5 min | < 30 sec |
| Edge quality score | 6/10 | 9/10 |
| Temporal stability (flicker) | Visible | Minimal |
| User completion rate | 60% | 90% |
| Export confusion | High | None |
| App store rating | 4.0 | 4.8+ |

---

## Next Steps

1. **Immediate (This Week):**
   - Implement GPU acceleration (P0)
   - Add smart preview with problem detection (P0)
   - Deploy as v1.1-beta

2. **Short Term (Next 2 Weeks):**
   - Adaptive resolution pipeline
   - Enhanced temporal smoothing
   - One-tap fix flicker
   - Deploy as v1.2

3. **Medium Term (Next Month):**
   - MediaPipe v2 upgrade
   - Edge de-contamination
   - Workflow-based export
   - Deploy as v1.5

4. **Long Term (Next Quarter):**
   - Full matting model (RVM)
   - Background replacement
   - Batch processing
   - Deploy as v2.0

---

*This plan prioritizes user-visible impact while building toward long-term technical leadership.*
