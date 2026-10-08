#!/usr/bin/env python3
"""Erzeugt den Symbolsatz aus docs/icon/symbol.png (1024×1024, Motiv mit türkisem Hintergrund):
Android-Mipmaps (auch adaptiv mit sicherer Zone) und die Desktop-Symbole app.png / app.ico."""
from pathlib import Path
from PIL import Image

HIER = Path(__file__).resolve().parent.parent
QUELLE = HIER / "docs" / "icon" / "symbol.png"
TUERKIS = (64, 171, 181, 255)


def quadrat(groesse, anteil=1.0):
    """Das Motiv auf `anteil` der Kantenlänge verkleinert, Rest in Hintergrundfarbe (adaptives Symbol: 80 %)."""
    bild = Image.open(QUELLE).convert("RGBA").resize((1024, 1024), Image.LANCZOS)
    if anteil < 1.0:
        innen = int(1024 * anteil)
        klein = bild.resize((innen, innen), Image.LANCZOS)
        bild = Image.new("RGBA", (1024, 1024), TUERKIS)
        bild.paste(klein, ((1024 - innen) // 2, (1024 - innen) // 2))
    return bild.resize((groesse, groesse), Image.LANCZOS)


def main():
    res = HIER / "app" / "src" / "main" / "res"
    for name, faktor in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
        ordner = res / f"mipmap-{name}"
        ordner.mkdir(exist_ok=True)
        quadrat(int(48 * faktor)).save(ordner / "ic_launcher.png")
        quadrat(int(48 * faktor)).save(ordner / "ic_launcher_round.png")
        # Adaptiv: 108 dp, die Form (Kreis, abgerundet) schneidet der Launcher; das Motiv bleibt in der Mitte
        quadrat(int(108 * faktor), anteil=0.78).save(ordner / "ic_launcher_foreground.png")
    desk = HIER / "desktop" / "icons"
    desk.mkdir(exist_ok=True)
    png = quadrat(512)
    png.save(desk / "app.png")
    png.save(desk / "app.ico", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("Symbole geschrieben")


if __name__ == "__main__":
    main()
