# Android host

The native element lives in `/packages/android` (`android.namespace` `com.carlossweb.lynxvlcvideo`). It is **not** an npm package — Gradle / Maven owns it. The mithril helper on npm only types `m("vlc-video", …)`; without this host step the tag does nothing.

## Recommended: Maven Central

```kotlin
dependencies {
    implementation("org.lynxsdk.lynx:lynx:4.1.0")
    implementation("io.github.carlos-sweb:lynx-vlc-video:0.1.0")
}
```

`libvlc-all:3.6.5` comes in transitively (`api` from the library). Do not bump it to `3.3.10` (dlopen-fails on Android 16) or `3.7.x` (needs compileSdk 36). Evidence: [TESTING](TESTING.md).

kapt / `lynx-processor` already run **inside** this library. The host does not need kapt just to register `<vlc-video>`.

## Local development (before / without Maven)

`settings.gradle.kts` of the host app:

```kotlin
include(":lynx-vlc-video")
project(":lynx-vlc-video").projectDir =
    file("../lynx-vlc-video/packages/android")
```

```kotlin
dependencies {
    implementation("org.lynxsdk.lynx:lynx:4.1.0")
    implementation(project(":lynx-vlc-video"))
}
```

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
./gradlew :packages:android:assembleRelease
```

That only packages the AAR. On-device playback needs a host that registers the Behavior and renders the tag.

## Maintainers: publish to Maven Central

Coordinates: `io.github.carlos-sweb:lynx-vlc-video` (version in root `gradle.properties` → `VERSION_NAME`). Plugin: `com.vanniktech.maven.publish` **0.34.0** (matches AGP 8.5.2).

1. Namespace **Verified** + Central Portal **User Token** + **GPG** key published to a keyserver.
2. Put secrets only in `~/.gradle/gradle.properties` (never commit them):

```properties
mavenCentralUsername=...
mavenCentralPassword=...
signing.keyId=...
signing.password=...
signing.secretKeyRingFile=/home/YOU/.gnupg/secring.gpg
```

3. `./gradlew :packages:android:publishToMavenCentral`
4. Open [Deployments](https://central.sonatype.com/publishing/deployments) → **Publish** (first release uses manual publish).
