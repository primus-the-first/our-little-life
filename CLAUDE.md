# Our Little Life

A cozy two-player life sim for Android. Two friends or partners each run one character on a shared plot: gather resources, build a house room by room, decorate it together, work, chat. Shared privately as an APK, not on the Play Store.

## Source documents

- PRD: https://claude.ai/code/artifact/8ae64bdd-5468-44af-8514-b0ce36bdf77d
- V1 build plan (phase checklists): https://claude.ai/code/artifact/0262b912-53d7-4b5d-b322-a047a4bfdcc3
- Design system (colours, type, components): https://claude.ai/artifact/EFPhptYF1UidJkHdoz2hLq
- Screen mockups: https://claude.ai/artifact/LC6ooxaBu6TKUxXesrgm9T

## Stack ($0 budget)

Kotlin, Jetpack Compose, Material 3. Firebase (Auth, Firestore, Storage, Cloud Functions, FCM) from Phase 2. Kenney CC0 art. No paid libraries or services.

## Structure

- `core/rules/` - pure Kotlin game rules (needs, day turns, actions, later building and careers). No Android imports. Every rule has unit tests.
- `app/` - the Android app: screens, theme, view models.
- Later: `core/data/`, `feature/*`, `functions/`, `content/` (game data as JSON), `art/`.

## Rules for working here

- Write tests before rules code in `core/rules`, and keep them passing.
- Screens use design-system tokens only: `OllTheme.colors.*` and `OllType.*`. No raw hex colours or ad-hoc text styles in screens.
- One primary (peach) button per screen. Disabled buttons say what's missing.
- Never show state with colour alone: low needs also say "Low".
- Tap targets at least 48dp. Portrait and landscape both work: in landscape the HUD moves to a side panel and the map fills the rest.
- Copy is warm, short, sentence case, no emoji in the UI.
- Game numbers (decay rates, costs, prices) are starting values; they move to `content/` JSON in Phase 4.
- Never commit signing keys, `google-services.json`, or anything under `art/adult/`.
- Adult mode content exists only in Partners worlds with both players opted in.

## Commands

- Run rules tests: `./gradlew :core:rules:test`
- Build debug APK: `./gradlew :app:assembleDebug`
- Install on a connected phone: `adb install -r -t app/build/outputs/apk/debug/app-debug.apk`
- Publish a release to friends' phones: bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then `tools/release.sh "What's new"`. It builds the signed release and force-pushes the APK and `latest.json` to the `releases` branch; the game offers the update when it next opens. Release signing needs `keystore.properties` and the key it points to (both git-ignored, backed up outside the repo). Debug builds are signed with a different key, so they never offer updates.

Running Gradle from Claude Code's shell on this machine fails with "Unable to establish loopback connection" unless Java gets a short temp folder. Use:

```bash
export TMP='C:\Users\Public\olbuild-tmp' TEMP='C:\Users\Public\olbuild-tmp' JAVA_HOME=~/.jdks/jbr-21.0.11
export GRADLE_OPTS='-Djava.io.tmpdir=C:\Users\Public\olbuild-tmp -Djdk.net.unixdomain.tmpdir=C:\Users\Public\olbuild-tmp'
./gradlew <tasks> -Dorg.gradle.jvmargs="-Xmx2048m -Djava.io.tmpdir=C:\\Users\\Public\\olbuild-tmp -Djdk.net.unixdomain.tmpdir=C:\\Users\\Public\\olbuild-tmp"
```

adb lives at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
