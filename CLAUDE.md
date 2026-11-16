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
- Build screens from `ui/components`: `ActionRow` for anything you can do (icon, name, what it takes and gives), `PopCard` for anything pressable that floats, `StatChip`/`StatusChip`, `SettingSwitch`, `ChoiceCard`. Icons come only from `OllIcons` (Material Symbols Rounded stand in until the pixel set exists).
- The main screen is: top line (day, coins, settings), the five need meters, a status strip, the map, and the bottom bar (Me, Together, Build or Furnish, Bed). Notices and cards sit at the bottom of the map. Bed is peach only when it's late or energy is low.
- Never show state with colour alone: low needs also say "Low".
- Tap targets at least 48dp. Portrait and landscape both work: in landscape the HUD moves to a side panel and the map fills the rest.
- Copy is warm, short, sentence case, no emoji in the UI.
- Game numbers (decay rates, costs, prices) are starting values; they move to `content/` JSON in Phase 4.
- Never commit signing keys, `google-services.json`, or anything under `art/adult/`.
- Adult mode content exists only in Partners worlds with both players opted in.
- Adult mode is "the private side" (`core/rules/.../Private.kt`, `home/PrivateScreen.kt`): each player opts in on their own phone, either can turn it off for both, and it sits behind a per-phone PIN on a screen that can't be captured. Moments are asked for and must be accepted; nothing is depicted (time passes, the bond grows). Notifications and anything on the main screen say who, never what. The players' own notes live in the world's saved state. Nothing explicit ever goes in this repo or the APK: both are public.

## Commands

- Run rules tests: `./gradlew :core:rules:test`
- Build debug APK: `./gradlew :app:assembleDebug`
- Draw the screens on this computer, no phone needed: `./gradlew :app:testDebugUnitTest` writes PNGs to `app/build/shots/` (`app/src/test/.../Screenshots.kt`, Robolectric). Look at them after any UI change, in Day and Night. The 3D view can't be drawn this way; they use the flat map.
- Install on a connected phone: `adb install -r -t app/build/outputs/apk/debug/app-debug.apk`. Phones on a release build refuse debug builds (different key), so test there with `tools/release.sh --build-only` and `adb install -r dist/OurLittleLife-<version>.apk`.
- Publish a release to friends' phones: bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then `tools/release.sh "What's new"`. It builds the release, signs it, and force-pushes the APK and `latest.json` to the `releases` branch; the game offers the update when it next opens. `tools/release.sh --build-only` makes the signed APK in `dist/` without publishing. Only APKs from the script are signed correctly; a plain `assembleRelease` lacks the rotation proof.
- Signing: the first phones ran debug builds, so releases are signed with the release key plus `tools/signing/debug-to-release.lineage` (a public proof, signed by the debug key, that the release key replaces it), and with the debug key for Android 8. Phones install them as normal updates and keep their saves. Signing needs `keystore.properties`, the key it points to, and `~/.android/debug.keystore`: never commit them, keep backups outside the repo. Debug builds don't offer updates.

Running Gradle from Claude Code's shell on this machine fails with "Unable to establish loopback connection" unless Java gets a short temp folder. Use:

```bash
export TMP='C:\Users\Public\olbuild-tmp' TEMP='C:\Users\Public\olbuild-tmp' JAVA_HOME=~/.jdks/jbr-21.0.11
export GRADLE_OPTS='-Djava.io.tmpdir=C:\Users\Public\olbuild-tmp -Djdk.net.unixdomain.tmpdir=C:\Users\Public\olbuild-tmp'
./gradlew <tasks> -Dorg.gradle.jvmargs="-Xmx2048m -Djava.io.tmpdir=C:\\Users\\Public\\olbuild-tmp -Djdk.net.unixdomain.tmpdir=C:\\Users\\Public\\olbuild-tmp"
```

adb lives at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
