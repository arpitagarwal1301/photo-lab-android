"""Renders docs/journey.gif: one search, step by step, with the real numbers from pipeline.py.

Usage (from the repo root, with onnxruntime, tokenizers, pillow, numpy installed):
    python scripts/journey-gif/make_demo_gallery.py docs/demo-gallery
    python scripts/journey-gif/render_journey.py .work/models docs/demo-gallery docs/journey.gif
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipeline as P  # noqa: E402

MODELS, GALLERY, OUT = (sys.argv[1:4] + [".work/models", "docs/demo-gallery", "docs/journey.gif"][len(sys.argv) - 1:])[:3]
QUERY = "wifi password"
W, H, FPS = 1080, 608, 12

BG, CARD, CARD2, INK, MUTED, LINE = (15, 17, 21), (27, 30, 36), (38, 42, 50), (232, 234, 237), (154, 160, 166), (60, 64, 72)
ACCENT, ORANGE, BLUE, GREEN, RED, YELLOW = (138, 180, 248), (232, 113, 10), (26, 115, 232), (129, 201, 149), (242, 139, 130), (253, 214, 99)


def F(size, bold=False, mono=False):
    path = "/System/Library/Fonts/Menlo.ttc" if mono else "/System/Library/Fonts/Helvetica.ttc"
    return ImageFont.truetype(path, size, index=1 if bold else 0)


def ease(t):
    t = min(max(t, 0.0), 1.0)
    return t * t * (3 - 2 * t)


def seg(p, a, b):
    """Local 0→1 progress of p within [a, b]."""
    return ease((p - a) / (b - a)) if b > a else float(p >= b)


def wrap(d, text, font, width):
    out, line = [], ""
    for w in text.split():
        t = (line + " " + w).strip()
        if d.textlength(t, font=font) > width and line: out.append(line); line = w
        else: line = t
    return out + [line] if line else out


# ---------- data ----------
models = P.Models(MODELS)
R = P.search(models, GALLERY, QUERY)
NAMES = R["names"]
THUMB = {n: Image.open(os.path.join(GALLERY, n + ".png")).convert("RGB") for n in NAMES}
_cache = {}


def thumb(n, s):
    k = (n, s)
    if k not in _cache: _cache[k] = THUMB[n].resize((s, s), Image.LANCZOS)
    return _cache[k]


RESULTS = [f["photo"] for f in R["fusion"]]
STEPS = ["typed", "pieces", "vectors", "nearest", "3 signals", "merge", "results"]

# ---------- layout pieces ----------
PX, PY, PW, PH = 36, 20, 300, 568           # phone
RX, RY, RW = 372, 26, 672                   # right panel
GRID_Y, CELL, GAP = PY + 120, 84, 6


def grid_pos(i):
    return PX + 22 + (i % 3) * (CELL + GAP), GRID_Y + (i // 3) * (CELL + GAP)


def draw_phone(im, d, typed, caret, results_p):
    d.rounded_rectangle((PX, PY, PX + PW, PY + PH), 36, fill=(8, 9, 11), outline=LINE, width=2)
    d.rounded_rectangle((PX + 10, PY + 10, PX + PW - 10, PY + PH - 10), 28, fill=CARD)
    d.text((PX + 26, PY + 24), "Photo Lab", font=F(18, True), fill=INK)
    d.rounded_rectangle((PX + 22, PY + 58, PX + PW - 22, PY + 100), 12, fill=CARD2, outline=ACCENT if typed is not None else LINE, width=2)
    if typed is None or typed == "":
        d.text((PX + 36, PY + 70), "Describe a photo…", font=F(16), fill=MUTED)
        if typed == "" and caret: d.line((PX + 36, PY + 68, PX + 36, PY + 90), fill=INK, width=2)
    else:
        d.text((PX + 36, PY + 69), typed, font=F(18), fill=INK)
        if caret:
            x = PX + 36 + d.textlength(typed, font=F(18))
            d.line((x + 2, PY + 68, x + 2, PY + 90), fill=INK, width=2)
    # gallery → results: photos slide to their result position; the rest fade out
    order = RESULTS + [n for n in NAMES if n not in RESULTS]
    for n in NAMES:
        a = grid_pos(NAMES.index(n)); b = grid_pos(order.index(n))
        x = int(a[0] + (b[0] - a[0]) * results_p); y = int(a[1] + (b[1] - a[1]) * results_p)
        t = thumb(n, CELL)
        if n not in RESULTS and results_p > 0:
            t = Image.blend(t, Image.new("RGB", t.size, CARD), 0.8 * results_p)
        im.paste(t, (x, y))
        if n in RESULTS and results_p > 0.6:
            r = RESULTS.index(n) + 1
            d.rounded_rectangle((x, y, x + 22, y + 20), 5, fill=ACCENT)
            d.text((x + 6, y + 2), str(r), font=F(14, True), fill=BG)
    if results_p > 0.9:
        d.text((PX + 26, PY + PH - 60), f"{len(RESULTS)} results · on the phone", font=F(14), fill=MUTED)


def draw_steps(d, cur):
    y = H - 34; x0, x1 = RX, RX + RW
    for i, s in enumerate(STEPS):
        x = x0 + i * (x1 - x0) / (len(STEPS) - 1)
        if i: d.line((x - (x1 - x0) / (len(STEPS) - 1) + 8, y, x - 8, y), fill=ACCENT if i <= cur else LINE, width=2)
        d.ellipse((x - 7, y - 7, x + 7, y + 7), fill=ACCENT if i <= cur else BG, outline=ACCENT if i <= cur else LINE, width=2)
        tw = d.textlength(s, font=F(13))
        d.text((min(max(x - tw / 2, x0 - 10), x1 - tw + 10), y + 12), s, font=F(13, i == cur), fill=INK if i == cur else MUTED)


def header(d, n, title, explain):
    d.rounded_rectangle((RX, RY, RX + 34, RY + 30), 15, fill=ACCENT)
    d.text((RX + (12 if n < 10 else 7), RY + 5), str(n), font=F(18, True), fill=BG)
    d.text((RX + 46, RY + 2), title, font=F(26, True), fill=INK)
    for i, line in enumerate(wrap(d, explain, F(15), RW)):
        d.text((RX, RY + 44 + i * 21), line, font=F(15), fill=MUTED)


def chip(d, x, y, piece, id_, scale=1.0, hi=False):
    f1, f2 = F(18), F(13, mono=True)
    w = max(d.textlength(piece, font=f1), d.textlength(str(id_), font=f2)) + 24
    if scale <= 0.05: return w
    cx, cy = x + w / 2, y + 26
    hw, hh = w / 2 * scale, 26 * scale
    d.rounded_rectangle((cx - hw, cy - hh, cx + hw, cy + hh), 10, fill=(56, 74, 110) if hi else CARD2)
    if scale > 0.8:
        d.text((x + 12, y + 6), piece, font=f1, fill=INK)
        d.text((x + 12, y + 31), str(id_), font=f2, fill=MUTED)
    return w


def barcode(d, x, y, w, h, vec, p):
    m = np.abs(vec).max(); n = len(vec); bw = w / n
    for i in range(int(n * p)):
        v = vec[i]; a = 0.15 + 0.85 * abs(v) / m
        c = ORANGE if v >= 0 else BLUE
        col = tuple(int(BG[k] + (c[k] - BG[k]) * a) for k in range(3))
        d.rectangle((x + i * bw, y, x + (i + 1) * bw, y + h), fill=col)


def bar(d, x, y, w, value, vmax, p, kept=True, marker=None):
    d.rectangle((x, y, x + w, y + 8), fill=CARD2)
    d.rectangle((x, y, x + w * min(value / vmax, 1) * p, y + 8), fill=GREEN if kept else RED)
    if marker is not None:
        mx = x + w * min(marker / vmax, 1); d.line((mx, y - 4, mx, y + 12), fill=INK, width=2)


def tick(d, x, y, ok, size=18):
    """A drawn ✓ (green) or ✗ (red): the system font has no such glyphs."""
    s = size
    if ok: d.line([(x, y + s * 0.55), (x + s * 0.38, y + s * 0.9), (x + s, y + s * 0.1)], fill=GREEN, width=4, joint="curve")
    else:
        d.line((x + 2, y + 2, x + s - 2, y + s - 2), fill=RED, width=4); d.line((x + s - 2, y + 2, x + 2, y + s - 2), fill=RED, width=4)


# ---------- scenes: (name, seconds, draw(im, d, p)) ----------
def s_cursor(im, d, p):
    header(d, 1, "Search your photos", "All 8 photos in this demo gallery were indexed on the phone: every photo is now a list of numbers.")
    d.text((RX, RY + 140), "Tap the search box…", font=F(22), fill=INK)


def s_typed(im, d, p):
    header(d, 1, "You type", "Everything that follows runs on the phone, offline, in about 40 ms.")
    n = int(len(QUERY) * seg(p, 0, 0.8))
    d.text((RX, RY + 150), QUERY[:n], font=F(64, True), fill=INK)
    if p < 0.95 and int(p * 12) % 2 == 0:
        x = RX + d.textlength(QUERY[:n], font=F(64, True)); d.rectangle((x + 4, RY + 156, x + 10, RY + 220), fill=ACCENT)


def s_pieces(im, d, p):
    header(d, 2, "Cut into pieces, then numbers", "Networks can't read letters. Each model has a fixed list of word pieces and swaps each piece for its number. Start and end markers are added.")
    d.text((RX, RY + 120), "MobileCLIP · byte-pair pieces (49,408 in its list)", font=F(16, True), fill=ACCENT)
    x = RX
    for i, (t, id_) in enumerate(zip(R["clip_tokens"], R["clip_ids"])):
        x += chip(d, x, RY + 146, t.replace("<|startoftext|>", "<start>").replace("<|endoftext|>", "<end>"), id_, seg(p, 0.05 + i * 0.07, 0.15 + i * 0.07), i in (0, len(R["clip_ids"]) - 1)) + 8
    d.text((RX, RY + 232), "MiniLM · word-pieces (30,522 in its list)", font=F(16, True), fill=ACCENT)
    x = RX
    for i, (t, id_) in enumerate(zip(R["pieces"], R["piece_ids"])):
        x += chip(d, x, RY + 258, t, id_, seg(p, 0.4 + i * 0.07, 0.5 + i * 0.07), i in (0, len(R["piece_ids"]) - 1)) + 8
    if p > 0.85:
        d.text((RX, RY + 340), "Same word, different cuts: “wifi” is one piece for CLIP, “wi” + “##fi” for MiniLM.", font=F(16), fill=YELLOW)


def s_vectors(im, d, p):
    header(d, 3, "Numbers become meaning (neural networks)", "Each network turns the pieces into one list of numbers, a vector. Similar meanings point the same way. Each photo was turned into 512 numbers the same way.")
    for row, (label, vec, ms, y) in enumerate([("MobileCLIP text encoder", R["q_clip"], "512 numbers · ~25 ms on the phone", RY + 130), ("MiniLM", R["q_text"], "384 numbers · ~8 ms", RY + 250)]):
        q = seg(p, row * 0.35, row * 0.35 + 0.55)
        d.rounded_rectangle((RX, y, RX + 190, y + 56), 10, fill=CARD2, outline=ACCENT, width=2)
        d.text((RX + 14, y + 8), label.split(" text")[0], font=F(18, True), fill=INK)
        d.text((RX + 14, y + 32), "text network" if row == 0 else "sentence network", font=F(13), fill=MUTED)
        # dots flowing from the box into the barcode
        for k in range(5):
            fx = RX + 196 + ((q * 3 + k / 5) % 1) * 40
            if 0 < q < 1: d.ellipse((fx - 3, y + 25, fx + 3, y + 31), fill=ACCENT)
        barcode(d, RX + 244, y + 6, RW - 244, 44, vec, q)
        if q > 0.6:
            d.text((RX + 244, y + 58), ms + "   [" + ", ".join(f"{v:.3f}" for v in vec[:4]) + ", …]", font=F(13, mono=True), fill=MUTED)
    d.text((RX, RY + 360), "orange = positive · blue = negative · brighter = bigger", font=F(13), fill=MUTED)


def s_nearest(im, d, p):
    header(d, 4, "Find the nearest photos", "Each photo is a point in 512-dimensional space (squashed to 2-D here). Nearest to the query = most similar. With thousands of photos, an HNSW graph finds them without measuring every one.")
    x0, y0, x1, y1 = RX + 20, RY + 120, RX + RW - 20, RY + 480
    pts = np.array([R["xy"][n] for n in NAMES]); lo, hi = pts.min(0), pts.max(0)
    pos = {n: (x0 + 40 + (R["xy"][n][0] - lo[0]) / (hi[0] - lo[0] + 1e-9) * (x1 - x0 - 80), y0 + 30 + (R["xy"][n][1] - lo[1]) / (hi[1] - lo[1] + 1e-9) * (y1 - y0 - 60)) for n in NAMES}
    for _ in range(60):  # nudge overlapping thumbnails apart so every photo stays visible
        for a in NAMES:
            for b in NAMES:
                if a < b:
                    ax, ay = pos[a]; bx, by = pos[b]; dx, dy = bx - ax, by - ay
                    if abs(dx) < 64 and abs(dy) < 64:
                        push = 3 if dx >= 0 else -3
                        pos[a], pos[b] = (min(max(ax - push, x0 + 30), x1 - 30), ay), (min(max(bx + push, x0 + 30), x1 - 30), by + (2 if dy >= 0 else -2))
    sims = {r["photo"]: r["score"] for r in R["look"]}
    w = np.array([math.exp(60 * sims[n]) for n in NAMES]); w /= w.sum()
    qx = sum(w[i] * pos[n][0] for i, n in enumerate(NAMES)); qy = sum(w[i] * pos[n][1] for i, n in enumerate(NAMES)) - 40
    top = [r["photo"] for r in R["look"][:3]]
    for i, n in enumerate(top):
        q = seg(p, 0.35 + i * 0.15, 0.55 + i * 0.15)
        if q > 0:
            px, py = pos[n]; d.line((qx, qy, qx + (px - qx) * q, qy + (py - qy) * q), fill=ACCENT, width=3)
            if q > 0.9: d.text(((qx + px) / 2 + 6, (qy + py) / 2 - 18), f"{sims[n]:.2f}", font=F(15, True, mono=True), fill=ACCENT)
    for n in NAMES:
        x, y = pos[n]; t = thumb(n, 52)
        im.paste(t, (int(x - 26), int(y - 26)))
        d.rectangle((x - 27, y - 27, x + 26, y + 26), outline=ACCENT if n in top and p > 0.7 else LINE, width=2)
    a = seg(p, 0.1, 0.3)
    if a > 0:
        r = 10 + 4 * math.sin(p * 20)
        d.regular_polygon((qx, qy, r * a), 5, rotation=-18, fill=YELLOW)
        label = "“" + QUERY + "”"; lw = d.textlength(label, font=F(16, True))
        d.text((qx + 16 if qx + 16 + lw < RX + RW else qx - 16 - lw, qy - 10), label, font=F(16, True), fill=YELLOW)


def s_signals(im, d, p):
    header(d, 5, "Three ways to match", "Each signal makes its own list, and a photo only gets on a list if it clears that signal's bar.")
    cw = (RW - 24) / 3
    cols = [("1  Looks like it", "CLIP: query vs pixels. Must beat the best generic guess (black line)."),
            ("2  Text means it", "MiniLM: query vs text read from the photo. Bar = 0.45."),
            ("3  Contains the words", "BM25: exact words, rare words count more.")]
    for c, (title, sub) in enumerate(cols):
        q = seg(p, c * 0.3, c * 0.3 + 0.3)
        if q <= 0: continue
        x = RX + c * (cw + 12); y = RY + 110
        d.rounded_rectangle((x, y, x + cw, y + 360), 12, fill=CARD)
        d.text((x + 12, y + 10), title, font=F(17, True), fill=INK)
        for i, line in enumerate(wrap(d, sub, F(12), cw - 24)): d.text((x + 12, y + 34 + i * 16), line, font=F(12), fill=MUTED)
        rows = R["look"][:4] if c == 0 else R["meaning"][:4] if c == 1 else R["word_rows"][:4]
        for i, r in enumerate(rows):
            ry = y + 92 + i * 66
            im.paste(thumb(r["photo"], 44), (int(x + 12), int(ry)))
            if c == 0:
                bar(d, x + 66, ry + 8, cw - 110, r["score"], 0.4, q, r["kept"], r["rival_score"])
                d.text((x + 66, ry + 22), f"{r['score']:.2f} vs {r['rival_score']:.2f}", font=F(12, mono=True), fill=MUTED)
                d.text((x + 66, ry + 38), r["rival"].replace("a screenshot of ", "screenshot: ")[:26], font=F(11), fill=MUTED)
            elif c == 1:
                bar(d, x + 66, ry + 8, cw - 110, r["score"], 0.8, q, r["kept"], 0.45)
                d.text((x + 66, ry + 22), f"{r['score']:.2f}", font=F(12, mono=True), fill=MUTED)
                d.text((x + 66, ry + 38), ("“" + r["chunk"])[:24] + "…", font=F(11), fill=MUTED)
            else:
                for j, (w_, tf, idf, part) in enumerate(t for t in r["terms"] if t[1]):
                    d.text((x + 66, ry + 2 + j * 17), f"{w_} ×{tf} → {part:.2f}", font=F(12, mono=True), fill=MUTED)
                d.text((x + cw - 50, ry + 12), f"{r['score']:.2f}", font=F(16, True), fill=GREEN)
            if c < 2 and q > 0.7:
                tick(d, x + cw - 32, ry + 8, r["kept"])
        if c == 2 and q > 0.5:
            d.text((x + 12, y + 92 + len(rows) * 66), "The Wi-Fi screen says “Wi-Fi”,\nnot “wifi”: no exact match.", font=F(12), fill=YELLOW)


def s_merge(im, d, p):
    header(d, 6, "Merge the lists (Reciprocal Rank Fusion)", "Scores from different signals can't be compared, so only positions count: each list adds 1 / (60 + rank). Being on all three lists beats being #1 on one.")
    names = ["looks", "means", "words"]
    vmax = R["fusion"][0]["total"]
    for i, f in enumerate(R["fusion"][:4]):
        q = seg(p, i * 0.15, i * 0.15 + 0.4)
        if q <= 0: continue
        y = RY + 124 + i * 92
        d.rounded_rectangle((RX, y, RX + RW, y + 80), 12, fill=CARD if i else (34, 44, 60))
        d.text((RX + 14, y + 26), f"#{i + 1}", font=F(22, True), fill=ACCENT if i == 0 else INK)
        im.paste(thumb(f["photo"], 60), (RX + 60, y + 10))
        x = RX + 136
        for s, rank in enumerate(f["ranks"]):
            label = f"{names[s]} #{rank}" if rank else f"{names[s]} –"
            tw = d.textlength(label, font=F(14)) + 16
            d.rounded_rectangle((x, y + 12, x + tw, y + 36), 8, fill=(40, 70, 50) if rank else CARD2)
            d.text((x + 8, y + 16), label, font=F(14), fill=INK if rank else MUTED)
            x += tw + 8
        parts = [f"1/{60 + r}" for r in f["ranks"] if r]
        d.text((RX + 136, y + 46), " + ".join(parts) + f" = {f['total']:.4f}", font=F(14, mono=True), fill=MUTED)
        bar(d, RX + RW - 170, y + 52, 150, f["total"], vmax, q)


def s_results(im, d, p):
    header(d, 7, "Results", "Best first. Each result can say why it matched, so nothing is a black box.")
    best = R["fusion"][0]["photo"]
    a = seg(p, 0.2, 0.5)
    t = thumb(best, 200)
    if a > 0:
        im.paste(Image.blend(Image.new("RGB", t.size, BG), t, a), (RX, RY + 110))
        d.rectangle((RX - 2, RY + 108, RX + 201, RY + 311), outline=ACCENT, width=3)
    why = []
    lk = next(r for r in R["look"] if r["photo"] == best); mn = next(r for r in R["meaning"] if r["photo"] == best)
    wr = next(r for r in R["word_rows"] if r["photo"] == best)
    why.append(("Looks like it", f"{lk['score']:.2f} beats “{lk['rival']}” {lk['rival_score']:.2f}"))
    why.append(("Text means it", f"“{mn['chunk'][:30]}…” {mn['score']:.2f}"))
    why.append(("Contains the words", " + ".join(t[0] for t in wr["terms"] if t[1]) + f" = {wr['score']:.2f}"))
    d.text((RX + 230, RY + 110), "Why it matched", font=F(20, True), fill=INK)
    for i, (k, v) in enumerate(why):
        q = seg(p, 0.35 + i * 0.12, 0.5 + i * 0.12)
        if q <= 0: continue
        y = RY + 150 + i * 56
        tick(d, RX + 230, y + 1, True, 16); d.text((RX + 256, y), k, font=F(17, True), fill=GREEN)
        d.text((RX + 252, y + 24), v, font=F(14), fill=MUTED)
    if p > 0.75:
        d.text((RX, RY + 340), "Search → result: ~40 ms on a mid-range phone. No photo leaves the device.", font=F(16), fill=YELLOW)


SCENES = [("cursor", 1.6, s_cursor), ("typed", 2.0, s_typed), ("pieces", 3.6, s_pieces), ("vectors", 3.4, s_vectors),
          ("nearest", 3.8, s_nearest), ("signals", 5.6, s_signals), ("merge", 4.0, s_merge), ("results", 5.0, s_results)]


def frame(scene_i, p):
    im = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(im)
    name = SCENES[scene_i][0]
    if name == "cursor": typed, results = None, 0.0
    elif name == "typed": typed, results = QUERY[:int(len(QUERY) * seg(p, 0, 0.8))], 0.0
    elif name == "results": typed, results = QUERY, seg(p, 0, 0.5)
    else: typed, results = QUERY, 0.0
    draw_phone(im, d, typed, name in ("cursor", "typed") and int(p * 10) % 2 == 0 and (name == "typed" or p > 0.7), results)
    SCENES[scene_i][2](im, d, p)
    draw_steps(d, max(0, scene_i - 1))
    if name == "cursor":  # cursor glides in and clicks the search box
        sx, sy, ex, ey = W - 120, H - 140, PX + 150, PY + 84
        q = seg(p, 0, 0.6); x, y = sx + (ex - sx) * q, sy + (ey - sy) * q
        if p > 0.6:
            r = 10 + 30 * seg(p, 0.6, 1.0); d.ellipse((ex - r, ey - r, ex + r, ey + r), outline=ACCENT, width=3)
        d.polygon([(x, y), (x, y + 26), (x + 7, y + 20), (x + 12, y + 31), (x + 17, y + 29), (x + 12, y + 18), (x + 21, y + 18)], fill=INK, outline=BG)
    return im


frames, durations = [], []
for i, (_, secs, _) in enumerate(SCENES):
    n = int(secs * FPS)
    for k in range(n):
        frames.append(frame(i, k / (n - 1))); durations.append(int(1000 / FPS))
durations[-1] = 2500  # hold the result before looping

# One shared palette for all frames keeps colours stable and the file small.
sample = Image.new("RGB", (W, H * 4))
for j, k in enumerate(np.linspace(0, len(frames) - 1, 4).astype(int)): sample.paste(frames[k], (0, H * j))
pal = sample.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
q = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in frames]
os.makedirs(os.path.dirname(OUT) or ".", exist_ok=True)
q[0].save(OUT, save_all=True, append_images=q[1:], duration=durations, loop=0, optimize=True, disposal=1)
print(OUT, len(frames), "frames", round(os.path.getsize(OUT) / 1e6, 2), "MB")
