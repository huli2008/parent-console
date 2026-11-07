from PIL import Image, ImageDraw
import pathlib
RES = pathlib.Path(r"D:\Users\huli2\Documents\deepseek-harness\default-workspace\parent-console\android\app\src\main\res")
icon = Image.open(r"D:\Users\huli2\Documents\deepseek-harness\default-workspace\parent-console\icon-preview.png").convert("RGBA")
master = 1024
big = icon.resize((master, master), Image.LANCZOS)
mask = Image.new("L", (master*4, master*4), 0)
ImageDraw.Draw(mask).ellipse([0,0,master*4-1,master*4-1], fill=255)
mask = mask.resize((master, master), Image.LANCZOS)
rnd = big.copy(); rnd.putalpha(mask)
for name,size in {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}.items():
    out = RES / f"mipmap-{name}"; out.mkdir(parents=True, exist_ok=True)
    rnd.resize((size,size), Image.LANCZOS).save(out/"ic_launcher_round.png", optimize=True)
    print("round", name, size)
