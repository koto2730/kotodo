"""Render the Play Store icon from the same geometry as the adaptive icon vectors."""
import sys
from PIL import Image, ImageDraw

SIZE = 512
SS = 4  # supersampling factor for the white mark

# Adaptive-icon canvas is 108x108; a launcher mask shows roughly the central 78.
CROP = 78.0
ORIGIN = (108.0 - CROP) / 2.0

# Gradient stops of ic_launcher_background.xml, along the (0,0)->(108,108) diagonal.
STOPS = [(0.00, (0x54, 0x6E, 0x7A)),
         (0.55, (0x37, 0x47, 0x4F)),
         (1.00, (0x1B, 0x24, 0x29))]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient_color(t):
    t = max(0.0, min(1.0, t))
    for i in range(len(STOPS) - 1):
        t0, c0 = STOPS[i]
        t1, c1 = STOPS[i + 1]
        if t <= t1:
            return lerp(c0, c1, (t - t0) / (t1 - t0))
    return STOPS[-1][1]


def build_background():
    img = Image.new('RGB', (SIZE, SIZE))
    pixels = img.load()
    scale = CROP / SIZE
    for fy in range(SIZE):
        y = ORIGIN + fy * scale
        for fx in range(SIZE):
            x = ORIGIN + fx * scale
            pixels[fx, fy] = gradient_color((x + y) / 216.0)
    return img


def to_px(point, scale):
    """108-space point -> supersampled pixel coordinates."""
    return ((point[0] - ORIGIN) * scale, (point[1] - ORIGIN) * scale)


def stroke(draw, points, width, scale):
    px = [to_px(p, scale) for p in points]
    w = width * scale
    draw.line(px, fill=255, width=round(w), joint='curve')
    # PIL has no round cap, so cap each end with a disc.
    for x, y in px:
        r = w / 2.0
        draw.ellipse((x - r, y - r, x + r, y + r), fill=255)


def build_mark():
    big = SIZE * SS
    scale = big / CROP
    mask = Image.new('L', (big, big), 0)
    draw = ImageDraw.Draw(mask)
    stroke(draw, [(39.5, 49), (49, 58.5), (70, 37.5)], 8, scale)
    stroke(draw, [(38, 71), (70, 71)], 6.5, scale)
    return mask.resize((SIZE, SIZE), Image.LANCZOS)


def main(out_path):
    icon = build_background()
    icon.paste(Image.new('RGB', (SIZE, SIZE), (255, 255, 255)), (0, 0), build_mark())
    icon.save(out_path, 'PNG')
    print('wrote', out_path, icon.size)


if __name__ == '__main__':
    main(sys.argv[1])
