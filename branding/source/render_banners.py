"""Screenshots every asset in banners.html with headless Chrome into branding/banners/.

Run from the repository root: python branding/source/render_banners.py
"""
import subprocess
import tempfile
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "banners"
# Chrome, not Edge: Edge is redirected by another program on this PC.
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
PROFILE = tempfile.mkdtemp(prefix="bei-chrome-")

# name: (width, height, device scale). Description images are small and rendered at 2x so they
# stay sharp; gallery images are full size.
ASSETS = {
    "banner": (800, 150, 2),
    "social": (1280, 640, 1),
    "features": (1920, 1080, 1),
    **{f"h_{k}": (800, 46, 2) for k in ("features", "players", "authors", "install", "keys", "config", "faq")},
}

OUT.mkdir(parents=True, exist_ok=True)
page = (HERE / "banners.html").as_uri()
for name, (w, h, scale) in ASSETS.items():
    target = OUT / f"{name}.png"
    # Headless Chrome can hang on very short windows, so render at least 400 tall and crop.
    win_h = max(h, 400)
    for attempt in range(2):
        try:
            subprocess.run([
                CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--force-device-scale-factor={scale}",
                "--allow-file-access-from-files", f"--user-data-dir={PROFILE}", "--virtual-time-budget=2000",
                f"--window-size={w},{win_h}", f"--screenshot={target}", f"{page}#{name}",
            ], check=True, capture_output=True, timeout=60)
            break
        except subprocess.TimeoutExpired:
            if attempt:
                raise
    if win_h != h:
        with Image.open(target) as im:
            cropped = im.crop((0, 0, w * scale, h * scale))
            cropped.load()
        cropped.save(target, optimize=True)
    print("wrote", target.name)
