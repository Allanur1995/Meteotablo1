# Meteotablo Windows (Windows 7 32-bit & 64-bit)

Desktop version of **Meteotablo** targeting **JVM 1.8 (Java 8)** and **32-bit Windows PE (`.exe`)**, compatible with both **Windows 7 SP1 32-bit (x86)** and **64-bit (x64)** as well as Windows 10/11.

## Included Features
- Selectable live METAR/TAF sources from both the toolbar and Server settings:
  - **Авто: IP → Интернет** (default: queries `http://217.174.229.206:8181/` with `meteo` / `meteo`, automatically falls back to internet websites if IP is unavailable)
  - **Авто: Интернет → IP** (tries internet websites first, falls back to IP)
  - **Только IP-сервер**
  - **Только Интернет** (`aviationweather.gov`, `tgftp.nws.noaa.gov`, `metar.vatsim.net` — each toggleable)
- All 7 Turkmenistan airports (`UTAM`, `UTAA`, `UTAK`, `UTAN`, `UTAT`, `UTAV`, `UTAE`) + custom ICAO codes.
- 3-column Meteotablo instrument board with custom `Graphics2D` runway wind roses, headwind/tailwind/crosswind calculations, Day/Night mode, unit toggles (`kt / м/с / км/ч`, `гПа / дм`), METAR Table + Favorite Airports bar, and manual METAR/TAF input.
- Persistent configuration saved in `%USERPROFILE%\.meteotablo\config.properties`.

## Building the `.exe` via GitHub Actions
Push this repository to GitHub (`main` or `master`, or run **Actions -> Build Windows 7 EXE (32-bit & 64-bit)** manually) to download:
1. **`Meteotablo-Win7-Portable-x86-x64.zip`** — contains `Meteotablo.exe` + bundled **32-bit JRE 8**. Extract anywhere on Windows 7 (32-bit or 64-bit) and double-click `Meteotablo.exe` (no Java installation required).
2. **`Meteotablo-Win7-Setup-EXE`** (`Meteotablo-Setup-Win7-x86-x64.exe`) — single-file installer for Windows 7 SP1 (32-bit and 64-bit).

## Building Locally
```bash
gradle -p windows test fatJar --no-daemon
```
Output JAR: `windows/build/libs/Meteotablo-desktop.jar` (runs on Java 8+ 32-bit or 64-bit).
