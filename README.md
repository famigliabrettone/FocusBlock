# 🛡️ FocusBlock — WhatsApp & App Blocker for Android

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%2F%20No%20Servers-success.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

**FocusBlock** is a native Android **WhatsApp Blocker** and Digital Wellbeing application built to protect your off-work hours and enforce the right to disconnect. It selectively blocks **WhatsApp**, **WhatsApp Business**, or any chosen application during evenings, weekends, and holidays—**without turning off your phone or cutting internet access to other apps**.

---

## 💡 The Problem It Solves

If you rely on WhatsApp or messaging apps for work, disconnecting after hours is hard:
* **Do Not Disturb** silences everything indiscriminately.
* **Turning off Wi-Fi or Mobile Data** stops you from browsing, streaming music, or using maps.
* **Force-stopping apps** is tedious and easy to forget.

**FocusBlock solves this with a 3-layer blocking architecture**: to anyone messaging or calling you on WhatsApp, you appear **offline / unreachable** (single grey tick, just like a switched-off phone), while every other app on your device continues working at full speed.

---

## ⚙️ How It Works: 3-Layer Architecture

FocusBlock combines three native Android APIs to guarantee a watertight block:

```text
┌─────────────────────────────────────────────────────────────────┐
│                          FOCUSBLOCK                             │
├────────────────────────────────┬────────────────────────────────┤
│ 1. Local VPN Sinkhole          │ Drops data packets only for    │
│    (VpnService)                │ selected apps (no servers).    │
├────────────────────────────────┼────────────────────────────────┤
│ 2. Accessibility Service       │ Intercepts app launches and    │
│    (AccessibilityService)      │ displays a clean block screen. │
├────────────────────────────────┼────────────────────────────────┤
│ 3. Notification Blocker        │ Cancels notifications & VoIP   │
│    (NotificationListener)      │ incoming ringtones instantly.  │
└────────────────────────────────┴────────────────────────────────┘

1. **🛡️ Network Layer (Local `VpnService` Sinkhole):**
   Creates a dummy internal VPN interface (`10.0.0.2` / `fd00::1`). Using `addAllowedApplication()`, **only** blocked apps are routed through this interface where their traffic is dropped. All other apps bypass the VPN and connect directly to the internet.  
   *Zero external servers: 100% local and offline.*

2. **🚫 UI Layer (`AccessibilityService`):**
   Detects when a blocked app is opened during a restricted schedule and immediately overlays `BlockScreenActivity`, showing when the app will become available again.

3. **🔕 Notification Layer (`NotificationListenerService`):**
   Immediately intercepts and cancels push notifications and incoming VoIP call rings (WhatsApp, WhatsApp Business, Telegram, Messenger, etc.) during active block windows.

---

## ✨ Key Features

* 📅 **Flexible Time Schedules:**
  * Select specific days of the week (e.g., weekends only, or Monday–Friday evenings).
  * Custom time ranges, including overnight schedules crossing midnight (e.g., `18:00 – 08:00`).
  * Full-day (`24h`) blocking option.
* 🗓️ **Automatic Holiday Detection:**
  * Built-in holiday calendar (preconfigured with Italian national holidays), including dynamic calculation of **Easter and Easter Monday** using Gauss's Easter algorithm.
* ⚡ **Quick Actions:**
  * **Block Now:** Trigger an immediate focus session with a single tap.
  * **Temporary Emergency Unblock:** Pause blocking for 5, 10, 15, 30, or 60 minutes, automatically re-enabling protection when time expires.
* 📦 **Universal App Picker:**
  * Block any installed app on your device, not just WhatsApp.
* 🔋 **Battery Friendly:**
  * Uses `AlarmManager` exact alarms rather than heavy background polling loops.

---

## 🔒 100% Privacy & Security

* **No Remote Servers:** The app never connects to external servers or collects analytics.
* **No Message Logging:** Notifications and network packets are discarded locally without reading or storing content.
* **Open Source:** Full source code available for transparent inspection.

---

## 📱 Required Permissions

* **VPN Service (`BIND_VPN_SERVICE`):** To isolate internet traffic exclusively for blocked apps.
* **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`):** To prevent opening blocked apps on screen.
* **Notification Access (`BIND_NOTIFICATION_LISTENER_SERVICE`):** To suppress notifications and incoming VoIP calls.
* **Exact Alarms (`SCHEDULE_EXACT_ALARM`):** To enforce start and end times down to the minute.

---

## 🛠️ Build & Requirements

* Android Studio Iguana / Jellyfish / Ladybug or newer
* JDK 17+
* Android 8.0 (API 26) or higher

```bash
git clone [https://github.com/famigliabrettone/FocusBlock.git](https://github.com/famigliabrettone/FocusBlock.git)
cd FocusBlock
./gradlew assembleDebug



🤝 Contributing
Contributions from the community are welcome!

Fork the project

Create your feature branch (git checkout -b feature/AmazingFeature)

Commit your changes (git commit -m 'Add some AmazingFeature')

Push to the branch (git push origin feature/AmazingFeature)

Open a Pull Request
📄 License
Distributed under the MIT License. See LICENSE for more information.

