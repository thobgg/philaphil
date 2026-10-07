#!/usr/bin/env python3
"""Erzeugt das App-Symbol (Briefmarke mit Zähnung) für Android-Mipmaps und den Desktop."""
from pathlib import Path
from PIL import Image, ImageDraw

HIER = Path(__file__).resolve().parent.parent
PAPIER = (243, 227, 207, 255)
BRAUN = (122, 74, 30, 255)
GRUEN = (91, 107, 79, 255)
WEISS = (255, 253, 248, 255)


def marke(groesse, hintergrund=None, rand=0.0):
    """Zeichnet eine Marke; rand = Anteil Freiraum (Android-Vordergrund braucht die sichere Zone)."""
    s = 1024
    bild = Image.new("RGBA", (s, s), hintergrund or (0, 0, 0, 0))
    z = ImageDraw.Draw(bild)
    a = int(s * rand)
    links, oben, rechts, unten = a, a, s - a, s - a
    # weisses Markenpapier
    z.rectangle([links, oben, rechts, unten], fill=WEISS)
    # Zaehnung: Loecher am Rand
    schritt = (rechts - links) / 14
    r = schritt * 0.28
    for i in range(15):
        x = links + i * schritt
        z.ellipse([x - r, oben - r, x + r, oben + r], fill=hintergrund or (0, 0, 0, 0))
        z.ellipse([x - r, unten - r, x + r, unten + r], fill=hintergrund or (0, 0, 0, 0))
        y = oben + i * schritt
        z.ellipse([links - r, y - r, links + r, y + r], fill=hintergrund or (0, 0, 0, 0))
        z.ellipse([rechts - r, y - r, rechts + r, y + r], fill=hintergrund or (0, 0, 0, 0))
    # Bildfeld
    innen = schritt * 1.1
    z.rectangle([links + innen, oben + innen, rechts - innen, unten - innen], fill=BRAUN)
    # Motiv: Sonne ueber Huegel (eine kleine Landschaft, ohne fremdes Bild)
    cx = (links + rechts) / 2
    z.ellipse([cx - schritt * 1.6, oben + innen * 1.6, cx + schritt * 1.6, oben + innen * 1.6 + schritt * 3.2], fill=PAPIER)
    z.chord([links + innen * 0.5, unten - innen * 4.2, rechts - innen * 0.5, unten + innen * 2], 180, 360, fill=GRUEN)
    # Wertangabe
    z.rectangle([rechts - innen - schritt * 2.4, unten - innen - schritt * 1.4, rechts - innen - schritt * 0.4, unten - innen - schritt * 0.4], fill=PAPIER)
    return bild.resize((groesse, groesse), Image.LANCZOS)


def main():
    res = HIER / "app" / "src" / "main" / "res"
    dichten = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, faktor in dichten.items():
        ordner = res / f"mipmap-{name}"
        ordner.mkdir(exist_ok=True)
        marke(int(48 * faktor), hintergrund=PAPIER, rand=0.12).save(ordner / "ic_launcher.png")
        marke(int(48 * faktor), hintergrund=PAPIER, rand=0.12).save(ordner / "ic_launcher_round.png")
        # Adaptiv: 108 dp, sichere Zone sind die mittleren 66 dp
        marke(int(108 * faktor), rand=0.22).save(ordner / "ic_launcher_foreground.png")
    desk = HIER / "desktop" / "icons"
    desk.mkdir(exist_ok=True)
    png = marke(512, hintergrund=PAPIER, rand=0.08)
    png.save(desk / "app.png")
    png.save(desk / "app.ico", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("Symbole geschrieben")


if __name__ == "__main__":
    main()
