# EstateDesk — Real Estate CRM for Android

A production-quality, offline-first real estate CRM built as a **native Android application**
(Kotlin, no Gradle, no third-party app dependencies). It is a separate product from the
Next.js web app in this repository and lives entirely in `android/`.

## What it is

EstateDesk covers the full sales lifecycle of a real estate agent/agency:

| Area | Features |
|---|---|
| **Dashboard** | Follow-ups today, overdue, meetings, viewings, business KPIs, pipeline snapshot, recent leads |
| **Contacts** | Profiles, multiple phones/WhatsApp, classification (Buyer/Seller/Investor/Renter/Landlord), status, temperature, budget, requirements, tags, custom fields, timeline |
| **Leads** | Requirement capture, budget, location, intent, sources, priority, **rule-based lead scoring**, kanban-style pipeline with 12 customizable stages, probability, follow-ups |
| **Properties** | Listings with price/currency, size units (Marla/Kanal/SqFt…), beds/baths, condition, owner, photos (gallery picker), statuses (Available…Off Market), documents |
| **Property matching** | Deterministic matcher: budget/location/type/intent/beds/baths/size → transparent **% score with ✓/△/✗ reasons** (no fake AI) |
| **Deals** | Buyer/seller/property, value, commission % + amount + received/pending, stages, closing, **commission tracking** |
| **Tasks & follow-ups** | Kinds (Task/Follow-up/Call/Meeting/Viewing/Email), due date+time, priority, recurrence, reminders, snooze, **Follow Up Today** workbench |
| **Activities** | Every call/note/viewing/status change/deal update lands in per-record timelines |
| **Finances** | Income & expenses, categories, related deals, monthly totals |
| **Search** | Global FTS4 search across contacts/leads/properties/deals/tasks with prefix + fuzzy fallback |
| **Analytics** | Leads over time, sources donut, pipeline distribution, conversion funnel, revenue, property types, top areas, activity counts — all from real stored data, hand-drawn canvas charts |
| **Data** | CSV import wizard (column mapping, preview, validation, duplicate detection), CSV export, **backup/restore** (zip incl. photos + documents), Recycle Bin with restore, optional sample dataset |
| **Security & privacy** | 100% offline/local by default, app lock (PBKDF2 PIN + device biometric via Keyguard), no analytics/tracking, no secrets |
| **UX** | Light/dark/system theme, one design system, bottom navigation, FAB quick-add, empty/error/loading states, paginated lists for 10k+ rows, local notifications with done/snooze actions |

No fake buttons: everything above is implemented against a real SQLite database that
persists across restarts.

## Install the APK

A pre-built, signed APK is at `android/dist/EstateDesk-1.0.0.apk`.

**On a phone (no computer):**
1. Copy `EstateDesk-1.0.0.apk` to the device (USB, Bluetooth, cloud drive…).
2. Open the file — Android will ask to allow installs from that app ("Install unknown apps").
3. Tap **Install**. The app appears as **EstateDesk**.

**Via adb:**
```bash
adb install -r android/dist/EstateDesk-1.0.0.apk
```

The APK is signed (APK Signature Scheme v2 + JAR v1) with the project key at
`android/keystore/release.pem`. **Keep that key** if you publish updates — it is the
app's signing identity. It is git-ignored on purpose; re-running the build regenerates
one if it is missing (which would require uninstalling the old APK first).

## Build from source

The build uses a tiny, fully offline toolchain (no Gradle, no Google SDK downloads):

```bash
sudo bash android/tools/setup_toolchain.sh   # installs JDK17 + kotlinc + aapt2 + d8 + android.jar
bash android/build.sh                        # produces dist/EstateDesk-1.0.0.apk
```

Pipeline: `aapt2 compile/link` (resources + R) → `kotlinc` (Kotlin → JVM classes, incl.
converted `R.kt`) → `d8` (dex) → Python packager → `sign_apk.py` (v1+v2 signing) → verify.

Business-logic smoke tests run on the plain JVM:

```bash
# in android/ (uses kotlinc from the toolchain)
export JAVA_HOME=$(/opt/toolchain/venv/bin/python -c "import jdk4py; print(jdk4py.JAVA_HOME)")
KOTLINC=/opt/toolchain/kotlinc/bin/kotlinc
CP=/opt/toolchain/sdk/android-35/android.jar:/opt/toolchain/kotlinc/lib/kotlin-stdlib.jar
mkdir -p /tmp/st && $KOTLINC -nowarn -jvm-target 1.8 -classpath $CP -d /tmp/st \
  app/src/main/kotlin/com/estatedesk/crm/core/Csv.kt \
  app/src/main/kotlin/com/estatedesk/crm/core/Security.kt \
  app/src/main/kotlin/com/estatedesk/crm/data/Models.kt \
  app/src/main/kotlin/com/estatedesk/crm/domain/MatchingEngine.kt \
  app/src/main/kotlin/com/estatedesk/crm/domain/LeadScorer.kt \
  tests/DomainSmokeTest.kt
java -cp /tmp/st:$CP DomainSmokeTestKt
```

## Architecture

```
app/src/main/kotlin/com/estatedesk/crm/
├── App.kt                    # Application: theme, DB, notification channels, reminders
├── core/                     # Async, UiKit (design system), Palette (theming), Fmt, Csv,
│                             # Security (PBKDF2), Files, FilesProvider, BaseActivity, Bio
├── data/                     # SQLite: Db (schema + migrations), Store (settings/pipeline/
│                             # currency), DAOs (Contact/Lead/Property/Deal/Task/Activity/
│                             # Finance/Document/Custom/Search FTS/Stats)
├── domain/                   # MatchingEngine, LeadScorer, ImportExport, BackupService,
│                             # MessageTemplates, PipelineConfig, SampleData
├── ui/screens/               # 34 activities (dashboard, lists, forms, details, settings)
├── ui/widgets/               # BottomNav, PagedList (pagination), Rows, Dialogs
├── ui/charts/                # Canvas charts (bar/line/donut/hbar)
├── receivers/                # ReminderReceiver, TaskActionReceiver (notification buttons), BootReceiver
└── services/                 # Notifier, ReminderScheduler (AlarmManager)
```

Design decisions:

- **Single-threaded DB access** (`Async.db`) serializes SQLite access — no coroutines or
  ORM, no dependency risk; WAL journaling for durability.
- **Soft deletes everywhere** → Recycle Bin with 30-day purge; destructive actions are
  confirmed and reversible.
- **Pagination everywhere** (`PagedList` + LIMIT/OFFSET + indexed queries) so 10k+ rows stay smooth.
- **Money as minor units (Long)**, configurable currency (PKR default, 9 currencies), never floats.
- **Deterministic "AI-free" intelligence**: matching, scoring, next-step suggestions and
  message templates are rule-based over real data, with explanations.
- **Local notifications** with Done/Snooze actions; alarms re-armed on boot and app start.
- All strings externalized in `res/values/strings.xml` (i18n-ready, English first).

## Status & QA notes

- Compiles, dexes, links and signs cleanly; APK structure verified (manifest, resources,
  all 70+ classes in dex).
- Domain logic covered by `tests/DomainSmokeTest.kt` (16 checks: matching, requirement
  parsing, scoring, CSV edge cases, PIN hashing) — all green.
- On-device UI pass (visual polish, edge-case interactions, notification behavior) should
  be run on a real device/emulator — this sandbox has neither.

## License / privacy

The app is fully offline and collects nothing. See the repository LICENSE.
