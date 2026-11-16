# Kotodo Portable Domain & Data Specification (iOS / Cross-Platform Reference)

This document captures the platform-independent data schema, date/repeat arithmetic, notification rules, and CSV interchange format of **Kotodo** so that an iOS edition (Swift / SwiftUI / SwiftData or SQLite) or a shared Kotlin Multiplatform (KMP) module can maintain 100% behavioral and data compatibility with the Android app.

---

## 1. Core Principles

1. **Zero Network Access**: The app must not request network entitlements or transmit user data off-device. All persistence and reminders are strictly local.
2. **Local Calendar Semantics**: All scheduled dates (`startDate`, `dueDate`, `repeatEndDate`, `completedDate`) are timezone-free calendar dates (`LocalDate` on Android, `DateComponents(year:month:day:)` / epoch day `Int64` on iOS) evaluated against the device's current local timezone.

---

## 2. Data Model (`todos` Table)

Dates are persisted as **Epoch Days** (`days since 1970-01-01`, ISO-8601 proleptic Gregorian calendar) so SQL comparisons (`<=`, `=`) are fast and timezone-invariant.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | — | Unique identifier |
| `title` | `TEXT NOT NULL` | `""` | Task title (required) |
| `description` | `TEXT` | `NULL` | Optional multiline note |
| `priority` | `INTEGER NOT NULL` | `2` (`Middle`) | `0`: Highest, `1`: High, `2`: Middle, `3`: Low, `4`: Lowest |
| `groupName` | `TEXT` | `NULL` | Free-text group / category name |
| `repeat` | `INTEGER NOT NULL` | `0` | `1` if repeating is enabled |
| `repeatType` | `TEXT NOT NULL` | `"NONE"` | `"NONE"`, `"DAILY"`, `"WEEKLY"`, `"MONTHLY"`, `"YEARLY"` |
| `repeatInterval` | `INTEGER NOT NULL` | `1` | Cycle step $N \ge 1$ |
| `weekOfMonth` | `INTEGER NOT NULL` | `0` | `0`: by day-of-month (`monthRule`), `1..4`: 1st–4th weekday, `5`: last weekday of month |
| `weekRule` | `INTEGER NOT NULL` | `0` | 7-bit mask (`bit 0` = Monday … `bit 6` = Sunday) |
| `monthRule` | `INTEGER NOT NULL` | `0` | 31-bit mask (`bit 0` = 1st … `bit 30` = 31st) |
| `yearRule` | `INTEGER NOT NULL` | `0` | 12-bit mask (`bit 0` = January … `bit 11` = December) |
| `repeatEndDate` | `INTEGER` | `NULL` | Epoch day after which no follow-up is spawned |
| `startDate` | `INTEGER` | `NULL` | Epoch day when the todo begins showing on the Today list |
| `dueDate` | `INTEGER` | `NULL` | Epoch day when the todo is due |
| `notify` | `INTEGER NOT NULL` | `0` | `1` if local reminder notification is enabled |
| `notifyMinuteOfDay` | `INTEGER NOT NULL` | `540` | Minutes from `00:00` (`540` = `09:00`, clamped to `0..1439`) |
| `completedDate` | `INTEGER` | `NULL` | Epoch day when the todo was completed |
| `completed` | `INTEGER NOT NULL` | `0` | `1` if completed |
| `followUpCreated` | `INTEGER NOT NULL` | `0` | `1` once a repeating todo has spawned its next occurrence |
| `parentTodoId` | `INTEGER` | `NULL` | `id` of the parent todo that spawned this follow-up |
| `createdAt` | `INTEGER NOT NULL` | Epoch ms | Creation timestamp for tie-breaker sorting |

---

## 3. Visibility & Listing Rules

### 3.1 Day List ("当日" / Today Mode for Reference Date $D$)
A todo is visible on date $D$ if and only if:
- **Open (`completed == false`)**:
  $$(\text{startDate} \le D) \lor (\text{dueDate} \le D) \lor (\text{startDate} = \text{NULL} \land \text{dueDate} = \text{NULL})$$
- **Completed (`completed == true`)**:
  $$\text{completedDate} = D$$
  *(Completed items remain visible with a strike-through for the rest of the day they were completed, then drop off the next day.)*

### 3.2 Overdue Highlighting
An open todo is overdue on reference date $D$ when $\text{dueDate} \ne \text{NULL} \land \text{dueDate} < D$.

---

## 4. Repeat Arithmetic (`RepeatRule`)

### 4.1 Anchor Date & Lead Time
- `anchorDate = dueDate ?? startDate ?? today`
- When both `startDate` and `dueDate` are set, `leadDays = dueDate.toEpochDay() - startDate.toEpochDay()`.
- When a repeating todo is completed on `today`, the next occurrence's anchor date $A_{\text{next}}$ is advanced until $A_{\text{next}} > \max(\text{anchorDate}, \text{today})$.
  - If the todo is more than 500 cycles behind `today`, the cursor is fast-forwarded close to `today` in a single division step before stepping cycle-by-cycle.
- If `repeatEndDate != null && A_next > repeatEndDate`, no follow-up is created.

### 4.2 Cycle Rules
- **DAILY**: $A_{\text{next}} = \text{from} + \text{interval}\text{ days}$.
- **WEEKLY**:
  - Active weekdays come from `weekRule` (or `from.dayOfWeek` if `weekRule == 0`).
  - Within the same ISO week (Monday–Sunday) as `from`, any selected weekday strictly after `from` is chosen; otherwise jump to the Monday of `weekStart + interval weeks` and pick the first selected weekday.
- **MONTHLY**:
  - **Day-of-month mode (`weekOfMonth == 0`)**: Selected days come from `monthRule` (or `[from.dayOfMonth]` if `monthRule == 0`). Any selected day strictly after `from` in the same month is chosen; otherwise advance by `interval` months. Days `29..31` are clamped to `YearMonth.lengthOfMonth()` in shorter months.
  - **Nth-weekday mode (`weekOfMonth in 1..5`)**: Selected weekdays come from `weekRule` (or `[from.dayOfWeek]` if `weekRule == 0`). For `1..4`, resolve the $n$-th weekday of the target month (skipping months where that weekday does not have an $n$-th occurrence); for `5` (`WEEK_OF_MONTH_LAST`), resolve the last occurrence of that weekday in the target month.
- **YEARLY**:
  - Target months come from `yearRule` (or `[from.monthValue]` if `yearRule == 0`).
  - Within each target month, candidate days are resolved using the same day-of-month (`weekOfMonth == 0`) or nth-weekday (`weekOfMonth in 1..5`) rule as `MONTHLY`. If all candidates in `from.year` have passed, advance by `interval` years.

---

## 5. Local Notifications

- **Trigger Date**: `notifyDate = min(startDate, dueDate)` (whichever is set, or the earlier of the two).
- **Trigger Time**: `notifyDate` at `notifyMinuteOfDay` in the device's local timezone.
- **Suppression Rules**:
  - Never fire if `completed == true` or `notify == false`.
  - Never fire on `today` if the parent repeating todo (`parentTodoId`) was already completed on `today`.
- **iOS Mapping**: Use `UNUserNotificationCenter` with `UNCalendarNotificationTrigger(dateMatching: repeats: false)`. Because iOS limits an app to 64 pending local notifications, schedule the nearest 60 upcoming reminders within the 120-day horizon and refresh the queue on app foreground (`scenePhase == .active`) and after database mutations.

---

## 6. CSV Interchange Format

- **Encoding**: UTF-8 with a leading UTF-8 BOM (`U+FEFF`) so spreadsheet apps on Windows, macOS, and iOS open Japanese text without garbling.
- **Formula Injection Protection**: Cells starting with `=`, `+`, `-`, `@`, or `\t` are prefixed with `'` on export and unprefixed on import.
- **Columns**:
  ```csv
  title,description,priority,group,start_date,due_date,repeat,repeat_type,repeat_interval,week_rule,month_rule,year_rule,repeat_end_date,notify,notify_time,completed,completed_date
  ```
- **Nth-Weekday Encoding in `month_rule`**:
  - When `weekOfMonth == 0`, `month_rule` holds `|`-separated day numbers (`1|15`).
  - When `weekOfMonth in 1..4`, `month_rule` encodes `@1`..`@4` (`@L` for `5` = last), while `week_rule` holds the weekday tokens (`MON|FRI`). The importer also accepts combined tokens such as `3MON` or `LFRI` in `month_rule` or a separate `week_of_month` column.
