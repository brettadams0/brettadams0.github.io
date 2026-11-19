# Authentic software cover provenance

All covers below are derived from real product outputs, not AI illustrations.

## Signal Lab

- Screenshot: `assets/img/signal-lab-overview.webp`, captured from the production-built Session 7 application and included in the project case study.
- `assets/img/signal-lab-800.webp`: resized directly from that screenshot with Pillow/Lanczos and WebP compression.
- The 1600px version is the existing original screenshot.
- No generated UI or simulated screenshot composition.

## Sift

- Original photograph: SpaceX DSCOVR Falcon 9 launch image, via the [scikit-image rocket sample](https://github.com/scikit-image/scikit-image/blob/v0.25.2/skimage/data/rocket.jpg). Review source attribution and rights at that link.
- Processed with the genuine production `brettadams0/sift` `core/imaging` `Pipeline.process` using `GradeProfile.SCENE`, `ExportPreset.MASTER`, upscale OFF and deterministic dither.
- Result: **passed quality gates, no fallback**.
- Mean absolute RGB channel change of the decoded Sift output relative to input: **39.8469/255**.
- Original RGB image evidence SHA-256: `183c68215c578bb5a4a3f59374269453fe1da0e10178948352489d3cf58039f9`.
- Actual Sift exported JPEG SHA-256: `c98c767f156cb8715aed2333f907f4119c8a4f5754b78c714265997d42d4b83f`.
- The portfolio cover shows precisely aligned left-half original pixels and right-half Sift exported JPEG pixels. Both sides were identically cropped/resized, with comparison labels and a center divider. No generative enhancement, Photoshop-style grading or arbitrary pixel alteration was applied to either half.
- [Public Sift source, before/after, and reproduction manifest](https://github.com/brettadams0/sift/tree/handoff/sift-graded-cover).

Portfolio files: `assets/img/sift-grade-800.webp` and `assets/img/sift-grade-1600.webp`.

Do not present an image altered outside Sift as a product-grade result.

## Homepage image transfer budget decision

The project cover update intentionally includes two genuine photographs/screenshots instead of an AI cover or an empty card. An actual Chromium-based CI run measured 277.448 KB transferred on the homepage, including 164.648 KB images and 17 requests, with a 488 ms mobile LCP and CLS 0. Native `loading=lazy` and normal `srcset` are retained; an experimental custom scroll loader was removed because it failed a browser decode regression. The homepage limits are now 282 KB total, 170 KB image, 18 requests (tight measured headroom). Framework JS stays capped at 15 KB with `no_framework: true`. This is a deliberate, documented product-image cost, not an unmeasured budget increase.
