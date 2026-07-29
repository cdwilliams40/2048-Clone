#!/usr/bin/env bash
# Checks that need no Android SDK: they compile the app against a real Android
# runtime jar, verify the layout geometry, and render every screen to PNG.
#
# These exist because the SDK is a heavy dependency and these three things
# catch most regressions without it. CI runs them on every push; you can run
# them locally the same way.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build/offline"
KOTLIN_VERSION="${KOTLIN_VERSION:-2.0.21}"
ANDROID_ALL="${ANDROID_ALL:-14-robolectric-10818077}"

cd "$ROOT"   # Render.kt writes to build/preview relative to the CWD
mkdir -p "$BUILD/libs"

kotlinc_bin="${KOTLINC:-}"
if [[ -z "$kotlinc_bin" ]]; then
  if command -v kotlinc >/dev/null 2>&1; then
    kotlinc_bin="$(command -v kotlinc)"
  else
    if [[ ! -x "$BUILD/kotlinc/bin/kotlinc" ]]; then
      echo "==> fetching Kotlin $KOTLIN_VERSION"
      curl -sSLo "$BUILD/kotlin-compiler.zip" \
        "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VERSION/kotlin-compiler-$KOTLIN_VERSION.zip"
      unzip -q -o "$BUILD/kotlin-compiler.zip" -d "$BUILD"
      chmod +x "$BUILD"/kotlinc/bin/*
    fi
    kotlinc_bin="$BUILD/kotlinc/bin/kotlinc"
  fi
fi

# Robolectric's android-all is a full Android runtime jar on Maven Central, so
# the app can be compiled and its pure-Java classes (RectF, Color) executed
# without the SDK.
if [[ ! -f "$BUILD/libs/android-all.jar" ]]; then
  echo "==> fetching android-all $ANDROID_ALL"
  curl -sSLo "$BUILD/libs/android-all.jar" \
    "https://repo1.maven.org/maven2/org/robolectric/android-all/$ANDROID_ALL/android-all-$ANDROID_ALL.jar"
fi

APP_SRC="$ROOT/app/src/main/kotlin"
PKG="$APP_SRC/com/barnyardblitz"

echo "==> type-checking the whole app against the Android API"
"$kotlinc_bin" "$APP_SRC" -cp "$BUILD/libs/android-all.jar" -d "$BUILD/app.jar" -nowarn

echo "==> layout geometry across screen sizes"
"$kotlinc_bin" "$ROOT/tools/LayoutCheck.kt" -cp "$BUILD/app.jar:$BUILD/libs/android-all.jar" \
  -include-runtime -d "$BUILD/layoutcheck.jar" -nowarn
java -cp "$BUILD/layoutcheck.jar:$BUILD/app.jar:$BUILD/libs/android-all.jar" LayoutCheckKt

echo "==> rendering screens"
# Compiling the drawing stack against the Java2D shim also enforces that ui,
# art, engine and data import nothing from Android beyond android.graphics.
javac -d "$BUILD/shim" "$ROOT"/tools/preview/android/graphics/*.java
"$kotlinc_bin" "$PKG/engine" "$PKG/ui" "$PKG/art" "$PKG/data" \
  -cp "$BUILD/shim" -d "$BUILD/game.jar" -nowarn
"$kotlinc_bin" "$ROOT/tools/preview/Render.kt" -cp "$BUILD/game.jar:$BUILD/shim" \
  -include-runtime -d "$BUILD/render.jar" -nowarn
for size in "1080 2340 phone" "720 1440 small" "1600 1000 tablet"; do
  # shellcheck disable=SC2086
  java -Djava.awt.headless=true -cp "$BUILD/render.jar:$BUILD/game.jar:$BUILD/shim" RenderKt $size
done

echo "==> offline checks passed"
