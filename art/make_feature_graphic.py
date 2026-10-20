"""Render the 1024x500 Play Store feature graphic (en-US and ja-JP)."""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'store', 'graphics')
W, H = 1024, 500
TOP, MID, BOT = (0x54, 0x6E, 0x7A), (0x37, 0x47, 0x4F), (0x1B, 0x24, 0x29)
FONTS = 'C:/Windows/Fonts/'
LATIN = FONTS + 'segoeuib.ttf'
JA = FONTS + 'YuGothB.ttc'
JA_REG = FONTS + 'YuGothM.ttc'

TEXT = {
    'en-US': ('Kotodo', 'A todo list that never\ntouches the network.', LATIN, LATIN),
    'ja-JP': ('Kotodo', '通信しない、\nオフラインTodoリスト。', LATIN, JA),
}


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def background():
    img = Image.new('RGB', (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            t = (x / W + y / H) / 2
            px[x, y] = lerp(TOP, MID, t / 0.55) if t < 0.55 else lerp(MID, BOT, (t - 0.55) / 0.45)
    return img


def main():
    icon = Image.open(os.path.join(HERE, 'play_store_icon.png')).convert('RGBA').resize((300, 300), Image.LANCZOS)
    mask = Image.new('L', (300, 300), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 299, 299), radius=66, fill=255)
    for locale, (title, tagline, title_font, tag_font) in TEXT.items():
        img = background()
        img.paste(icon, (90, 100), mask)
        d = ImageDraw.Draw(img)
        d.text((450, 120), title, font=ImageFont.truetype(title_font, 104), fill='white')
        d.multiline_text((454, 284), tagline, font=ImageFont.truetype(tag_font, 40),
                         fill=(0xCF, 0xD8, 0xDC), spacing=12)
        d.rectangle((454, 256, 454 + 120, 262), fill=(0xE6, 0x4A, 0x19))
        path = os.path.join(OUT, 'feature-graphic-' + locale + '.png')
        img.save(path)
        print(path)


if __name__ == '__main__':
    main()
