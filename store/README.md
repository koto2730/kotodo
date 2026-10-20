# Google Play store assets / ストア掲載素材

Everything Play Console asks for in the main store listing, kept in the repo so it is
versioned with the app. Copy-paste or upload from here.

| Play Console field | File | Limit |
|---|---|---|
| App name | `listing/<locale>/title.txt` | 30 chars |
| Short description | `listing/<locale>/short_description.txt` | 80 chars |
| Full description | `listing/<locale>/full_description.txt` | 4000 chars |
| App icon | [`../art/play_store_icon.png`](../art/play_store_icon.png) | 512×512 PNG |
| Feature graphic | `graphics/feature-graphic-<locale>.png` | 1024×500 |
| Phone screenshots | `graphics/phone-<locale>/*.png` | 1080×2160, 2–8 images |
| Privacy policy | [`privacy-policy.md`](privacy-policy.md) | needs a public URL |

Locales: `en-US` (default) and `ja-JP`.

## Regenerating

- Icon: `python art/make_play_store_icon.py`
- Feature graphic: `python art/make_feature_graphic.py` (needs Pillow and the Windows
  Segoe UI / Yu Gothic fonts; adjust the font paths on other systems)
- Screenshots: taken on a 1080×2160 emulator (`adb shell wm size 1080x2160`; Play rejects
  screenshots whose long side exceeds twice the short side) after importing a CSV with
  dates around "today" through *Import CSV…*.

## Suggested metadata

- Category: Productivity — Contact email: your own — Free, no ads, no in-app purchases
- Data safety: collects no data, shares no data (the app has no INTERNET permission)
- Content rating questionnaire: no user-generated content shared, no violence etc.
