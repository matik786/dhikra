# Dhikra — Android app

Native Android app (Kotlin, zero external dependencies). Package `com.dhikra.app`.

## Features
- **Themes**: Burgundy Gold (default) and Emerald Night, switchable in
  Settings → Appearance and applied app-wide (activities, widget, verse cards,
  wallpapers). Persisted across restarts.
- **Home-screen widget**: shows the ayah card in the active theme (Amiri Quran
  Arabic, transliteration, Saheeh International translation, theme kicker).
  Tap the card to open the verse detail screen; the small round gold button
  advances to the next verse. Auto-rotates every 3 hours via AlarmManager
  (rescheduled on boot).
- **Verse detail screen**: Arabic, transliteration, translation, reference and
  theme, with Next verse / Set as wallpaper / Open on quran.com actions.
- **Main app**: verse card preview, next-prayer countdown header, Next verse,
  **Set as wallpaper** (renders at exact screen size), **Add widget to home
  screen** (pin request), **Open on quran.com** for the current verse,
  **Prayer reminder settings**.
- **Prayer (namaz) reminders**: notification X minutes before each prayer.
  - Source precedence per prayer: manual HH:MM override → mosque schedule
    cache (same day) → calculated from device location (or manual lat/long),
    5 methods (MWL default), Hanafi/Shafi'i Asr — Hanafi by default.
  - Calculation verified against Aladhan API (within 1 min for Bellevue WA).
  - **Mosque auto-detect**: pluggable `MosqueSource` interface
    (`MosqueSource.kt`). v1 ships with no providers — there is currently no
    clean, licensed mosque-iqamah API (MasjidTimes: no public API; Mawaqit:
    unofficial + Europe-centric; MasjidNow: no API) — so the app falls back
    gracefully to manual mosque entry + calculated times. The UI always shows
    the active source and last-update time; last valid schedule is cached.
    Never fabricates mosque data.
  - "Detect nearby mosques" + nearby list (populated only by real sources),
    manual mosque name + per-prayer Iqamah overrides, lead-time presets
    (5/10/15/20/30 min + custom), per-prayer toggles, notification-tone
    picker (Android system picker), vibration toggle. Rescheduled daily and
    on boot.
- **Offline Quran library**: the complete Quran (6,236 verses) is bundled in
  `app/src/main/assets/quran.json` (Uthmani Arabic, Pickthall English, English
  transliteration). `app/src/main/assets/rotation.json` defines the curated
  reminder rotation (572 verses across taqwa, mercy, gratitude, remembrance,
  hope, hereafter, patience, trust, prayer, charity, and forgiveness themes).
  The original 25 cards are preserved byte-identically in
  `app/src/main/assets/legacy_verses.json` and lead the rotation so a saved
  position keeps pointing at the same card.
- **Sources**: Quran text: Tanzil.net (CC BY 3.0). English translation:
  Marmaduke Pickthall (1930), public domain. Data files were obtained through
  the Al Quran Cloud API.
- **Original artwork only**: burgundy+gold open-book launcher icon
  (mipmap densities mdpi–xxxhdpi), no copyrighted assets.

## Project layout
Standard Android source under `app/src/main/` (manifest, Kotlin, res, assets).

## Rebuilding
Gradle does not work in this sandbox (Java loopback sockets are intercepted, which
breaks the Gradle daemon protocol), so the APK is built manually:

```bash
./build-manual.sh
```

Requires: JDK 17 at `~/jdk17`, Android SDK at `~/android-sdk`
(build-tools 34.0.0, platform android-34), Kotlin compiler at `~/kotlin-compiler`.
Output: `/tmp/apkbuild/out/dhikra.apk` (zipaligned, signed with debug key).

To open in Android Studio instead: the `app/build.gradle.kts`, `settings.gradle.kts`
are present but the app was simplified to zero dependencies, so they can be trimmed.
