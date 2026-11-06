# EcoDrive OBD

Android app that measures a car's fuel consumption in real time through a Bluetooth OBD-II module (ELM327), and scores eco-driving **while accounting for road gradient**.

<p>
  <img src="docs/screenshots/direct.jpg" width="24%" alt="Live dashboard" />
  <img src="docs/screenshots/trajet.jpg" width="24%" alt="Trip chart" />
  <img src="docs/screenshots/carte.jpg" width="24%" alt="Over-consumption map" />
  <img src="docs/screenshots/vehicule.jpg" width="24%" alt="Vehicle profile" />
</p>

The app UI is in French.

## Features

- **Instant and average consumption** (L/100 km, L/h when stopped), read over OBD-II: fuel rate (PID 5E), otherwise mass air flow (PID 10), otherwise manifold pressure (PID 0B).
- **Gradient-aware eco score**: actual consumption is compared with the consumption expected by a physical model (mass, rolling resistance, aerodynamics) for the current speed and slope. Climbing is not penalised; accelerating downhill is.
- **Elevation** from the phone's barometer, or from the [Open-Meteo](https://open-meteo.com/en/docs/elevation-api) terrain model (Copernicus 90 m) when the phone has none.
- **Trip chart** coloured by eco score, scrollable, averaged according to the displayed time window.
- **Over-consumption map** (OpenStreetMap), aggregated per road, with a per-day detail view.
- **Trip kept while the engine runs** (PID 1F), even if the app restarts.
- **Auto-start** when the phone connects to the car over Bluetooth.
- **Android Auto** (Car App Library, IoT category).

## Build

Requires JDK 17 and Android SDK 36.

```bash
./gradlew assembleDebug
./gradlew :shared:jvmTest :app:testDebugUnitTest
```

A built-in **ELM327 simulator** (*Connecter* → *Simulateur*) answers with real OBD frames, so the app can be tested on an emulator without a car.

## Android Auto

In a real car, Android Auto only shows Car App Library apps installed from a trusted source (Google Play, internal test track). For development, use the [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu).

## Project layout (Kotlin Multiplatform)

- `shared/` — Kotlin Multiplatform module (Android, JVM, iOS). `commonMain` holds the logic both apps share: ELM327 protocol and PID parsing, fuel calculation, VIN, physical vehicle model, eco score, gear estimation, geohash and road segments, trip summaries and their JSON files (kotlinx.serialization), OpenStreetMap speed limits and terrain elevation (Ktor), the ELM327 simulator and the Wi-Fi transport.
- `app/` — Android app: Compose UI, Android Auto, foreground service, classic Bluetooth transport (SPP), sensors and GPS, geocoder, vehicle registry, daily history.
- `iosApp/` — SwiftUI app (live consumption and eco score) built on the `Shared` framework.

## iOS

### OBD adapter

iOS apps cannot use classic Bluetooth (SPP/RFCOMM) adapters: Core Bluetooth only speaks Bluetooth Low Energy, and classic accessories need Apple's MFi programme. The usual ELM327 "Bluetooth" dongles sold for Android therefore do not work on iPhone. Two kinds of adapter do:

- **Bluetooth LE (4.0) ELM327** — `BleTransport` (`shared/src/iosMain`). It connects to the first adapter advertising a known service (`FFF0`, `FFE0`, `18F0`) or a known name prefix (`OBD`, `V-LINK`, `Vgate`…), then picks the first notify characteristic and the first write characteristic it finds, so it does not depend on one clone's exact UUIDs. Not yet tried with a real adapter.
- **Wi-Fi ELM327** — `TcpTransport` (`shared/src/commonMain`), raw TCP to the adapter, by default `192.168.0.10:35000`, after joining the adapter's Wi-Fi network. Covered by a JVM test against a local fake adapter.

The app's `Info.plist` declares `NSBluetoothAlwaysUsageDescription` and `NSLocalNetworkUsageDescription` for these two transports.

### Build (needs Xcode)

The iOS app has not been built or run yet: this requires a full Xcode install. Kotlin code for the iOS targets compiles without Xcode (`./gradlew :shared:compileKotlinIosSimulatorArm64`); linking the framework and building the app do not. Once Xcode is installed:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
brew install xcodegen
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
cd iosApp && xcodegen generate && open EcoDriveOBD.xcodeproj
```

The Xcode project runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` before each build. Pick *Simulateur* in the app to try it without a car.

