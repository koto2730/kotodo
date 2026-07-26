# Kotodo

A todo app for Android that never touches the network.
ネットワークを一切使わない、デバイスローカルなAndroid Todoアプリ。

Kotodo does not declare `android.permission.INTERNET`, so it is technically
incapable of sending your data anywhere. Everything lives in one SQLite file on
the device.

Kotodo は `android.permission.INTERNET` を宣言していないため、そもそも通信できません。
データは端末上の SQLite ファイル 1 つだけに保存されます。

## Features / 機能

- **Today's list** — on launch, Kotodo reads the device's local date and shows every
  todo whose 開始予定日 or 完了予定日 has arrived, plus every todo with no dates at all.
  Forget to finish something and it simply keeps showing up.
- **Any other day** — step back and forward a day at a time, or jump to a date, to
  review what was or will be scheduled.
- **Filter and sort** — by priority, group, and date state (overdue / due today /
  undated / upcoming); sort by priority, dates, title, group or creation order.
- **Full CRUD** — tap a row to edit, long-press to delete (with undo), `+` to add.
- **Tick it off** — the checkbox on the left sets 完了日 and 完了フラグ and draws a
  line through the title.
- **Repeats** — daily / weekly / monthly / yearly with an interval, plus optional
  weekday, day-of-month and month selection. Completing a repeating todo creates the
  next one with the dates shifted; 繰り返し終了日 stops the chain.
- **Local reminders** — an optional notification at a per-todo time, delivered by
  `AlarmManager` on the device. No push service involved.
- **CSV import / export** — via the system file picker. The importer accepts a
  Markdown checklist prefix (`- [ ]` / `- [x]`) on the title and ignores it.

## Design / デザイン

The icon is the app's defining gesture: a tick with a strike-through line under it,
the same mark the list draws through a title when you complete a todo. It ships as
an adaptive icon (with a monochrome layer for themed icons) and reuses the same
shape as the notification icon.

アイコンは「完了するとタイトルに線が引かれる」というこのアプリの動きをそのまま図案化したものです。アダプティブアイコン（テーマアイコン用のモノクロレイヤー付き）で、通知アイコンにも同じ図形を使っています。

The chrome is 鉄紺 (slate) and deliberately near-neutral, so the priority stripes
stay the most colourful thing on screen. A single 朱 (vermilion) accent marks the
things you act on — the add button, checkboxes and switches.

| Role | Light | Dark |
|---|---|---|
| App bar / primary | `#37474F` 鉄紺 | `#263238` |
| Status bar | `#263238` | `#1B2429` |
| Accent (FAB, checkbox) | `#E64A19` 朱 | `#FF8A65` |
| Priority: 最高 / 高 / 中 / 低 / 最低 | `#D32F2F` `#F9A825` `#1976D2` `#689F38` `#9E9E9E` | lighter variants |
| Overdue text | `#D32F2F` | `#EF5350` |

`art/play_store_icon.png` is the 512×512 store listing icon, generated from the
same geometry by `art/make_play_store_icon.py` — re-run it after editing the
launcher vectors so the two never drift apart.

## Languages / 表示言語

Kotodo ships English (`values/`) and Japanese (`values-ja/`) resources and follows
the **device language** automatically — a Japanese device shows Japanese, everything
else falls back to English. Dates, weekday and month names come from `java.time`
with the active locale, so `2026/07/25` and `7月` on a Japanese device become
`Jul 25, 2026` and `Jul` on an English one.

On Android 13+ the app also declares a `localeConfig`, so the language can be
overridden per app under **Settings › Apps › Kotodo › Language** without changing
the whole device. To try it from a terminal:

```bash
adb shell cmd locale set-app-locales com.mugime.kotodo --user current --locales ja-JP
```

## Requirements / 動作環境

| | |
|---|---|
| Language | Java |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 36 |
| Persistence | SQLite via AndroidX Room |
| Build | Gradle + Android Gradle Plugin 8.13 |

## Build / ビルド

```bash
./gradlew assembleDebug
```

Run the unit tests (repeat-rule arithmetic and CSV round trips):

```bash
./gradlew testDebugUnitTest
```

## CI/CD and releases / CI/CDとリリース

Every push to `main` and every PR runs unit tests, lint, and a debug build via
[`.github/workflows/ci.yml`](.github/workflows/ci.yml). It needs no secrets.

Pushing a tag like `v1.0.0` builds a **signed** release APK and attaches it to a
**draft** [GitHub Release](../../releases), via
[`.github/workflows/release.yml`](.github/workflows/release.yml) — that's the
supported way to distribute the app; there is no Play Store listing. The release
is a draft on purpose: nothing is visible to anyone until you open it on the
Releases page, check the attached APK, and click **Publish release**.

### Building a signed release locally / ローカルでの署名ビルド

Signing reads from `keystore.properties` at the repo root (gitignored — never
commit it or the keystore file itself):

```bash
cp keystore.properties.example keystore.properties
# then edit keystore.properties to point at your own .jks/.keystore file
./gradlew assembleRelease
```

Without `keystore.properties`, `assembleRelease` still runs but produces an
**unsigned** APK — fine for checking the build, not for distributing.

### One-time setup: signing secrets for CI / CIの署名シークレット設定(初回のみ)

The release workflow needs four repository secrets. Set these from your own
terminal (values never need to pass through anyone else) — either via
`gh secret set`, run from the repo root:

```bash
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
base64 -w0 /path/to/your/release.keystore | gh secret set KEYSTORE_BASE64
```

(On Windows PowerShell, replace the last line with:
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\release.keystore")) | gh secret set KEYSTORE_BASE64`)

...or via the GitHub web UI under **Settings › Secrets and variables › Actions**.

### Cutting a release / リリースの切り方

```bash
git tag v1.0.0
git push origin v1.0.0
```

That triggers the release workflow; a **draft** release with the signed APK shows
up on the [Releases](../../releases) page a few minutes later — review it and
click **Publish release** when you're ready for it to go public. Bump
`versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts)
before tagging.

## Data model / データモデル

One table, `todos`, holding:

| Field | 項目 | Notes |
|---|---|---|
| `title` | タイトル | required |
| `description` | 説明 | |
| `priority` | プライオリティ | Highest / High / Middle / Low / Lowest |
| `groupName` | グループ | free text, existing values are offered as suggestions |
| `repeat` | 繰り返し有り | |
| `repeatType` | 繰り返しの単位 | `DAILY` / `WEEKLY` / `MONTHLY` / `YEARLY` |
| `repeatInterval` | 間隔 | every N units |
| `weekRule` | 曜日指定 | bit mask, Monday = bit 0 |
| `monthRule` | 日にち指定 | bit mask, day 1 = bit 0 |
| `yearRule` | 月指定 | bit mask, January = bit 0 |
| `repeatEndDate` | 繰り返し終了日 | no occurrence is created past this date |
| `startDate` | 開始予定日 | |
| `dueDate` | 完了予定日 | |
| `notify` | 通知有り | |
| `notifyMinuteOfDay` | 通知時刻 | minutes from midnight, default 09:00 |
| `completedDate` | 完了日 | |
| `completed` | 完了フラグ | |

Dates are stored as epoch days, so they stay comparable in SQL and independent of
time zone.

### Listing rule / 表示ルール

A todo appears on the list for day *D* when:

```
(開始予定日 <= D) OR (完了予定日 <= D) OR (both are unset)
```

Completed todos are shown on their 完了日 only, so the strike-through stays visible
for the rest of the day and then the item drops off.

### Repeat rule / 繰り返しルール

The cycle is measured from 完了予定日 (or 開始予定日 when there is no due date).
An empty day selection means "same day as the anchor date", so a weekly todo anchored
on a Wednesday repeats on Wednesdays until you pick specific weekdays. A day of month
that does not exist in the target month is clamped to the last day, so "the 31st,
monthly" lands on 28 February.

When a repeating todo is completed both scheduled dates shift by the same number of
days, preserving the lead time between them. If the todo was long overdue the search
keeps advancing until the next occurrence lands in the future.

## CSV format / CSV形式

Export writes this header:

```
title,description,priority,group,start_date,due_date,repeat,repeat_type,repeat_interval,week_rule,month_rule,year_rule,repeat_end_date,notify,notify_time,completed,completed_date
```

Import is deliberately forgiving:

- column order is free and columns may be missing;
- headers match English and Japanese aliases case-insensitively, ignoring spaces,
  underscores and hyphens (`due_date`, `Due Date`, `期限`, `完了予定日` all work);
- a file with no recognisable header is read as one title per line;
- a checkbox prefix on the title is stripped, and `[x]` marks the item complete when
  the file carries no explicit completion column.

So this is a valid import file:

```
- [ ] 牛乳を買う
- [x] 家賃を払う
```

And so is this:

```csv
タイトル,期限,優先度,グループ,繰り返し種別,曜日
週次レポート,2026-07-31,high,仕事,weekly,MON|WED
ゴミ出し,2026-07-26,middle,家事,weekly,火・金
```

Dates accept `2026-07-25`, `2026/7/25`, `2026.7.25` and `20260725`.

[`sample.csv`](sample.csv) in the repository root is a ready-to-import example
covering every field, including a checkbox-prefixed row.

## Project layout / 構成

```
app/src/main/java/com/mugime/kotodo/
├── KotodoApp.java            application entry point, notification channel
├── MainActivity.java         drawer + navigation host
├── data/                     Room database, DAO, repository, type converters
├── elements/                 Todo entity, Priority, RepeatType
├── notify/                   AlarmManager scheduling, alarm and boot receivers
├── ui/list/                  list screen, filter/sort sheet, adapter
├── ui/edit/                  create/edit form
└── utils/                    date helpers, repeat arithmetic, CSV
```

## Notes / 注意

- Reminders are not replayed. If a reminder time has already passed when the alarms
  are (re)armed, it is skipped — the todo is on the list anyway.
- On Android 12+ Kotodo asks for exact alarms and falls back to a ten-minute window
  when that is not granted.
- Un-completing a repeating todo does not remove the follow-up it created; delete it
  by hand if you do not want it.

## License

[MIT](LICENSE)
