# Build and CI notes

## Pinned toolchain

The app uses JDK 17, Gradle Wrapper 8.13 (with its official distribution SHA-256 pinned), Android Gradle Plugin 8.13.0, Kotlin 2.3.0 with the `compilerOptions` DSL, compile/target SDK 36, and Build Tools 36.0.0. The installable debug application ID is `com.rollinkxx.xauusd.debug`. A release keystore is not configured; any release APK emitted by CI is unsigned.

## Android SDK packages and license scope

The workflow uses `android-actions/setup-android@v4` with its blanket license acceptance disabled. It records only the standard `android-sdk-license` acceptance hash needed for the stable SDK packages, then explicitly requests `platform-tools`, API 36, Build Tools 36.0.0, the emulator, and the API 35 default x86_64 system image. It does not request Google TV, Google APIs, preview platforms, or unrelated add-ons. The API 35 emulator is used only for the app launch/navigation smoke test; compilation targets API 36.

The first `setup-android@v3` attempt used its then-default `tools` package, which Google has removed. That action also blanket-accepted all pending licenses on its temporary hosted runner, including at least the Google TV add-on license, before failing at the removed package. No provider key or user data was involved. The workflow was replaced with v4 and a scoped stable-package setup following the user's explicit approval.

## Verification commands

Local Gradle wrapper startup and project configuration (`./gradlew --version` and `./gradlew help`) succeeded. The sandbox itself has no Android SDK, so local Android compilation and unit tests could not run. Hosted CI is the verification gate: unit tests, lint, debug/release assembly, and an API 35 emulator smoke test. Run logs and APK checksums are uploaded as workflow artifacts.

## Official references

- [Android Gradle Plugin 8.13 release notes](https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes)
- [Kotlin Gradle compiler options](https://kotlinlang.org/docs/gradle-compiler-options.html)
- [Android SDK command-line tools](https://developer.android.com/tools)
- [Android SDK package manager](https://developer.android.com/tools/sdkmanager)
- [Android SDK license terms](https://developer.android.com/studio/terms)
- [setup-android action](https://github.com/android-actions/setup-android)
