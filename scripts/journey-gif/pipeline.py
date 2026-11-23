"""The Photo Lab search pipeline in Python, for the README animation: same models, same maths as the app.

MobileCLIP (looks like it) + MiniLM over text chunks (text means it) + BM25 (contains the words) → Reciprocal Rank Fusion,
with the "other guesses" check. Numbers here match the phone to about 3 decimals (the app is checked against this
kind of Python reference in embed-core's tests).
"""
import json, math, os, re
import numpy as np
import onnxruntime as ort
from PIL import Image
from tokenizers import BertWordPieceTokenizer, Tokenizer

OTHER_GUESSES = [
    "a screenshot of a phone app", "a screenshot of a web page", "a screenshot of a chat", "a screenshot of a settings menu",
    "a document with text", "a photo of a person", "a selfie", "a group of people", "a photo of food", "a room indoors",
    "a city street", "a landscape", "a building", "a car", "an animal", "a product photo", "a blurry photo", "a photo of an object",
]
SAME_AS_QUERY, MIN_TEXT, RRF_K, K1, B = 0.8, 0.45, 60, 1.2, 0.75
STOP = {"a", "an", "the", "of", "and", "or", "to", "in", "on", "for", "is", "it", "at", "with", "by"}


class Models:
    def __init__(self, models_dir):
        c, m = os.path.join(models_dir, "mobileclip"), os.path.join(models_dir, "minilm")
        q8 = os.path.join(c, "text_model_q8.onnx")
        self.clip_text = ort.InferenceSession(q8 if os.path.exists(q8) else os.path.join(c, "text_model.onnx"))
        self.clip_vision = ort.InferenceSession(os.path.join(c, "vision_model.onnx"))
        self.clip_tok = Tokenizer.from_file(os.path.join(c, "tokenizer.json"))
        self.minilm = ort.InferenceSession(os.path.join(m, "model.onnx"))
        self.wp = BertWordPieceTokenizer(os.path.join(m, "vocab.txt"), lowercase=True)

    def clip_tokens(self, text):
        e = self.clip_tok.encode(text)
        return [t.replace("</w>", "") for t in e.tokens], e.ids

    def embed_text(self, text):
        ids = self.clip_tok.encode(text).ids[:77]; ids += [0] * (77 - len(ids))
        v = self.clip_text.run(None, {"input_ids": np.array([ids], dtype=np.int64)})[0][0]
        return v / np.linalg.norm(v)

    def embed_image(self, path):
        im = Image.open(path).convert("RGB"); w, h = im.size; s = 256 / min(w, h)
        im = im.resize((256 if w <= h else int(w * s), 256 if h < w else int(h * s)), Image.BILINEAR)
        w, h = im.size; l, t = round((w - 256) / 2), round((h - 256) / 2); im = im.crop((l, t, l + 256, t + 256))
        x = (np.asarray(im, dtype=np.float32) / 255).transpose(2, 0, 1)[None]
        v = self.clip_vision.run(None, {"pixel_values": x})[0][0]
        return v / np.linalg.norm(v)

    def pieces(self, text):
        e = self.wp.encode(text); return e.tokens, e.ids

    def embed_minilm(self, text):
        e = self.wp.encode(text); ids = np.array([e.ids], dtype=np.int64)
        feeds = {"input_ids": ids, "attention_mask": np.ones_like(ids), "token_type_ids": np.zeros_like(ids)}
        h = self.minilm.run(None, {k: v for k, v in feeds.items() if k in [i.name for i in self.minilm.get_inputs()]})[0][0]
        v = h.mean(0); return v / np.linalg.norm(v)


def words(text):
    return [w for w in re.findall(r"[^\W_]+", text.lower()) if w not in STOP]


def bm25(docs, query):
    """Returns {doc: (score, [(word, tf, idf, part)])} for docs containing any query word."""
    toks = {d: words(t) for d, t in docs.items() if t}
    n, avg = len(toks), np.mean([len(w) for w in toks.values()])
    out = {}
    for d, ws in toks.items():
        parts = []
        for w in dict.fromkeys(words(query)):
            tf = ws.count(w); df = sum(1 for x in toks.values() if w in x)
            idf = math.log(1 + (n - df + 0.5) / (df + 0.5))
            part = idf * tf * (K1 + 1) / (tf + K1 * (1 - B + B * len(ws) / avg)) if tf else 0.0
            parts.append((w, tf, idf, part))
        if any(p[3] for p in parts): out[d] = (sum(p[3] for p in parts), parts)
    return out


def search(models, gallery_dir, query):
    texts = json.load(open(os.path.join(gallery_dir, "texts.json")))
    names = list(texts)
    img = {n: models.embed_image(os.path.join(gallery_dir, n + ".png")) for n in names}
    chunks = {n: (t, models.embed_minilm(t)) for n, t in texts.items() if t}   # short texts: one chunk each
    q_clip, q_text = models.embed_text(query), models.embed_minilm(query)
    guesses = [(g, v) for g, v in ((g, models.embed_text(g)) for g in OTHER_GUESSES) if float(v @ q_clip) < SAME_AS_QUERY]

    look = []
    for n in names:
        s = float(img[n] @ q_clip)
        rival, rs = max(((g, float(img[n] @ v)) for g, v in guesses), key=lambda x: x[1])
        look.append(dict(photo=n, score=s, rival=rival, rival_score=rs, kept=s > rs))
    look.sort(key=lambda r: -r["score"])
    meaning = sorted((dict(photo=n, chunk=t, score=float(v @ q_text), kept=float(v @ q_text) > MIN_TEXT) for n, (t, v) in chunks.items()), key=lambda r: -r["score"])
    bm = bm25(texts, query)
    word_rows = sorted((dict(photo=n, score=s, terms=p) for n, (s, p) in bm.items()), key=lambda r: -r["score"])

    lists = [[r["photo"] for r in look if r["kept"]], [r["photo"] for r in meaning if r["kept"]], [r["photo"] for r in word_rows]]
    fused = {}
    for li in lists:
        for rank, p in enumerate(li, 1): fused[p] = fused.get(p, 0) + 1 / (RRF_K + rank)
    fusion = []
    for p, total in sorted(fused.items(), key=lambda x: -x[1]):
        ranks = [li.index(p) + 1 if p in li else None for li in lists]
        fusion.append(dict(photo=p, ranks=ranks, parts=[1 / (RRF_K + r) if r else 0 for r in ranks], total=total))

    # 2-D map of the vector space (PCA of the photo vectors), with the query placed in it.
    X = np.stack([img[n] for n in names]); mu = X.mean(0)
    _, _, vt = np.linalg.svd(X - mu, full_matrices=False)
    xy = {n: (X[i] - mu) @ vt[:2].T for i, n in enumerate(names)}

    ct, ci = models.clip_tokens(query); wt, wi = models.pieces(query)
    return dict(query=query, names=names, texts=texts, clip_tokens=ct, clip_ids=ci, pieces=wt, piece_ids=wi,
                q_clip=q_clip, q_text=q_text, look=look, meaning=meaning, words=list(dict.fromkeys(words(query))),
                word_rows=word_rows, fusion=fusion, xy=xy)
