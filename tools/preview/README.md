# Preview harness

Renders the real game screens to PNG on a desktop JVM, so the look and layout
can be iterated on without an emulator or a device.

`android/graphics/` is a small Java2D stand-in for the slice of
`android.graphics` the drawing code uses — `Canvas`, `Paint`, `Path`, `Bitmap`,
`RectF`, `Color`, `LinearGradient`, `Typeface`. The game's `engine`, `ui`, `art`
and `data` packages compile against it unchanged, which is only possible
because none of them import anything else from Android.

It is a development tool, not part of the app: nothing here ships in the APK,
and it is deliberately not wired into Gradle.

## Running it

Needs a standalone Kotlin compiler (`kotlinc`) on the path.

```bash
javac -d build/preview-shim tools/preview/android/graphics/*.java

kotlinc app/src/main/kotlin/com/barnyardblitz/engine \
        app/src/main/kotlin/com/barnyardblitz/ui \
        app/src/main/kotlin/com/barnyardblitz/art \
        app/src/main/kotlin/com/barnyardblitz/data \
        -cp build/preview-shim -d build/preview-game.jar

kotlinc tools/preview/Render.kt -cp build/preview-game.jar:build/preview-shim \
        -include-runtime -d build/preview-render.jar

java -Djava.awt.headless=true \
     -cp build/preview-render.jar:build/preview-game.jar:build/preview-shim \
     RenderKt 1080 2340 phone
```

Screens land in `build/preview/`. Pass width, height and a tag to render
another form factor — `720 1440 small`, `1600 1000 tablet`.

## Caveats

Java2D is not Skia. Text metrics and antialiasing differ slightly from a real
device, and the shim approximates per-corner rounded rectangles with a single
radius. It is accurate enough to judge layout, colour and composition; it is
not a pixel-exact preview, and it exercises none of the Android lifecycle,
input or audio paths.
