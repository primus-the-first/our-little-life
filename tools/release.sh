#!/usr/bin/env bash
# Publishes a release to friends' phones: builds the signed APK and puts it, with latest.json, on the
# repo's "releases" branch. Within a few minutes, the game offers the update the next time it opens.
#
#   tools/release.sh "Smoother walking and a 3D race"
#   tools/release.sh --build-only      (signed APK in dist/ for testing, nothing published)
#
# Before running: bump versionCode and versionName in app/build.gradle.kts, and commit.
# Needs keystore.properties, the key it points to, and the debug key (~/.android/debug.keystore).
# None are committed; keep a backup of all three.
#
# Key rotation: the first phones ran debug builds, signed with the debug key. Releases are signed
# with the release key plus tools/signing/debug-to-release.lineage, the debug key's signed OK to
# hand over, so phones install them as normal updates and keep their saves. Android 8 phones
# still check the debug key's signature, so every release is signed with both.
set -euo pipefail

cd "$(dirname "$0")/.."
BUILD_ONLY=false
if [ "${1:-}" = "--build-only" ]; then BUILD_ONLY=true; shift; fi
NOTES="${1:-}"
REPO="primus-the-first/our-little-life"
BRANCH="releases"
RAW="https://raw.githubusercontent.com/$REPO"

[ -f keystore.properties ] || { echo "keystore.properties is missing, so the release can't be signed." >&2; exit 1; }
DEBUG_KEY="$HOME/.android/debug.keystore"
[ -f "$DEBUG_KEY" ] || { echo "The debug key ($DEBUG_KEY) is missing; restore it from your backup." >&2; exit 1; }
LINEAGE=tools/signing/debug-to-release.lineage

CODE=$(sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
NAME=$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
echo "Releasing $NAME (version code $CODE)"

# Refuse to publish a version phones already have: they'd never be offered it.
PUBLISHED=$(curl -fsS "$RAW/$BRANCH/latest.json" 2>/dev/null | sed -n 's/.*"versionCode": *\([0-9]*\).*/\1/p' || true)
if ! $BUILD_ONLY && [ -n "$PUBLISHED" ] && [ "$CODE" -le "$PUBLISHED" ]; then
  echo "Version code $CODE isn't newer than the published $PUBLISHED. Bump it in app/build.gradle.kts." >&2
  exit 1
fi

# Gradle on this machine needs a short temp folder (see CLAUDE.md).
SHORT_TMP='C:\Users\Public\olbuild-tmp'
mkdir -p /c/Users/Public/olbuild-tmp
export TMP="$SHORT_TMP" TEMP="$SHORT_TMP" JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jbr-21.0.11}"
export GRADLE_OPTS="-Djava.io.tmpdir=$SHORT_TMP -Djdk.net.unixdomain.tmpdir=$SHORT_TMP"
./gradlew :core:rules:test :app:assembleRelease \
  -Dorg.gradle.jvmargs="-Xmx2048m -Djava.io.tmpdir=C:\\Users\\Public\\olbuild-tmp -Djdk.net.unixdomain.tmpdir=C:\\Users\\Public\\olbuild-tmp"

BUILT=app/build/outputs/apk/release/app-release.apk
[ -f "$BUILT" ] || { echo "The build didn't produce $BUILT." >&2; exit 1; }

# Re-sign with both keys and the rotation proof. Passwords go through the environment, never the command line.
prop() { sed -n "s/^$1=//p" keystore.properties | tr -d '\r'; }
export OLL_STORE_PASS="$(prop storePassword)" OLL_KEY_PASS="$(prop keyPassword)"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
APKSIGNER="$(ls -d "$SDK"/build-tools/* | tail -1)/lib/apksigner.jar"
mkdir -p dist
APK="dist/OurLittleLife-$NAME.apk"
"$JAVA_HOME/bin/java" -jar "$APKSIGNER" sign \
  --ks "$DEBUG_KEY" --ks-key-alias androiddebugkey --ks-pass pass:android \
  --next-signer --ks "$(prop storeFile)" --ks-key-alias "$(prop keyAlias)" --ks-pass env:OLL_STORE_PASS --key-pass env:OLL_KEY_PASS \
  --lineage "$LINEAGE" --rotation-min-sdk-version 28 \
  --out "$APK" "$BUILT"
"$JAVA_HOME/bin/java" -jar "$APKSIGNER" verify "$APK"
echo "Signed: $APK"
if $BUILD_ONLY; then exit 0; fi

# The branch holds only the newest release, so the repo doesn't grow with every APK. Two commits:
# the APK first, then latest.json pointing at the APK by that commit's ID, which never goes stale
# in GitHub's cache. our-little-life.apk on the branch is the link for installing the first time.
ORIGIN=$(git remote get-url origin)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$APK" "$WORK/our-little-life.apk"
(
  cd "$WORK"
  git init -q -b "$BRANCH"
  git config core.autocrlf false
  git add our-little-life.apk
  git commit -q -m "Our Little Life $NAME"
  APK_COMMIT=$(git rev-parse HEAD)
  ESCAPED_NOTES=$(printf '%s' "$NOTES" | sed 's/\\/\\\\/g; s/"/\\"/g')
  cat > latest.json <<JSON
{
  "versionCode": $CODE,
  "versionName": "$NAME",
  "apk": "$RAW/$APK_COMMIT/our-little-life.apk",
  "notes": "$ESCAPED_NOTES"
}
JSON
  git add latest.json
  git commit -q -m "Point the game at $NAME"
  git push -q --force "$ORIGIN" "$BRANCH:$BRANCH"
)

echo "Published $NAME. Phones will be offered it within about five minutes."
echo "First-time install link: https://github.com/$REPO/raw/$BRANCH/our-little-life.apk"
