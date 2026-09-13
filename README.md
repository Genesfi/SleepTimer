# Sleep Timer - Relaxing Bedtime Companion

A premium, high-comfort Sleep Timer application designed for Android. This app helps users fall asleep peacefully by automatically pausing multimedia playback, freeing up system resources, and providing a sleek, dark-themed user interface optimized for bedtime usage.

## ✨ Features

-   **Circular Duration Selector**: An intuitive and cool circular slider to set your sleep timer from 1 to 180 minutes.
-   **Smooth Audio Fade-out**: Prevents abrupt silence by gradually decreasing the system volume before pausing playback.
-   **Advanced App Kill (Opt-in)**: Automatically terminates background processes of selected media apps (Spotify, YouTube, Netflix, etc.) to save battery and RAM.
-   **Zen Dark Theme**: A high-comfort "Midnight Twilight" UI palette designed to reduce eye strain in dark environments.
-   **Haptic Feedback**: Tactile "click" sensations when adjusting the timer for a premium feel.
-   **Lockscreen Integration**: High-priority notifications with real-time countdown visibility even when the device is locked (optimized for Xiaomi/POCO devices).
-   **Usage Analytics**: Track your app usage history and see which media apps were active.
-   **Detailed Playback History**: Capture and view a chronological list of all songs and videos played during each session.

## 🛠 Technical Stack

-   **Language**: 100% Kotlin
-   **UI Framework**: Jetpack Compose (Modern Declarative UI)
-   **Architecture**: MVVM (Model-View-ViewModel)
-   **Background Services**: Android Foreground Services with WakeLock for reliable operation during screen-off.
-   **Database**: Room Persistence Library for session and usage history.
-   **DI / State**: Kotlin Coroutines & Flow for reactive state management.

## 🚀 Getting Started

### Prerequisites
-   Android Device running Android 8.0 (Oreo) or higher (Optimized for Android 15/16).
-   Grant **Notification Permission** to see the countdown on your status bar/lockscreen.
-   Grant **Usage Stats Access** (Optional) to enable usage history tracking.

### Installation
1.  Clone the repository.
2.  Open the project in **Android Studio (Ladybug or later)**.
3.  Build and run the `app` module on your device.

## 📱 Xiaomi / POCO Users Note
To ensure the timer notification appears on your Lock Screen:
1.  Go to **Settings** > **Apps** > **Manage Apps** > **Sleep Timer**.
2.  Select **Notifications**.
3.  Ensure **Lock screen notifications** is toggled **ON**.
4.  Inside **Notification Categories**, set **Sleep Timer Service** to **Show all notifications and content**.

## 📄 License
This project is for personal use and portfolio purposes. Feel free to explore and modify!
