# Roadmap

This document outlines the planned features and improvements for Video Background Remover.

## Current Status: MVP Complete ✅

The MVP (Minimum Viable Product) is complete with the following features:

- ✅ Video import via Storage Access Framework
- ✅ Person/human segmentation using MediaPipe
- ✅ Real-time preview with checkerboard transparency
- ✅ Background processing with WorkManager
- ✅ PNG sequence export (ZIP with alpha)
- ✅ Mask video export (grayscale MP4)
- ✅ Share intent support
- ✅ Settings/preferences

---

## Version 1.1 (Planned)

### Performance Improvements
- [ ] MediaCodec-based frame extraction (faster than MediaMetadataRetriever)
- [ ] GPU acceleration for segmentation (OpenCL/Vulkan delegates)
- [ ] Multi-threaded frame processing
- [ ] Frame pooling to reduce GC pressure

### Enhanced Segmentation
- [ ] General object segmentation (not just people)
- [ ] Hair detail refinement
- [ ] Edge-aware smoothing
- [ ] Chroma key fallback for green screen videos

### Export Improvements
- [ ] Direct GIF export
- [ ] WebM with alpha support
- [ ] Adjustable output resolution
- [ ] Quality presets (Fast/Balanced/Quality)

---

## Version 1.2 (Planned)

### Cloud Processing (Optional)
- [ ] Opt-in cloud processing for higher quality
- [ ] Desktop-grade models (PyTorch/TensorFlow)
- [ ] 4K processing support
- [ ] Automatic device ↔ cloud selection

### Pro Features (In-App Purchase)
- [ ] Unlimited video duration
- [ ] Batch processing
- [ ] Custom background replacement
- [ ] Advanced keyframe editing
- [ ] Priority cloud processing

### Social Features
- [ ] Share to TikTok/Instagram directly
- [ ] Templates and presets
- [ ] Community backgrounds library

---

## Version 2.0 (Future)

### AI Improvements
- [ ] Real-time preview while recording
- [ ] Subject tracking across frames
- [ ] Automatic edge refinement
- [ ] Depth-aware segmentation

### Platform Expansion
- [ ] iOS version
- [ ] Desktop app (Windows/Mac)
- [ ] Web version (limited processing)

### Ecosystem
- [ ] Plugin SDK for third-party editors
- [ ] API for developers
- [ ] Batch automation tools

---

## Technical Debt & Improvements

### Architecture
- [ ] Migrate to multi-module structure
- [ ] Add comprehensive integration tests
- [ ] Implement CI/CD for releases
- [ ] Add crash reporting (opt-in)

### Code Quality
- [ ] 100% unit test coverage for core logic
- [ ] UI tests with Espresso
- [ ] Performance benchmarks
- [ ] Memory leak detection in CI

### Documentation
- [ ] Video tutorials
- [ ] API documentation
- [ ] Contribution guidelines
- [ ] Architecture decision records (ADRs)

---

## Community Requests

Features requested by the community (vote with 👍 on issues):

1. **Trim/Cut video before processing** - Most requested
2. **Adjust segmentation boundaries manually**
3. **Blur background instead of removing**
4. **Replace background with image/video**
5. **Support for pets/animals**
6. **Real-time camera preview**

---

## How to Contribute

We welcome contributions! See areas marked with `[Help Wanted]` or check the GitHub Issues:

1. Pick an issue from the roadmap
2. Comment on the issue to claim it
3. Fork and create a feature branch
4. Submit a pull request

---

## Release Schedule

| Version | Target Date | Status |
|---------|-------------|--------|
| 1.0.0 | Feb 2026 | ✅ Released |
| 1.1.0 | Mar 2026 | 🚧 In Progress |
| 1.2.0 | Q2 2026 | 📋 Planned |
| 2.0.0 | Q4 2026 | 💡 Concept |

---

*This roadmap is subject to change based on user feedback and technical constraints.*
