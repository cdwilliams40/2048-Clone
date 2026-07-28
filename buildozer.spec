[app]

title = Barnyard Blitz
package.name = barnyardblitz
package.domain = com.barnyardblitz

source.dir = .
source.include_exts = py,png
source.exclude_dirs = tests, tools, .github, bin, .buildozer

version = 1.0.0

# python-for-android ships a pygame recipe that builds against the SDL2
# bootstrap, which is the same SDL2 the desktop build uses.
requirements = python3,pygame

orientation = portrait
fullscreen = 1

icon.filename = %(source.dir)s/assets/icon.png
presplash.filename = %(source.dir)s/assets/presplash.png
android.presplash_color = #A63A34

android.api = 34
android.minapi = 21
android.ndk_api = 21
android.archs = arm64-v8a, armeabi-v7a
android.accept_sdk_license = True
android.allow_backup = True

# The game keeps its high scores in the app's private storage, so it needs no
# runtime permissions at all.
android.permissions =

p4a.bootstrap = sdl2

[buildozer]

log_level = 2
warn_on_root = 0
