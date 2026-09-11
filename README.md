# KeyFold: Posture-Aware Developer Keyboard for Samsung Fold 7 & Termux

KeyFold is a custom Android Input Method Service (IME) specifically designed for foldable devices (tailored for the **Samsung Galaxy Z Fold 7**) and command-line terminal workflows (**Termux**).

It brings back the beloved "Hacker's Keyboard" experience with deep hardware fold integration: when you unfold the Fold 7 in landscape and bend it into tabletop/flex mode (~90° angle), KeyFold automatically dimensions itself to fit the bottom half of the screen below the hinge crease, resizing Termux to occupy the top half like a classic clamshell **PDA / mini-laptop**.

---

## Key Features

1. **Tabletop / Flex Mode (PDA Experience)**:
   - Uses Jetpack WindowManager (`FoldingFeature`) and sensor detection.
   - Snaps keyboard height to the lower half below the hinge crease (~50% height).
   - Insets tell Termux to occupy the top half without entering full-screen "extract mode".
   - You can hide Termux's extra keys bar to reclaim maximum screen real estate.
2. **Terminal-First Layouts**:
   - 5 full rows with dedicated `ESC`, `TAB`, `CTRL`, `ALT`, cursor arrows (`▲`, `▼`, `◀`, `▶`), and direct symbol keys (`~`, `/`, `-`, `|`, `\`, `` ` ``, `$`).
   - Sticky modifier keys: single tap latches modifier for next keystroke; double tap locks (Caps/Ctrl lock); illuminated LED indicators.
   - Long-press repeat on Backspace, Delete, and Arrow keys.
   - `FN` layer with `F1`–`F12`, `Home`, `End`, `PgUp`, `PgDn`, `Insert`, and numeric keypad.
3. **Multi-Posture Detection**:
   - Automatically switches layout and height across 6 postures:
     * `UNFOLDED_LANDSCAPE_HALF` (PDA Tabletop flex mode)
     * `UNFOLDED_LANDSCAPE_FLAT` (Tablet landscape)
     * `UNFOLDED_PORTRAIT_HALF` (Book mode)
     * `UNFOLDED_PORTRAIT_FLAT` (Tablet portrait)
     * `FOLDED_PORTRAIT` (Cover screen portrait)
     * `FOLDED_LANDSCAPE` (Cover screen landscape)
4. **JSON-Configurable & Hot-Reloadable**:
   - Layouts are plain human-readable JSON files.
   - Automatically exported on launch to:
     `/sdcard/Android/data/com.keyfold.terminal/files/layouts/`
   - Edit layout JSON files directly in Termux using `vim`, `nano`, or `helix`.
   - Hot-reload without leaving Termux via:
     ```bash
     am broadcast -a com.keyfold.terminal.RELOAD_CONFIG
     ```
   - Also includes a built-in companion Settings Activity with posture inspector and live keyboard preview.

---

## Project Structure

```
keyfold/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/layouts/
│       │   │   ├── unfolded_landscape_pda.json    # Default 5-row PDA layout
│       │   │   ├── unfolded_landscape_flat.json   # Tablet landscape layout
│       │   │   ├── unfolded_portrait.json         # Tablet portrait layout
│       │   │   ├── folded_portrait.json           # Cover screen portrait
│       │   │   ├── folded_landscape.json          # Cover screen landscape
│       │   │   └── fn_layer.json                  # Function keys & navigation
│       │   ├── java/com/keyfold/terminal/
│       │   │   ├── model/LayoutModels.kt          # JSON data models
│       │   │   ├── posture/
│       │   │   │   ├── DevicePosture.kt           # Posture enums
│       │   │   │   └── DevicePostureDetector.kt   # WindowManager & sensor tracker
│       │   │   ├── layout/LayoutRepository.kt     # JSON loader & cache
│       │   │   ├── ime/
│       │   │   │   └── KeyFoldInputMethodService.kt # Core IME Service
│       │   │   ├── ui/KeyFoldKeyboardView.kt      # Hardware Canvas view
│       │   │   └── settings/KeyFoldSettingsActivity.kt # Settings & Preview
│       │   └── res/
│       │       ├── values/ (colors, strings, themes)
│       │       └── xml/method.xml
│       └── test/java/com/keyfold/terminal/
│           └── LayoutParsingTest.kt
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

---

## Building & Installing

### 1. Build the APK
```bash
./gradlew assembleDebug
```
Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Sideload to Samsung Galaxy Fold 7
#### Option A: via ADB (USB or Wireless ADB)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Option B: via Termux
Copy the APK to your phone storage or Termux home directory, then run:
```bash
termux-open app-debug.apk
```
Follow the on-screen Android package installer prompt.

### 3. Enable KeyFold
1. Go to Android **Settings > General management > Keyboard list and default**.
2. Toggle **KeyFold Developer Keyboard** ON.
3. Select **KeyFold** as the default keyboard (or switch to it using the keyboard switcher icon in the navigation bar when inside Termux).

---

## Customizing Layouts in Termux

The layout files are located at:
```bash
cd /sdcard/Android/data/com.keyfold.terminal/files/layouts/
ls -la
```

Edit any layout, for example `unfolded_landscape_pda.json`:
```bash
nano unfolded_landscape_pda.json
```

Trigger hot-reloading instantly:
```bash
am broadcast -a com.keyfold.terminal.RELOAD_CONFIG
```
You can create a convenient alias in your `~/.bashrc` in Termux:
```bash
alias kfreload='am broadcast -a com.keyfold.terminal.RELOAD_CONFIG'
```
