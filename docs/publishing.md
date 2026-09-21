# Publishing (maintainers)

Consumer install is in [quick-start](quick-start.md). This page is only for cutting a release.

## Android → Maven Central

Coordinates: `io.github.carlos-sweb:lynx-vlc-video` (version = root `gradle.properties` → `VERSION_NAME`). Plugin: `com.vanniktech.maven.publish` **0.34.0**.

### Secrets (never commit)

`~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=...   # Central Portal User Token
mavenCentralPassword=...
signing.keyId=...
signing.password=...
signing.secretKeyRingFile=/home/YOU/.gnupg/secring.gpg
```

Namespace `io.github.carlos-sweb` must be **Verified**. GPG public key must be on a keyserver.

### Release

```bash
# bump VERSION_NAME in gradle.properties if needed
./gradlew :packages:android:publishAndReleaseToMavenCentral
```

Or upload only (`publishToMavenCentral`) and click **Publish** on [Deployments](https://central.sonatype.com/publishing/deployments). Sync can take 10–30 minutes. A published version is immutable.

Smoke-test first:

```bash
./gradlew :packages:android:publishToMavenLocal
```

## mithril → npm

Package: `@carlos-sweb/lynx-vlc-video-mithril` (`packages/mithril`).

```bash
cd packages/mithril
# bump "version" in package.json
npm publish --access public --otp=YOUR_2FA_CODE
```

`prepublishOnly` runs `tsc -b`. Account needs 2FA for publish.

## After a release

- Update version pins in [quick-start](quick-start.md) / [android-host](android-host.md) when the Android artifact changes.
- Note the release in `CHANGELOG.md`.
