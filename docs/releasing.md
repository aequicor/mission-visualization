# Desktop releases

This document is the source of truth for Mission Visualization desktop releases.
The product version is declared once as `missionVisualizationVersion` in
`gradle.properties`. The macOS app bundle, DMG, Windows app image, EXE installer,
and release tag `vMAJOR.MINOR.PATCH` use that value.

## Release artifacts

Pushing a matching version tag, or manually running `.github/workflows/release.yml`,
builds these installers with JDK 21:

- `Mission Visualization-<version>-setup.exe` for Windows x64-compatible systems;
- `Mission Visualization-<version>-macos-x64.dmg` for Intel Macs;
- `Mission Visualization-<version>-macos-arm64.dmg` for Apple Silicon Macs.

The workflow first runs all JVM tests and a desktop build. A manual workflow run uploads
the three Actions artifacts without publishing a GitHub release. A tag run also
publishes them to the matching GitHub release. Packaging is unsigned unless signing
and notarization credentials are supplied securely by a release environment; no
credentials belong in this repository.

Compose Desktop/jpackage creates both platform app images with a private Java runtime.
The Windows installer recursively copies the complete release app image. The macOS
DMG contains the complete `.app` bundle.

## Local packaging

On an Apple Silicon Mac, build the host-architecture DMG with:

```bash
./gradlew :desktopApp:packageReleaseDmg
```

The final image is written to
`desktopApp/build/compose/binaries/main-release/dmg/`. The post-processing task
replaces `.VolumeIcon.icns`, marks the custom volume icon, removes unexpected root
entries, and converts the image to compressed UDZO. The final root contains only
`.DS_Store`, `.VolumeIcon.icns`, `Applications`, and `Mission Visualization.app`.

On Windows, install Inno Setup 6.7.1 and run:

```powershell
.\gradlew.bat :desktopApp:packageReleaseWindowsInstaller
```

The compiler is resolved in this order:

1. `-PinnoSetupCompiler=C:\path\to\ISCC.exe`;
2. `INNO_SETUP_COMPILER`;
3. `ISCC.exe` from `PATH`.

The EXE is written to `desktopApp\build\compose\binaries\main-release\inno\`.
It has an identity independent from the legacy jpackage MSI and installs for the
current user into `%LOCALAPPDATA%\Programs\Mission Visualization` without elevation.
Do not remove or migrate an existing MSI installation automatically.

## Release verification

Before publishing, complete the checks on real target hosts.

Windows:

- install the EXE and confirm that no UAC prompt appears;
- verify the install directory, installer icon, application icon, uninstall icon,
  and current-user Start menu shortcut;
- launch the app and complete a short editor scenario: open the bundled sample,
  select an object, change a property, and verify the preview updates;
- install a newer build over the existing EXE installation and confirm the running
  app closes without being restarted automatically;
- uninstall through Windows Settings and confirm legacy MSI installations remain.

macOS, on both Intel and Apple Silicon:

- mount the DMG and verify the branded volume icon in Finder;
- confirm the root has no `.background` or `.fseventsd` directory;
- compare the mounted `.VolumeIcon.icns` with
  `desktopApp/src/main/resources/icons/mission-logo.icns` using `shasum`;
- run `codesign --verify --deep --strict` on `Mission Visualization.app`;
- visually confirm that Finder shows only the app and the `Applications` shortcut;
- copy the app to `/Applications`, launch it, and complete the short editor scenario;
- confirm `Mission Visualization.app/Contents/runtime` exists so the install does
  not depend on a system Java runtime.

Mission Visualization does not capture the screen or microphone, so its macOS bundle
does not declare `NSScreenCaptureUsageDescription` or `NSMicrophoneUsageDescription`.
