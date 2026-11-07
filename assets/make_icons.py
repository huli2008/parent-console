"""Build launcher icons from the supplied artwork: crop to the icon, round the corners, export densities."""
from PIL import Image, ImageDraw, ImageFilter
import pathlib

SRC = pathlib.Path(r"D:\Users\huli2\Documents\deepseek-harness\default-workspace\parent-console\icon-src.png")
RES = pathlib.Path(r"D:\Users\huli2\Documents\deepseek-harness\default-workspace\parent-console\android\app\src\main\res")

im = Image.open(SRC).convert("RGB")

# 1. crop to the artwork: drop the white margin around the rounded square
gray = im.convert("L")
w, h = im.size
px = im.load()
g = gray.load()
# scan inward from each side until a non-near-white pixel appears
def not_white(x, y):
    r, gg, b = px[x, y]
    return not (r > 245 and gg > 245 and b > 245)

left = next(x for x in range(w) if any(not_white(x, y) for y in range(0, h, 4)))
right = next(x for x in range(w - 1, -1, -1) if any(not_white(x, y) for y in range(0, h, 4)))
top = next(y for y in range(h) if any(not_white(x, y) for x in range(0, w, 4)))
bottom = next(y for y in range(h - 1, -1, -1) if any(not_white(x, y) for x in range(0, w, 4)))
box = (left, top, right + 1, bottom + 1)
art = im.crop(box)
print("crop:", box, "->", art.size)

# 2. square it, then rounded-rect alpha mask (matches the artwork's own corners)
side = min(art.size)
art = art.crop(((art.width - side) // 2, (art.height - side) // 2,
                (art.width - side) // 2 + side, (art.height - side) // 2 + side))

master = 1024
art = art.resize((master, master), Image.LANCZOS)
mask = Image.new("L", (master, master), 0)
d = ImageDraw.Draw(mask)
radius = int(master * 0.225)
# supersample the mask for smooth corners
ss = 4
big = Image.new("L", (master * ss, master * ss), 0)
bd = ImageDraw.Draw(big)
bd.rounded_rectangle([0, 0, master * ss - 1, master * ss - 1], radius=radius * ss, fill=255)
mask = big.resize((master, master), Image.LANCZOS)

icon = art.convert("RGBA")
icon.putalpha(mask)

# 3. export densities
dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, size in dens.items():
    out = RES / f"mipmap-{name}"
    out.mkdir(parents=True, exist_ok=True)
    icon.resize((size, size), Image.LANCZOS).save(out / "ic_launcher.png", optimize=True)
    print("wrote", out / "ic_launcher.png", size)

# 4. adaptive icon (API 26+): foreground = artwork inset, background = gradient
fg = Image.new("RGBA", (master, master), (0, 0, 0, 0))
inner = int(master * 0.66)
small = icon.resize((inner, inner), Image.LANCZOS)
fg.paste(small, ((master - inner) // 2, (master - inner) // 2), small)
bg = Image.new("RGBA", (master, master), (0, 0, 0, 0))
bd2 = ImageDraw.Draw(bg)
for y in range(master):
    t = y / master
    bd2.line([(0, y), (master, y)], fill=(int(0x6d + (0x3b - 0x6d) * t),
                                          int(0x5c + (0x8c * 0) + (0x2f - 0x5c) * 0),  # placeholder, replaced below
                                          0, 255))

# simple vertical gradient: #7C5CFF -> #2F80ED
bg = Image.new("RGBA", (master, master))
for y in range(master):
    t = y / (master - 1)
    r = int(0x7C + (0x2F - 0x7C) * t)
    g2 = int(0x5C + (0x80 - 0x5C) * t)
    b = int(0xFF + (0xED - 0xFF) * t)
    ImageDraw.Draw(bg).line([(0, y), (master, y)], fill=(r, g2, b, 255))

for name, size in dens.items():
    out = RES / f"mipmap-{name}"
    fg.resize((size * 2, size * 2), Image.LANCZOS).save(out / "ic_launcher_foreground.png")
    bg.resize((size * 2, size * 2), Image.LANCZOS).save(out / "ic_launcher_background.png")

# preview for the report
icon.resize((512, 512), Image.LANCZOS).save(
    r"D:\Users\huli2\Documents\deepseek-harness\default-workspace\parent-console\icon-preview.png")
print("done")
