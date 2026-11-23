"""Draws the 8-photo demo gallery used by the README animation. Everything is drawn here, so it is free to publish.

Each picture's text is known, so it doubles as the "OCR" text (the phone uses ML Kit for that step).
Usage: python scripts/journey-gif/make_demo_gallery.py docs/demo-gallery
"""
import json, math, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "docs/demo-gallery"
S = 512
FONT = "/System/Library/Fonts/Helvetica.ttc"


def font(size, bold=False):
    try:
        return ImageFont.truetype(FONT, size, index=1 if bold else 0)
    except OSError:
        return ImageFont.load_default(size)


def gradient(top, bottom, h=S, w=S):
    im = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    return im


def beach():
    im = gradient((255, 120, 60), (255, 200, 120), h=S)
    d = ImageDraw.Draw(im)
    d.ellipse((190, 210, 320, 340), fill=(255, 236, 150))
    d.rectangle((0, 300, S, 400), fill=(40, 90, 150))
    for y in range(305, 400, 14):
        d.line([(0, y), (S, y + 4)], fill=(255, 170, 110), width=2)
    d.polygon([(0, 390), (S, 360), (S, S), (0, S)], fill=(230, 200, 150))
    d.line([(400, 480), (420, 330)], fill=(70, 50, 30), width=10)  # palm
    for a in range(0, 360, 60):
        d.line([(420, 330), (420 + 70 * math.cos(math.radians(a)), 330 + 30 * math.sin(math.radians(a)))], fill=(30, 70, 30), width=8)
    return im, ""


def phone_frame(bg=(250, 250, 250)):
    im = Image.new("RGB", (S, S), bg)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, S, 34), fill=(235, 235, 235))
    d.text((16, 8), "9:41", fill=(40, 40, 40), font=font(18, True))
    return im, d


def wifi_settings():
    im, d = phone_frame()
    d.text((24, 52), "Wi-Fi", fill=(20, 20, 20), font=font(40, True))
    d.rounded_rectangle((24, 112, 488, 160), 12, fill=(232, 240, 254))
    d.text((40, 124), "Use Wi-Fi", fill=(20, 20, 20), font=font(22))
    d.rounded_rectangle((420, 124, 470, 148), 12, fill=(26, 115, 232))
    d.ellipse((446, 126, 466, 146), fill=(255, 255, 255))
    for i, (name, note) in enumerate([("HomeNet", "Connected"), ("Cafe_Guest", "Saved"), ("Office-5G", "Secured"), ("Neighbour", "Secured")]):
        y = 190 + i * 72
        for r in (8, 16, 24):  # signal arcs
            d.arc((40 - r, y + 30 - r, 40 + r, y + 30 + r), 225, 315, fill=(60, 60, 60), width=3)
        d.text((80, y), name, fill=(20, 20, 20), font=font(24))
        d.text((80, y + 30), note, fill=(110, 110, 110), font=font(18))
        d.line([(24, y + 62), (488, y + 62)], fill=(225, 225, 225), width=1)
    return im, "Wi-Fi Use Wi-Fi HomeNet Connected Cafe_Guest Saved Office-5G Secured Neighbour Secured"


def note():
    im = Image.new("RGB", (S, S), (120, 90, 60))  # wooden fridge? a cork board
    d = ImageDraw.Draw(im)
    for x in range(0, S, 9):
        for y in range((x * 7) % 11, S, 13):
            d.point((x, y), fill=(140, 105, 70))
    paper = Image.new("RGB", (380, 360), (255, 240, 120))
    p = ImageDraw.Draw(paper)
    p.text((28, 40), "Guest WiFi", fill=(30, 30, 120), font=font(40, True))
    p.text((28, 110), "network: HomeNet", fill=(30, 30, 120), font=font(30))
    p.text((28, 170), "password:", fill=(30, 30, 120), font=font(30))
    p.text((28, 220), "sunflower42", fill=(200, 30, 30), font=font(46, True))
    paper = paper.rotate(-6, expand=True, fillcolor=(120, 90, 60))
    im.paste(paper, (50, 60))
    d.ellipse((240, 52, 268, 80), fill=(220, 40, 40))
    return im, "Guest WiFi network: HomeNet password: sunflower42"


def receipt():
    im = Image.new("RGB", (S, S), (60, 60, 66))
    d = ImageDraw.Draw(im)
    d.rectangle((110, 20, 402, 500), fill=(252, 252, 248))
    lines = [("CHAI CORNER", True, 30), ("MG Road, Bengaluru", False, 18), ("", False, 10), ("Masala chai  2 x 40   80", False, 20),
             ("Samosa       1 x 30   30", False, 20), ("Bun maska    1 x 50   50", False, 20), ("", False, 10), ("TOTAL           Rs 160", True, 24),
             ("Paid by UPI", False, 18), ("", False, 10), ("Thank you! Visit again", False, 18)]
    y = 44
    for text, bold, size in lines:
        d.text((130, y), text, fill=(30, 30, 30), font=font(size, bold))
        y += size + 14
    for x in range(110, 402, 16):
        d.polygon([(x, 500), (x + 8, 510), (x + 16, 500)], fill=(252, 252, 248))
    return im, "CHAI CORNER MG Road, Bengaluru. Masala chai 2 x 40 80. Samosa 1 x 30 30. Bun maska 1 x 50 50. TOTAL Rs 160. Paid by UPI. Thank you! Visit again"


def mountains():
    im = gradient((120, 170, 230), (220, 235, 250))
    d = ImageDraw.Draw(im)
    d.polygon([(0, 400), (150, 170), (300, 400)], fill=(90, 100, 120))
    d.polygon([(150, 170), (115, 235), (185, 235)], fill=(250, 250, 255))
    d.polygon([(180, 400), (360, 120), (512, 400)], fill=(70, 80, 100))
    d.polygon([(360, 120), (318, 185), (402, 185)], fill=(250, 250, 255))
    d.rectangle((0, 390, S, S), fill=(70, 130, 70))
    for x in range(20, S, 46):
        d.polygon([(x, 470), (x + 16, 410), (x + 32, 470)], fill=(30, 80, 40))
    return im, ""


def ticket():
    im = Image.new("RGB", (S, S), (230, 232, 236))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((30, 110, 482, 400), 18, fill=(255, 255, 255), outline=(200, 200, 205), width=2)
    d.rectangle((30, 110, 482, 160), fill=(26, 82, 160))
    d.text((50, 120), "RAIL TICKET", fill=(255, 255, 255), font=font(28, True))
    d.text((50, 180), "BLR", fill=(20, 20, 20), font=font(52, True))
    d.line([(205, 212), (265, 212)], fill=(80, 80, 80), width=5)  # arrow
    d.polygon([(265, 200), (285, 212), (265, 224)], fill=(80, 80, 80))
    d.text((300, 180), "MYS", fill=(20, 20, 20), font=font(52, True))
    d.text((50, 260), "PNR 482 199 0112", fill=(40, 40, 40), font=font(24))
    d.text((50, 300), "Coach S4  Seat 32  Mon 14 Dec", fill=(40, 40, 40), font=font(22))
    d.text((50, 340), "Fare Rs 210", fill=(40, 40, 40), font=font(22))
    for i in range(14):  # barcode
        d.rectangle((380 + i * 6, 260, 382 + i * 6 + (i % 3), 360), fill=(20, 20, 20))
    return im, "RAIL TICKET BLR MYS PNR 482 199 0112 Coach S4 Seat 32 Mon 14 Dec Fare Rs 210"


def chat():
    im, d = phone_frame((236, 229, 221))
    d.rectangle((0, 34, S, 90), fill=(7, 94, 84))
    d.text((24, 48), "Riya", fill=(255, 255, 255), font=font(26, True))
    msgs = [(False, "Reached your place!"), (False, "what's the wifi password?"), (True, "It's on the yellow note"), (True, "on the fridge"), (False, "found it, thanks")]
    y = 112
    for mine, text in msgs:
        w = d.textlength(text, font=font(22)) + 32
        x0 = S - 24 - w if mine else 24
        d.rounded_rectangle((x0, y, x0 + w, y + 46), 14, fill=(220, 248, 198) if mine else (255, 255, 255))
        d.text((x0 + 16, y + 11), text, fill=(20, 20, 20), font=font(22))
        y += 66
    return im, "Riya Reached your place! what's the wifi password? It's on the yellow note on the fridge found it, thanks"


def cake():
    im = gradient((255, 230, 240), (255, 210, 225))
    d = ImageDraw.Draw(im)
    d.ellipse((90, 380, 422, 440), fill=(240, 240, 245))  # plate
    d.rectangle((130, 260, 382, 410), fill=(140, 80, 50))
    d.rectangle((130, 250, 382, 290), fill=(255, 250, 250))
    for x in range(140, 382, 28):
        d.ellipse((x, 280, x + 22, 300), fill=(255, 250, 250))
    for x in (175, 225, 275, 325):
        d.rectangle((x, 190, x + 10, 250), fill=(100, 170, 240))
        d.ellipse((x - 4, 165, x + 14, 192), fill=(255, 190, 40))
    d.text((150, 60), "Happy Birthday!", fill=(200, 40, 100), font=font(34, True))
    return im, "Happy Birthday!"


PHOTOS = [("beach", beach), ("wifi-settings", wifi_settings), ("wifi-note", note), ("receipt", receipt),
          ("mountains", mountains), ("train-ticket", ticket), ("chat", chat), ("birthday-cake", cake)]

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    texts = {}
    for name, draw in PHOTOS:
        im, text = draw()
        im = im.filter(ImageFilter.SMOOTH)
        im.save(os.path.join(OUT, name + ".png"), optimize=True)
        texts[name] = text
    json.dump(texts, open(os.path.join(OUT, "texts.json"), "w"), indent=1)
    print(OUT, len(texts), "photos")
