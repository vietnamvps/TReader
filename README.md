# TL Reader

**Your books. Your library. Your reading habit.**

TL Reader is an Android reading application that brings your books and reading activity together in one place. Import EPUB and PDF files, manage your personal library, review your reading statistics, and set reminders to make reading part of your daily routine.

The application is named **TL Reader**; its source repository is named **TReader**.

> **Development status:** Active development. More features and improvements are being developed. The interface and behavior may change between versions.

## Contents

- [Overview](#overview)
- [Features](#features)
- [Supported formats](#supported-formats)
- [Getting started](#getting-started)
- [Build from source](#build-from-source)
- [Project layout](#project-layout)
- [Development direction](#development-direction)
- [Reporting bugs](#reporting-bugs)
- [Contributing](#contributing)
- [License](#license)

## Overview

TL Reader is designed for readers who want to keep their digital books organized and develop a consistent reading habit. It combines book reading, library management, statistics, and reminders in a single Android app.

Bring your own EPUB or PDF files and build a personal collection that is easy to return to whenever you have time to read.

## Features

### EPUB and PDF import

- Import books and documents from files on your device.
- Read EPUB books and PDF documents within the app.
- Keep imported titles together in your personal library.

### Personal library management

- View and manage your collection of imported books.
- Find the book you want to read from your library.
- Keep your reading collection in one place.

### Reading statistics

- Review your reading activity through the app's statistics section.
- Use the available information to understand your reading habits.
- Stay motivated to make reading part of your routine.

### Reading reminders

- Set reading reminders to help you make time for books.
- Use reminders as a prompt to return to your library regularly.

### Ongoing development

TL Reader is a growing project. Additional features, usability improvements, and bug fixes are being developed. Feedback from readers and contributors helps guide future improvements.

## Supported formats

| Format | Purpose |
| --- | --- |
| EPUB (`.epub`) | Import and read electronic books. |
| PDF (`.pdf`) | Import and read PDF books and documents. |

Compatibility can vary with the structure and content of individual files. Support for DRM-protected or password-protected files is not promised by this README. If a file fails to open, include its format and relevant details when reporting the issue.

## Getting started

### Install the app

If an APK has been published in this repository's **Releases** section, download the desired release and install it on your Android device. If no APK is available, follow [Build from source](#build-from-source).

### Import a book

1. Open TL Reader.
2. Use the app's book import option.
3. Select an EPUB or PDF file through the file picker.
4. Wait for the import to finish.
5. Open the imported book from your library.

Exact button names and screen layouts may differ between versions.

### Review your reading activity

Open the statistics section to review the reading activity recorded by the app. Use it to reflect on your reading habits and decide when to make more time for books.

### Set reading reminders

Open the reminder controls and configure your preferred reading time. If Android requests notification permission, allow it to receive reminder notifications.

If reminders do not appear, check the app's notification permission and your device's battery or background activity restrictions.

## Build from source

### Requirements

- Android Studio compatible with the project's Android Gradle Plugin.
- The Android SDK components required by the project's Gradle configuration.
- A compatible JDK for the project's Gradle and Android Gradle Plugin versions.
- Git, if cloning the repository.
- An Android device or emulator for running the app.

Use the checked-in Gradle configuration as the source of truth for version requirements. Check `app/build.gradle.kts` for Android SDK settings and `gradle/wrapper/gradle-wrapper.properties` for the Gradle distribution. This README does not assume a specific minimum Android version.

### Open in Android Studio

1. Copy this repository's clone URL from the GitHub **Code** button.
2. Clone the repository, or download and extract its ZIP archive.
3. Open the project root in Android Studio—the folder containing `settings.gradle.kts`.
4. Let Gradle synchronization finish and install any required SDK components.
5. Select the `app` run configuration and a device or emulator.
6. Run the application.

### Build a debug APK

Run the command from the project root.

**Windows PowerShell:**

```powershell
.\gradlew.bat assembleDebug
```

**Linux or macOS:**

```bash
chmod +x gradlew
./gradlew assembleDebug
```

For the standard `app` module debug build, the APK is normally generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Custom build variants or output naming may change this path. Debug builds are intended for development and testing. Distributed release builds require appropriate release signing.

For additional build and signing guidance, see the [official Android command-line build documentation](https://developer.android.com/build/building-cmdline).

### Build troubleshooting

| Problem | What to check |
| --- | --- |
| Gradle synchronization fails | Read the first relevant error and check dependency access, SDK components, and JDK compatibility. |
| Android SDK cannot be found | Configure the local Android SDK location in Android Studio. |
| `gradlew` reports permission denied | Run `chmod +x gradlew` on Linux or macOS. |
| No device is available | Start an emulator or connect a device configured for USB debugging. |
| Build fails after changing tool versions | Compare the changes with the project's checked-in Gradle configuration. |

Keep machine-specific configuration such as `local.properties`, signing keys, passwords, and other credentials out of commits.

## Project layout

Key files and directories include:

| Path | Contents |
| --- | --- |
| `app/src/main/java/com/treader/` | Kotlin source files, including `MainActivity.kt`, `Epub.kt`, `Model.kt`, and `Reminder.kt`. |
| `app/src/main/res/` | Android resources, including launcher icons. |
| `app/src/main/AndroidManifest.xml` | Application manifest. |
| `app/build.gradle.kts` | App module build configuration. |
| `build.gradle.kts` | Root build configuration. |
| `settings.gradle.kts` | Gradle project settings. |
| `gradle/wrapper/` | Gradle Wrapper files. |
| `gradlew` and `gradlew.bat` | Gradle launch scripts. |

The project layout may evolve as development continues.

## Development direction

The current foundation covers EPUB and PDF import, library management, reading statistics, and reading reminders.

Further development will expand the app and refine the reading experience. Specific additions and delivery dates will be documented as they are confirmed. A feature request or suggestion should not be interpreted as an already available feature.

## Reporting bugs

Please open a GitHub issue with enough information to reproduce the problem:

- A short, descriptive title.
- Device model and Android version.
- App version or source commit, if known.
- Steps to reproduce the issue.
- Expected behavior and actual behavior.
- Screenshots or relevant error logs, where useful.
- For import or reading issues: file format, approximate file size, and whether other files have the same problem.

Share a minimal sample file only when you have permission to share its contents. Remove personal information from logs and screenshots.

## Contributing

Bug reports, feature suggestions, documentation improvements, and focused code contributions are welcome.

1. Check existing issues and pull requests to avoid duplicate work.
2. For a substantial change, open an issue to discuss the approach first.
3. Fork the repository and create a branch for your change.
4. Keep the change focused and follow the existing code style.
5. Build the app and test the behavior affected by your change.
6. Submit a pull request explaining the problem, the solution, and how you tested it.

For reader or import changes, test relevant EPUB and PDF files. For visible interface changes, include screenshots where helpful. Do not commit generated build output, local configuration, or signing credentials.

## License

TL Reader is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Tú Nguyễn.

You may use, modify, and distribute this software, including for commercial purposes, subject to the terms in the license. Copies or substantial portions of the software must retain the copyright and permission notices.

Third-party libraries and components remain subject to their respective licenses.

---

**Make room for a little reading every day.**
