"""Builds the RuStore phone screenshots (1080x1920, 9:16) from the Roborazzi snapshots.

Run from the project root: python store/rustore/make_screenshots.py (needs Pillow).
Each snapshot is scaled to the frame height and centred on its own background colour,
so the store shows the real interface without system bars or added captions.
"""

from pathlib import Path

from PIL import Image

SHOTS = [
    ("01-glavnaya", "feature/home/src/test/snapshots/home_demo_sage_light.png"),
    ("02-vvod", "feature/entry/src/test/snapshots/entry_filled_sage_light.png"),
    ("03-dnevnik", "feature/diary/src/test/snapshots/diary_demo_sage_light.png"),
    ("04-grafiki", "feature/charts/src/test/snapshots/charts_30_sage_light.png"),
    ("05-napominaniya", "feature/reminders/src/test/snapshots/reminders_list_sage_light.png"),
    ("06-otchet-dlya-vracha", "feature/transfer/src/test/snapshots/export_sage_light.png"),
    ("07-urovni", "feature/levels/src/test/snapshots/levels_default_sage_light.png"),
    ("08-temnaya-tema", "feature/home/src/test/snapshots/home_demo_sage_dark.png"),
]
WIDTH, HEIGHT = 1080, 1920
OUT = Path("store/rustore/screenshots")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, source in SHOTS:
        image = Image.open(source).convert("RGB")
        width = round(image.width * HEIGHT / image.height)
        scaled = image.resize((width, HEIGHT), Image.LANCZOS)
        background = image.getpixel((2, image.height // 2))
        frame = Image.new("RGB", (WIDTH, HEIGHT), background)
        frame.paste(scaled, ((WIDTH - width) // 2, 0))
        frame.save(OUT / f"{name}.png", optimize=True)
        print(OUT / f"{name}.png")


if __name__ == "__main__":
    main()
