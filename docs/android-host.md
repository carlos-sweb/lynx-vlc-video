# Android host

Native `<vlc-video>` AAR (`android.namespace` `com.carlossweb.lynxvlcvideo`). Not an npm package — without this host step the JS tag does nothing. Full install path: [quick-start](quick-start.md).

## Maven Central (recommended)

```kotlin
dependencies {
    implementation("org.lynxsdk.lynx:lynx:4.1.0")
    implementation("io.github.carlos-sweb:lynx-vlc-video:0.1.0")
}
```

`libvlc-all:3.6.5` is transitive (`api`). Do not bump to `3.3.10` (fails on Android 16) or `3.7.x` (needs compileSdk 36). See [TESTING](TESTING.md).

kapt / `lynx-processor` already ran when the AAR was built. The host does **not** need kapt only for `<vlc-video>`.

## Register

```kotlin
import com.carlossweb.lynxvlcvideo.BehaviorGenerator as VlcVideoBehaviorGenerator

builder.addBehaviors(VlcVideoBehaviorGenerator.getBehaviors())
```

## Local `include` (hacking this repo)

`settings.gradle.kts`:

```kotlin
include(":lynx-vlc-video")
project(":lynx-vlc-video").projectDir =
    file("../lynx-vlc-video/packages/android")
```

```kotlin
implementation(project(":lynx-vlc-video"))
```

## If you also use `xelement`

Exclude `xelement-animax`. `pickFirsts` alone can keep the wrong `libc++_shared.so` and crash at `dlopen`:

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

Details: [ARCHITECTURE](ARCHITECTURE.md).

## Build check (this repo)

```bash
./gradlew :packages:android:assembleRelease
```

Releasing to Maven Central: [publishing](publishing.md).
