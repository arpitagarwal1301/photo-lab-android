# The README animation

`docs/journey.gif` is one real search, rendered frame by frame:

1. `make_demo_gallery.py` draws 8 simple pictures (a beach, a Wi-Fi settings screen, a note with a Wi-Fi password, a receipt, …). They are drawn by code, so they're free to publish, and their text is known, so it stands in for OCR.
2. `pipeline.py` runs the app's search in Python with the same models and maths: MobileCLIP, MiniLM, BM25, the other-guesses check and Reciprocal Rank Fusion.
3. `render_journey.py` draws each step with those numbers and writes the GIF.

```bash
python scripts/journey-gif/make_demo_gallery.py docs/demo-gallery
python scripts/journey-gif/render_journey.py .work/models docs/demo-gallery docs/journey.gif
```

Needs `pillow`, `numpy`, `onnxruntime` and `tokenizers`, plus the model files in `.work/models` (see the main README). Uses the macOS Helvetica and Menlo fonts; on other systems change the paths in `F()`.
