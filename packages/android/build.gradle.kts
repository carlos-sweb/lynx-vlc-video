// lynx-vlc-video — the raw, framework-agnostic <vlc-video> element for
// Lynx/Android. Framework-agnostic on purpose: it registers a native
// element tag via Lynx's own Behavior mechanism, the same layer <video>
// (org.lynxsdk.lynx:xelement-video) is built on — any JS framework that
// renders Lynx elements (mithril-lynx, ReactLynx, plain hyperscript, etc.)
// can use <vlc-video src="..."> once this module's Behavior is registered,
// with no framework-specific code in this module at all. See
// packages/mithril for a typed wrapper — that's the only framework-specific
// piece, and it's optional.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.carlossweb.lynxvlcvideo"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // The Lynx element PAPI this module's Behavior/LynxUI/@LynxProp/
    // @LynxUIMethod annotations target — compileOnly because the host app
    // already provides it (org.lynxsdk.lynx:lynx) and a library module
    // should never force a specific version on its consumer.
    compileOnly("org.lynxsdk.lynx:lynx:4.1.0")

    // The annotation processor that generates the Behavior/PropsSetter/
    // MethodInvoker boilerplate from this module's @LynxBehavior/@LynxProp/
    // @LynxUIMethod/@LynxGeneratorName annotations — the same mechanism
    // xelement-video itself is built with (confirmed in lynx-family/lynx's
    // own ext.gradle: `kapt project(':LynxProcessor')`), published
    // standalone on Maven Central for third-party use.
    kapt("org.lynxsdk.lynx:lynx-processor:4.1.0")

    // libVLC-all 3.7.x+ requires compileSdk 36 (AGP 8.5.2 here only
    // supports up to 34); 3.3.10 dlopen-fails on Android 16 devices
    // (missing NDK/bionic symbol). 3.6.5 is the newest version confirmed
    // working end-to-end on a real device — see /docs/TESTING.md before
    // changing this.
    api("org.videolan.android:libvlc-all:3.6.5")
}
