# Nibble

A Compose Multiplatform reading app that serves books in two‑minute **nibbles** —
sentence‑aligned chunks sized to a real reading rate — while keeping full continuous
reading and narrated audio one tap away. Built from the design file and the
*Nibble — Compose Multiplatform Dev Guide* (Claude Design).

Every surface reads and writes one canonical **reading position**, so nibble mode,
chapter mode, and audio are three renderers over the same offset — never three bookmarks.

## Status

A working Android app (driven end‑to‑end on an emulator), not a mock:

- **Real content.** Four full public‑domain books (Gutenberg) are bundled and ingested
  into real chapter spines and segmented into real nibbles (Jekyll 57, Dorian 180,
  Time Machine 74, Metamorphosis 46). All catalog counts/durations are derived.
- **EPUB import.** Pick an `.epub` → unzip → OPF spine → XHTML→text → segment → it
  appears on the shelf and reads (verified with *Alice in Wonderland*). DRM is rejected.
- **Persistence.** Onboarding, reading position, sessions, and prefs are saved to JSON
  and survive process death (verified: kill + relaunch resumes at the same place).
- **Real streaks/stats.** Derived from real `Session` rows with real timestamps in the
  device zone — a new user starts at 0 and builds up (a day counts at ≥60s, per §10).
- **The sarcastic voice (§11).** Copy is typed data with many DRY variants per surface,
  rotated so a roast never repeats twice running (rotation persisted), a Dry|Neutral
  toggle, PLAIN tone for errors/empty, and dormancy escalation (day 1/3/7/8+, capped).
- **On-device generation (§12).** An `OnDeviceModel` capability ladder with a real,
  private, no-network CPU engine: the reader's "Who?" gloss (grounded strictly before
  the reader's position — spoiler-safe — with one dry aside), a resume recap, and
  stop-point summaries. Nudge lines are *selected* from authored templates, never
  free-written (roast-safe). A neural backend (Gemini Nano / MediaPipe) plugs in at the
  NPU tier; the CPU engine is the always-available fallback.
- **Notifications & nudges (§9).** A real lockscreen drip (BigText with the actual book
  excerpt + "Finish it"/"Listen" deep-link actions), a WorkManager streak reminder with
  the nagging budget (≤2/day, never 23:00–07:00, suppressed if read today) and escalating
  copy, POST_NOTIFICATIONS handling, and deep links that open the reader at the right spot.
- **Saved lines.** The heart in the reader (and the feed's "♥ Save line" pill) keeps the
  sentence under the marker; a Saved Lines screen lists the kept quotes with share/delete,
  and tapping one reopens the book at that exact charOffset. Snapshotted text survives
  re-segmentation and imported-book deletion.
- **Full-text search.** The Shelf searches inside every book (case-insensitive scan of the
  ingested chapters, snippets with the match highlighted); tapping a hit jumps the reader
  straight to that position — same canonical-position write path as everything else.
- **Chapters list.** The reader controls sheet lists the spine; tap to jump. The current
  chapter is marked.
- **Real audio controls.** Narration speed (0.8–2×, persisted, applied mid-sentence),
  a sleep timer (10/20/30 min — pauses in place), restart-this-sentence, and real
  elapsed/remaining estimates (rate-adjusted). TTS utterances are chunked under the
  engine's ~4k char cap (a whole chapter in one `speak()` call is silently rejected).
- **Daily goal.** Minutes-per-day goal (5/10/15/20, set in You), a live progress bar on
  Home, and a "Daily goal met" pill on the reward screen. Derived from real sessions.
- **Real badges.** Derived from session rows and the finished-book set (first nibble,
  longest streak, word milestones, night owl / early bird, finished books) — the mock
  pills are gone. No greyed-out guilt trips: unearned badges simply don't exist.
- **Finished books.** Finishing the last nibble marks the book (persisted set); the shelf
  shows it, the abandoned list excludes it, and badges count it.
- **Share.** Share a streak from Home and share any saved line — both through the system
  share sheet. Imported books can be removed from the shelf (their lines and position go
  with them). The screen stays awake while the reader is open.
- **System back.** One `BackHandler` (CMP `ui-backhandler`) routes the hardware/gesture
  back through the store, peeled in layers: controls sheet → audio → reader (records the
  session) → Reward-as-decline → non-Today tab → Today; only a true root (Today,
  onboarding start) lets the platform close the app.
- **EPUB covers & illustrations.** Import now extracts the real cover (EPUB3
  `properties="cover-image"`, EPUB2 `<meta name="cover">`, or name-hint fallback) and every
  inline `<img>`/`<image>` to `filesDir/book-images/<bookId>/`. Covers render on the shelf,
  home hero, and abandoned rows (typographic card stays the fallback); illustrations are
  recorded as `ImageAnchor(charOffset, path, alt)` BESIDE the text — plainText and its
  charOffsets stay canonical for position/TTS/AI — and render at their paragraph boundary
  in chapter mode, inside the owning nibble in nibble mode, with alt as caption +
  contentDescription. Deleting an import deletes its images. Decoding is a tiny
  expect/actual (downsampled BitmapFactory / okio + Skia) with a small cache; no image
  library dependency.
- **Accounts (optional).** Email/password auth for the coming web app, spoken directly to
  Firebase Auth's REST surface (`identitytoolkit` + `securetoken`) so the SAME common code
  serves Android, iOS and web — no platform Firebase SDK. Sign in / create / password
  reset / sign out live in You → Account; sessions persist and silently refresh; errors
  are mapped to PLAIN sentences. The reader never requires an account (local-first), and
  the app's only network permission exists for this. HTTP is a ~30-line expect/actual
  (`HttpURLConnection` / `NSURLSession`); a `fetch` actual slots in with the web target.
  **Setup:** put your Firebase project's Web API key in `AuthConfig.FIREBASE_API_KEY`
  (enable Email/Password in Firebase console → Authentication). For local dev, point
  `AuthConfig.EMULATOR_ORIGIN` at a Firebase Auth emulator (cleartext to `10.0.2.2` is
  allowed via `network_security_config.xml`).

The iOS target is wired (Compose entry point + `AVSpeechSynthesizer` narrator +
`IosPersistence`) but was not run this session.

Screens implemented, matching the design ids:

| Design | Screen | Notes |
|--------|--------|-------|
| 2a | `DiagnosisScreen` | attention tier → seeds nibble length |
| 2b | `PickBookScreen` | catalog grid, single selection |
| 2c | `HomeTodayScreen` | today's nibble, streak, sneak‑it‑in, abandoned shelf |
| 2d | Reader · nibble card | marker highlight + dimmed tail (`ProseText`) |
| 2e | `ReaderControlsSheet` | text scale, mode, audio handoff, nag toggle |
| 2f | `RewardScreen` | session summary, streak, "where you stopped" |
| 2g | `DamageReportScreen` | streak heat strip, stat tiles, histogram, badges |
| 2i | Reader · full chapter | |
| 2j | Reader · feed | one beat per card |
| 2k | Hands‑free audio | voice reads, text keeps up, transport, speed + sleep timer |
| 3x | Dark theme | same layouts, warm‑espresso palette |
| — | `You` | theme (System/Light/Dark/Sunset) + voice (Dry/Neutral) + daily goal |
| — | `SavedLinesScreen` | kept quotes; tap to reopen the book on that line |

## Architecture (maps to the dev guide)

Single Gradle module (`composeApp`) with packages mirroring the guide's module graph:

```
com.nibble.app
├── core/design    theme tokens + shared components (§3, §4)
├── core/domain    models, NibbleEngine.segment(), sample library (§6, §10)
├── core/audio     Narrator interface + platform impls (§8)
├── core/data      NibbleStore: StateFlow<AppState> + sealed Event (§5, §7)
└── feature/*      onboarding, home, reader, reward, stats
```

- **Theme (§3):** bespoke `NibbleColors` (light + dark, exact hex) + `NibbleType`
  (Newsreader for prose only, DM Sans for UI) behind CompositionLocals;
  `MaterialTheme` kept underneath only for ripple/selection.
- **Nibble engine (§6):** deterministic, ICU‑free sentence splitter; greedy
  accumulation to `wpm × 2` words, closing on paragraph ends within ±15%; sentences
  tile the chapter exactly. Runs once at load.
- **One position, three surfaces (§7):** `NibbleStore` holds one `ReadingPosition`
  per book; nibble index / audio word / scroll are all derived. Mode switches and the
  audio↔text handoff preserve `charOffset`.
- **Audio (§8):** `expect`‑style `Narrator` interface. Android = `TextToSpeech` +
  `UtteranceProgressListener.onRangeStart` (word‑level highlight follows the voice);
  iOS = `AVSpeechSynthesizer` + `willSpeakRangeOfSpeechString`.
- **Voice (§11):** copy is typed data (`Copy`/`Line`) with Dry/Plain tones and a
  Dry|Neutral toggle; error/empty copy never jokes.

## Build & run (Android)

Requires JDK 21 and the Android SDK (platform 36).

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.nibble.app/com.nibble.app.MainActivity
```

Toolchain: Gradle 8.14.3 (wrapper), AGP 8.11.1, Kotlin 2.2.0, Compose Multiplatform 1.8.2.

Deep links (from the guide's off‑app surfaces) are declared in the manifest:
`nibble://read/{bookId}`, `nibble://read/{bookId}?mode=audio`, `nibble://finish/{sessionId}`.

Testing on a foldable/tablet emulator? Emulate a phone with
`adb shell wm size 1080x2280 && adb shell wm density 420` (reset with `wm size reset`).

## What's real vs. still stubbed

Real: full‑book content + ingest, EPUB import, JSON persistence, real position/resume,
real sessions, real streak/stats/histogram, derived catalog counts, reader modes, the
TTS audio handoff, plus:

- **Tests (§14):** domain unit tests — segmentation tiling/determinism/abbreviations,
  streak derivation (current + longest), reading‑rate EMA, badge derivation, and
  full‑text search (`./gradlew :composeApp:testDebugUnitTest`, 27 tests).
- **Reading‑rate recalibration (§6):** EMA (α=0.2) of words÷active‑seconds; re‑segments
  when the rate drifts >15%, preserving position by char offset.
- **Accessibility (§13):** reduced‑motion (respects the OS setting), the nibble exposed
  to screen readers as one block with "nibble N of M", 48dp tap targets, sp type scaling.
- **On‑device AI cache (§12):** generated recaps/glosses persisted, so they're
  byte‑identical the second time.
- **Background audio (§8):** a foreground `MediaSession` service with an ongoing
  media notification + lockscreen transport controls; narration survives backgrounding.
- **Home‑screen widget (§9):** a Glance widget rendered from a shared snapshot written on
  every position change, deep‑linking into the reader.
- **Release build:** R8 + resource shrinking → a signed **2.7 MB** release APK (under the
  18 MB target), verified to run without breaking serialization.
- **Auto‑after‑sunset theme (§3).**
- **Large‑screen fallback:** the app is a phone layout (no tablet screens in v1, §1); on a
  large or **unfolded foldable** screen it caps content to ~560dp and centres it, so it
  stays clean instead of stretching. Verified on a OnePlus 15 and a Pixel 10 Pro Fold.

Still stubbed (architecture is faithful, so each can be swapped in without reshaping):

- **iOS:** the Kotlin (narrator, persistence, entry point) is written but there's no
  `iosApp/` Xcode project yet, so it's Android‑only in practice.
- **Neural on‑device model (§12):** the CPU extractive engine works everywhere; a real
  LLM (Gemini Nano / MediaPipe / Apple Foundation Models) needs NPU hardware or a ~1 GB
  model download and isn't wired — the abstraction and fallback are.
- **Persistence backend:** a JSON snapshot rather than SQLDelight (real, survives restart;
  not transactional).
- **Buddy/social (the deferred sync layer):** fake data omitted rather than faked.
- **Smaller:** iOS Live Activity; licensed catalog / network fetch; hands‑free
  activity‑recognition auto‑detect; EPUB cover‑image extraction; localization of the
  DRY copy. Navigation uses a small custom store instead of navigation‑compose/ViewModel;
  icons use Material Symbols with a hand‑drawn brand cookie.

## Design source

`Nibble - Compose Multiplatform Dev Guide.dc.html` and `Nibble - Book App.dc.html`
(Claude Design project `f680ef0a-…`).
# Nibble
