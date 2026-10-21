# Google Play Store Beta Release Guide — Lucid Cars (Wear OS)

## 1. Release Bundle & Signing Files
- **Signed Release Android App Bundle (`.aab`)**:
  `/storage/emulated/10/Documents/LucidGravityWatch/LucidCars-release.aab` (also copied to `/storage/emulated/10/Documents/LucidCars-release.aab`)
- **Signed Release APK (`.apk`)**:
  `/storage/emulated/10/Documents/LucidGravityWatch/Lucid Cars.apk` (also copied to `/storage/emulated/10/Documents/Lucid Cars.apk`)
- **Application ID**: `com.droid.wearoslucidwatch`
- **Version**: `1.0.20` (`versionCode = 21`)

---

## 2. Store Listing Graphics (in `play-store-assets/`)
- **Hi-Res App Icon (`512 × 512 px` PNG)**: `play-store-assets/app-icon-512x512.png`
- **Feature Graphic (`1024 × 500 px` 24-bit PNG, no alpha)**: `play-store-assets/feature-graphic-1024x500.png`
- **Wear OS 1:1 Screenshots (`454 × 454 px` 24-bit PNG, frameless)**:
  1. `play-store-assets/wear-screenshot-1-dashboard-454x454.png` (Dashboard & Controls)
  2. `play-store-assets/wear-screenshot-2-charging-454x454.png` (Active Charging Power in kW)
  3. `play-store-assets/wear-screenshot-3-confirm-454x454.png` (Intentional Press Confirmation Pop-up)

---

## 3. Store Listing Copy (Ready to Paste)
- **App Name**: `Lucid Cars` *(Note: For Closed/Open Beta review under Google Play's third-party companion policy, if prompted by Google Play reviewers, use `Watch for Lucid (Unofficial)`)*
- **Short Description (74 / 80 chars)**:
  `Standalone Wear OS watch control & live battery telemetry for Lucid cars.`
- **Full Description**:
  ```
  Control and monitor your Lucid vehicle directly from your wrist on Wear OS.

  Key Features:
  • Live Battery State of Charge (%) and active charging power (kW) or Plugged In status
  • Automatic 7-second telemetry polling in Interactive and Ambient watch states, with accelerated 3-second polling after command actions
  • Quick-access circular controls for Frunk, Door Lock/Unlock, Rear Trunk, Horn, and Headlight Flash
  • Intentional-press confirmation prompts for Frunk, Door Lock/Unlock, and Trunk commands to prevent accidental activation
  • Standalone Wear OS operation over HTTPS/gRPC with support for US, EU, SA, and ME regions
  ```

---

## 4. Google Play Reviewer Credentials (`Policy → App content → App access`)
Select **"All or some functionality in my app is restricted"** and add these built-in reviewer demo credentials so Google Play reviewers can test all dashboard controls without a physical vehicle:
- **Username / Email**: `demo@lucidcars.app`
- **Password**: `GooglePlayReview2026`
- **Instructions for Reviewer**:
  `Enter email demo@lucidcars.app and password GooglePlayReview2026 on the sign-in screen and tap "Sign In to Lucid Cloud". This loads an interactive demo Lucid Gravity GT vehicle allowing full testing of Frunk, Door Lock/Unlock, Trunk (with confirmation dialogs), Horn, and Lights.`
