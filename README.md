# Termux VM Fixer

Termux VM Fixer repairs a Termux installation running inside a Virtual Master
VM, where two kernel-level faults break `dpkg`, `apt` and the Termux bootstrap.

## What it fixes

1. **Broken temporary paths.** Virtual Master returns `EACCES` where Android
   returns `EROFS` for removals under `/`, so `dpkg` aborts when it cleans up
   `/data.dpkg-tmp`. The bundled `libvmfix.so` maps that specific failure to
   `ENOENT`, which `dpkg` accepts.
2. **Relative symlinks.** Virtual Master resolves a relative symlink target
   against the creating process's working directory instead of the link's own
   directory, producing dangling links after every package install.
   `libvmfix.so` resolves relative targets itself and passes an absolute target
   to the kernel.

The library is loaded through `LD_PRELOAD` from the Termux login hook. Small
`touch`, `cp` and `tar` shims cover the tools that cannot set timestamps on
these VMs.

## Repair routes

- **Root** - runs the repair as the superuser. This is the only route that can
  finish half-installed packages.
- **ADB over TCP** - connects to the VM's own `adbd` and repairs it remotely.
- **APK patcher** - builds a Termux APK that applies the fix when it is
  installed. It needs neither root nor ADB.

## Requirements

- Android 5.1.1 (API 22) or newer. The app targets API 37.
- `arm64-v8a` for the direct repair routes, because `libvmfix.so` is built for
  that ABI.
- Termux installed in the VM. For ADB repair, Termux must be debuggable
  (F-Droid or self-built); the Play Store build is not.
- A Virtual Master VM for the faults to exist at all.

## Build

Requirements:

- JDK 18 or newer
- Android SDK Platform 37
- Android NDK `27.0.12077973`
- CMake `3.22.1`
- `ANDROID_HOME` or `local.properties` pointing at the SDK

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

The Gradle wrapper uses Gradle 9.6.1 and Android Gradle Plugin 9.4.1. Kotlin and
Java target level 18. Release builds enable R8 and resource shrinking.

The native library is built from `app/src/main/cpp/` by the plugin, with CMake
and the NDK. It is stripped and free of debug information, and the build
copies it into `app/src/main/assets/libvmfix/`, one folder per ABI. No binary
is kept in the repository.

## Project layout

| Module | Purpose |
|---|---|
| `app` | Application entry point, navigation and dependency injection |
| `core:common` | Shared utilities |
| `core:model` | Domain models and Termux paths |
| `core:designsystem` | Compose theme and reusable UI components |
| `core:resources` | Shared strings and raw assets |
| `core:device` | Device probes, ADB transport and asset loading |
| `data:fix` | Repair engine and script contracts |
| `data:privilege` | Root and ADB privilege providers |
| `data:apkpatch` | APK patch pipeline, signing and verification |
| `feature:home` | Device summary and transport selection |
| `feature:fix` | Repair progress |
| `feature:settings` | Settings, diagnostics and licenses |
| `feature:patcher` | Patch configuration and results |

Shell scripts and hooks live in
`core/device/src/main/assets/scripts/`. The APK patch payload is assembled from
the same assets.

## Native library

The source for `libvmfix.so` is in `app/src/main/cpp/`, together with its
`CMakeLists.txt`. The build compiles it with the NDK for every ABI it
supports, strips the symbols and copies each one into
`app/src/main/assets/libvmfix/<abi>/libvmfix.so`. The app reads the folder
that matches the device and pushes that file onto the device.

The library is loaded with `LD_PRELOAD` and intercepts `rmdir`, `unlink`,
`rmdirat`, `unlinkat`, `symlink` and `symlinkat`, so those six functions have
to stay exported. Everything else in the file is static and never leaves the
library.

The size of the library tells the app whether the copy in a prefix is the
same build, so it is read from the asset at runtime rather than written down.

## Credits

Made by **Ala'a Al-Bataineh** — <alaabataineh92@gmail.com>

This project is open source. The source code, the issue tracker and the commit
history live at
[github.com/alaabataineh92/termux-vm-fixer](https://github.com/alaabataineh92/termux-vm-fixer).

The same credits are shown in the app, on the splash screen and under
**Settings > Credits**.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and
[NOTICE](NOTICE). Third-party components and their licenses are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The same notices are available
inside the app under **Settings > About > Licenses and notices**.

## Disclaimer

This project is not affiliated with Termux, Virtual Master or Google. Modifying
an APK and uninstalling Termux can delete data. Back up anything important
before using the patcher.
