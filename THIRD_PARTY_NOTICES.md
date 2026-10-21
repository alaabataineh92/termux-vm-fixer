# Third-party notices

Termux VM Fixer is distributed under the Apache License 2.0. It depends on the
following open-source projects. The notices below are provided in addition to,
not in place of, the license terms of each project.

## Apache License 2.0

The following components are licensed under the Apache License, Version 2.0.

| Component | Copyright |
|---|---|
| AndroidX Core, Activity, Lifecycle, Navigation, DataStore, DocumentFile, Security | Copyright The Android Open Source Project |
| Jetpack Compose (UI, Material 3, Material Icons) | Copyright The Android Open Source Project |
| Dagger and Hilt | Copyright Google LLC |
| Kotlin, Kotlin Coroutines | Copyright JetBrains s.r.o. and contributors |
| Apache Commons Compress | Copyright The Apache Software Foundation |
| Dadb | Copyright mobile.dev, Inc. |
| apksig | Copyright The Android Open Source Project |
| Gradle wrapper and Android Gradle Plugin | Copyright Gradle, Inc. and The Android Open Source Project |
| Termux (the `testkey_untrusted.jks` signing keystore) | Copyright The Termux contributors |

The full text of the Apache License 2.0 is included in `LICENSE`.

### Termux signing key

The file `app/src/main/assets/termux-testkey.jks` is the test signing keystore
that Termux publishes in the `termux/termux-app` repository and uses for the
APKs it releases on GitHub. It is included so that a patched Termux keeps the
signature of the Termux that is installed, which turns the install into an
update instead of a replacement.

The key is a test key that the Termux maintainers publish on purpose and that
everyone is able to use. It is not an official developer key, and the Termux
maintainers do not recommend using it for anything else.

- Source: https://github.com/termux/termux-app/blob/master/app/testkey_untrusted.jks
- Alias: `alias`
- Certificate SHA-256: `B6:DA:01:48:0E:EF:D5:FB:F2:CD:37:71:B8:D1:02:1E:C7:91:30:4B:DD:6C:4B:F4:1D:3F:AA:BA:D4:8E:E5:E1`

Termux is licensed under the Apache License 2.0.

## MIT License

The following components are licensed under the MIT License.

### Bouncy Castle

Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc.
(https://www.bouncycastle.org)

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Termux

Termux VM Fixer interoperates with, modifies APKs of, and repairs installations
of Termux. Termux is a separate project and is not bundled with this
application. Termux is licensed under the Apache License 2.0 and is available at
https://github.com/termux/termux-app.

## Virtual Master

Virtual Master is a separate proprietary application. It is not bundled with
this project and is not affiliated with it.
