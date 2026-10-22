"""Builds the launcher icon layers from the supplied artwork.

Input : heart-pulse.webp (heart with the pulse line cut out as transparency), background.png (flat #F2F3EA).
Output: app/src/main/res/drawable-nodpi/ic_launcher_foreground.png (also used as the monochrome layer)
        app/icon-src/play-store-512.png
Run   : python app/icon-src/make_icon.py   (needs Pillow)
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
RES = HERE.parent / "src" / "main" / "res"
CANVAS = 1024          # adaptive icon layer, 108 dp
HEART_WIDTH = 0.56     # share of the canvas; the guaranteed safe zone is 66/108 = 61 %
HEART_RGB = (83, 106, 72)

src = Image.open(HERE / "heart-pulse.webp").convert("RGBA")
alpha = src.getchannel("A").point(lambda a: 0 if a < 8 else 255 if a > 247 else round((a - 8) * 255 / 239))
bbox = alpha.point(lambda a: 255 if a > 128 else 0).getbbox()
alpha = alpha.crop(bbox)

width = round(CANVAS * HEART_WIDTH)
height = round(alpha.height * width / alpha.width)
alpha = alpha.resize((width, height), Image.LANCZOS)

layer = Image.new("RGBA", (CANVAS, CANVAS), HEART_RGB + (0,))
layer.paste(Image.new("RGBA", (width, height), HEART_RGB + (255,)), ((CANVAS - width) // 2, (CANVAS - height) // 2), alpha)
out = RES / "drawable-nodpi"
out.mkdir(parents=True, exist_ok=True)
layer.save(out / "ic_launcher_foreground.png", optimize=True)

bg = Image.open(HERE / "background.png").convert("RGBA").resize((CANVAS, CANVAS))
bg.alpha_composite(layer)
bg.convert("RGB").resize((512, 512), Image.LANCZOS).save(HERE / "play-store-512.png", optimize=True)
print("bbox", bbox, "heart", width, "x", height)
