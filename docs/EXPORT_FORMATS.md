# Export Formats Documentation

This document explains the export formats supported by Video Background Remover and how to use them in popular video editing software.

## Table of Contents

- [PNG Sequence (ZIP)](#png-sequence-zip)
- [Mask Video (MP4)](#mask-video-mp4)
- [Using in Editing Software](#using-in-editing-software)

---

## PNG Sequence (ZIP)

### Description
A ZIP archive containing individual PNG image files for each frame with **true alpha transparency**. This format preserves the highest quality and is ideal for editing workflows that support image sequences.

### File Structure
```
video_bg_removed_20240224_123045.zip
├── frame_00000.png  (RGBA with alpha channel)
├── frame_00001.png
├── frame_00002.png
├── ...
└── metadata.json
```

### Specifications
- **Format**: PNG (Portable Network Graphics)
- **Color Space**: RGBA (24-bit color + 8-bit alpha)
- **Resolution**: 512x512 (configurable)
- **Frame Rate**: Matches source or target FPS (default 15fps)
- **Compression**: ZIP compression (10-20% size reduction)

### Pros
- ✅ True alpha transparency
- ✅ Highest quality (lossless)
- ✅ Compatible with most professional software
- ✅ Individual frame access

### Cons
- ❌ Large file size
- ❌ Requires software with image sequence support
- ❌ Slower to import (many files)

### Best For
- DaVinci Resolve
- Adobe After Effects
- Blender
- Natron
- Any software with PNG sequence import

---

## Mask Video (MP4)

### Description
A grayscale H.264 video file where the **luminance (brightness) represents the mask**. This format is compatible with virtually all video editing software through luma key/matte workflows.

### Visual Representation

| Mask Value | Pixel Color | Meaning |
|------------|-------------|---------|
| 1.0 (100%) | White       | Fully opaque (keep pixel) |
| 0.5 (50%)  | Gray        | Semi-transparent |
| 0.0 (0%)   | Black       | Fully transparent (remove pixel) |

### Specifications
- **Container**: MP4 (MPEG-4 Part 14)
- **Video Codec**: H.264 (AVC)
- **Color Space**: Grayscale (YUV 4:2:0, neutral chroma)
- **Resolution**: Matches processing resolution (default 512x512)
- **Frame Rate**: Matches source or target FPS
- **Bitrate**: Optimized for grayscale (lower than color video)

### Pros
- ✅ Universal compatibility
- ✅ Small file size
- ✅ Fast import and playback
- ✅ Works with any video editor

### Cons
- ❌ No true alpha (requires luma key setup)
- ❌ Slight quality loss (H.264 compression)
- ❌ Requires understanding of compositing

### Best For
- Adobe Premiere Pro
- Final Cut Pro
- DaVinci Resolve
- CapCut
- Any software with luma key/matte effects

---

## Using in Editing Software

### DaVinci Resolve

#### PNG Sequence
1. Extract the ZIP file
2. In the Media Pool, right-click → **Import Media**
3. Select the first PNG file (frame_00000.png)
4. Check **"Import image sequence"** option
5. Click **Import**
6. Drag the clip to your timeline

#### Mask Video (Luma Keyer)
1. Import your original video to Track 1
2. Import the mask MP4 to Track 2
3. Add the original video to Track 3 (duplicate)
4. On Track 3, add **Luma Keyer** effect
5. In Keyer settings, set:
   - **Key Input**: Track 2 (mask video)
   - **Luma Key**: White = opaque, Black = transparent
6. Adjust **Gain** and **Clip** if needed

### Adobe After Effects

#### PNG Sequence
1. Extract the ZIP file
2. **File → Import → File**
3. Select frame_00000.png
4. Check **"PNG Sequence"** option
5. Import as footage
6. Drag to composition timeline

#### Mask Video (Luma Matte)
1. Import original video
2. Import mask MP4
3. Place original video on bottom layer
4. Place mask MP4 on layer above
5. Set mask layer to **TrkMat: Luma**
6. The original video will now use the mask's brightness as alpha

### Adobe Premiere Pro

#### PNG Sequence
1. Extract the ZIP file
2. **File → Import**
3. Select the first PNG and check "Image Sequence"
4. Import and use as regular footage

#### Mask Video (Track Matte Key)
1. Place original video on Track V1
2. Place mask MP4 on Track V2
3. Copy original video to Track V3
4. On V3 clip, add **Track Matte Key** effect
5. In Effect Controls:
   - **Matte**: Video 2 (mask)
   - **Composite Using**: Luma
   - **Reverse**: unchecked

### Final Cut Pro

#### PNG Sequence
1. Extract the ZIP file
2. In Browser, **File → Import → Files**
3. Select all PNG files or just the first one
4. Import as image sequence (if supported by plugin)

#### Mask Video (Luma Keyer)
1. Place original video on primary storyline
2. Connect mask MP4 as connected clip above
3. Apply **Luma Keyer** effect to original video
4. In Inspector, use mask clip as key source

### Blender (VSE - Video Sequence Editor)

#### PNG Sequence
1. Extract the ZIP file
2. In VSE, **Add → Image/Sequence**
3. Navigate to extracted folder
4. Select all PNG files (A key)
5. **Add Image Strip**

#### Mask Video
1. Add original video strip
2. Add mask MP4 as separate strip
3. Add **Color Key** or **Luminance Key** modifier
4. Use mask strip as factor input

---

## Troubleshooting

### PNG Sequence Issues

| Issue | Solution |
|-------|----------|
| Frames out of order | Ensure alphabetical sorting (frame_00000, not frame_0) |
| Alpha not showing | Check software supports RGBA PNG import |
| Missing frames | Verify all PNG files extracted from ZIP |
| Slow performance | Lower preview resolution or use proxy files |

### Mask Video Issues

| Issue | Solution |
|-------|----------|
| Mask not working | Ensure "Luma" (not "Alpha") is selected as matte type |
| Inverted mask | Check "Reverse" or "Invert" option in keyer settings |
| Soft edges missing | Adjust keyer tolerance/threshold settings |
| Color tint on mask | Ensure mask is truly grayscale (R=G=B) |

---

## Technical Notes

### Mask Video Encoding

The mask video uses H.264 codec with these characteristics:
- **Y (Luma) channel**: Carries the mask data (0-255)
- **U, V (Chroma) channels**: Set to neutral 128 (no color)
- **Bitrate**: Lower than standard video (grayscale needs less data)
- **GOP Structure**: I-frames every 5 seconds for scrubbing performance

### Why Grayscale Instead of Alpha?

Most video codecs (including H.264/AVC) don't support alpha channels in standard profiles. While H.265/HEVC and ProRes 4444 support alpha, they're:
- Less compatible
- Larger file sizes
- Slower to encode

The luma matte workflow is the industry standard for cross-platform compatibility.

### Converting Between Formats

If you need the mask in a different format:

**MP4 to PNG Sequence:**
```bash
ffmpeg -i mask_video.mp4 frame_%05d.png
```

**PNG to ProRes with Alpha:**
```bash
ffmpeg -i frame_%05d.png -c:v prores_ks -pix_fmt yuva444p10le mask_alpha.mov
```

---

## Support

For questions about using these formats in specific software, please refer to:
- The software's official documentation
- Community forums for that software
- This app's GitHub Issues page
