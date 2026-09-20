# Android host

The native element lives in `/packages/android`. It is a library module (`namespace com.carlossweb.lynxvlcvideo`). It is **not** published yet — consume it as a local Gradle project.

## Include the module

`settings.gradle.kts` of the host app:

```kotlin
include(":lynx-vlc-video")
project(":lynx-vlc-video").projectDir =
    file("../lynx-vlc-video/packages/android")
```

Adjust the path so it points at this repo’s `packages/android`.

Host `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.lynxsdk.lynx:lynx:4.1.0")
    implementation(project(":lynx-vlc-video"))
}
```

`libvlc-all:3.6.5` comes in transitively (`api` from the library). Do not bump it to `3.3.10` (dlopen-fails on Android 16) or `3.7.x` (needs compileSdk 36). Evidence: [TESTING](TESTING.md).

kapt / `lynx-processor` already run **inside** this library. The host does not need kapt just to register `<vlc-video>`.

## Register the Behavior

```kotlin
import com.carlossweb.lynxvlcvideo.BehaviorGenerator as VlcVideoBehaviorGenerator

val builder = LynxViewBuilder()
builder.addBehaviors(VlcVideoBehaviorGenerator.getBehaviors())
```

Until this line runs, `m("vlc-video", …)` mounts nothing useful.

## `libc++_shared.so` vs `xelement`

If the host also uses `org.lynxsdk.lynx:xelement`, exclude `xelement-animax`. `pickFirsts` can keep the **wrong** `libc++_shared.so` and crash at `dlopen`:

```kotlin
implementation("org.lynxsdk.lynx:xelement:4.1.0") {
    exclude(group = "org.lynxsdk.lynx", module = "xelement-animax")
}

android {
    packaging {
        jniLibs {
            pickFirsts += listOf("lib/*/libc++_shared.so")
        }
    }
}
```

The `exclude` is the one that actually fixes the crash; `pickFirsts` only lets the merge succeed. Details: [ARCHITECTURE](ARCHITECTURE.md).

## Check the library builds

```bash
./gradlew :packages:android:assembleDebug
```

That only packages the AAR. On-device playback needs a host that registers the Behavior and renders the tag.
