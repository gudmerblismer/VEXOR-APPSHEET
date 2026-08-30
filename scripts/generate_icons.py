#!/usr/bin/env python3
"""
Genera todos los iconos de Android (legacy, redondo, adaptativo y monocromo)
a partir de UNA imagen de origen (el logo que sube el cliente en Vexor).

Uso:
    python3 scripts/generate_icons.py <URL_O_PATH_DEL_ICONO> <RUTA_RES>

Ejemplo:
    python3 scripts/generate_icons.py https://.../icono.png app/src/main/res

Reemplaza en el proyecto:
    mipmap-*/logo_vexor.webp
    mipmap-*/logo_vexor_round.webp
    mipmap-*/logo_vexor_foreground.webp
    mipmap-*/logo_vexor_monochrome.webp
"""
import sys
import os
import io
import urllib.request
from PIL import Image, ImageDraw, ImageOps

# Tamaños legacy (icono cuadrado/redondo completo) por densidad
LEGACY_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Tamaños del foreground adaptativo (el lienzo es más grande que el icono final,
# porque Android recorta según la máscara del launcher; dejamos ~66% de zona segura)
ADAPTIVE_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def load_source_image(source: str) -> Image.Image:
    if source.startswith("http://") or source.startswith("https://"):
        req = urllib.request.Request(source, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read()
        img = Image.open(io.BytesIO(data))
    else:
        img = Image.open(source)
    return img.convert("RGBA")


def make_square(img: Image.Image) -> Image.Image:
    """Recorta/centra la imagen en un lienzo cuadrado transparente."""
    w, h = img.size
    side = max(w, h)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, ((side - w) // 2, (side - h) // 2), img)
    return canvas


def circle_mask(img: Image.Image) -> Image.Image:
    """Aplica una máscara circular (para *_round.webp)."""
    size = img.size
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def make_monochrome(img: Image.Image) -> Image.Image:
    """Genera la silueta monocromática que exige el 'themed icon' de Android 13+."""
    gray = ImageOps.grayscale(img)
    alpha = img.split()[-1]
    mono = Image.new("RGBA", img.size, (255, 255, 255, 0))
    white = Image.new("RGBA", img.size, (255, 255, 255, 255))
    mono.paste(white, (0, 0), alpha)
    return mono


def save_webp(img: Image.Image, path: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "WEBP", lossless=True)
    print(f"  -> {path}")


def main():
    if len(sys.argv) < 3:
        print("Uso: generate_icons.py <icono_url_o_path> <ruta_res>")
        sys.exit(1)

    source, res_dir = sys.argv[1], sys.argv[2]
    print(f"Descargando/leyendo icono desde: {source}")
    src = make_square(load_source_image(source))

    # --- Icono legacy cuadrado y redondo ---
    for folder, size in LEGACY_SIZES.items():
        resized = src.resize((size, size), Image.LANCZOS)
        save_webp(resized, os.path.join(res_dir, folder, "logo_vexor.webp"))
        save_webp(circle_mask(resized), os.path.join(res_dir, folder, "logo_vexor_round.webp"))

    # --- Foreground adaptativo (icono a ~66% dentro del lienzo, con margen de seguridad) ---
    for folder, canvas_size in ADAPTIVE_SIZES.items():
        icon_size = int(canvas_size * 0.66)
        icon = src.resize((icon_size, icon_size), Image.LANCZOS)
        canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        offset = ((canvas_size - icon_size) // 2, (canvas_size - icon_size) // 2)
        canvas.paste(icon, offset, icon)
        save_webp(canvas, os.path.join(res_dir, folder, "logo_vexor_foreground.webp"))
        save_webp(make_monochrome(canvas), os.path.join(res_dir, folder, "logo_vexor_monochrome.webp"))

    print("Iconos generados correctamente.")


if __name__ == "__main__":
    main()
