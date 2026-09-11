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
2. **Space Bar Cursor Trackpad Mode (Glowing UI & Haptics)**:
   - **Hold & Slide**: Hold the space bar for 250ms (or swipe across it) to transform the keyboard into a precision cursor trackpad.
   - **Glowing Visual Feedback**: The space bar illuminates with a glowing neon-cyan aura and displays responsive cursor navigation indicators (`◀  SLIDE TO MOVE CURSOR  ▶`), while background keys dim to focus your attention.
   - **Universal Compatibility**: Slide your thumb left, right, up, or down to navigate text. Emits DPAD events that work seamlessly across both standard Android apps (Chrome, messaging) and **Termux** (`bash`, `nano`, `vim`, `fzf`).
   - **Tactile Haptic Ticks**: Delivers a crisp haptic tick for each character step so you can feel text navigation without looking.
   - **Zero Typing Latency**: Fast two-thumb rollover logic ensures that fast typing is never delayed or misordered.
3. **Samsung-Style Cover Screen Mode**:
   - When the device is closed (`FOLDED_PORTRAIT`), KeyFold automatically switches to a clean 4-row layout matching the standard **Samsung Keyboard**.
   - Removes terminal clutter (`Tab`, `Esc`, `Ctrl`, `Alt`, arrow keys) on the narrow cover screen for comfortable one-handed or two-thumb typing.
   - Centered home row (`a`–`l`) using spacer keys with standard key pitch and sizing.
   - **Secondary Numbers & Long-Press**: Keys `q`–`p` feature numbers `1`–`0` and `.` has `?`. Long-pressing any key for 400ms outputs its secondary symbol with tactile haptic feedback.
   - **Dedicated Symbol Layer**: Tap **`!#1`** to toggle full symbol keyboard (`@`, `#`, `$`, `%`, `&`, `-`, `+`, `(`, `)`, quotes, punctuation, and Settings shortcut `⚙`). Tap **`ABC`** to return.
4. **Interactive Height Tuning & Live Preview**:
   - In-app GUI settings card to customize the keyboard height independently for each posture profile.
   - Switch between **Percentage of Screen** (slider 25%–65% with `+`/`-` fine-tuning steppers), **Snap Below Hinge**, or **Fixed DP**.
   - Embedded real-time interactive preview that scales as you slide.
   - Writes directly to the layout JSON files and hot-reloads the active IME instantly.
5. **Terminal-First Unfolded Layouts**:
   - 5 full rows with dedicated `ESC`, `TAB`, `CTRL`, `ALT`, cursor arrows (`▲`, `▼`, `◀`, `▶`), and direct symbol keys (`~`, `/`, `-`, `|`, `\`, `` ` ``, `$`).
   - Sticky modifier keys: single tap latches modifier for next keystroke; double tap locks (Caps/Ctrl lock); illuminated LED indicators.
   - Long-press repeat on Backspace, Delete, and Arrow keys.
   - `FN` layer with `F1`–`F12`, `Home`, `End`, `PgUp`, `PgDn`, `Insert`, and numeric keypad.
6. **Multi-Posture Detection**:
   - Automatically switches layout and height across 6 postures:
     * `UNFOLDED_LANDSCAPE_HALF` (PDA Tabletop flex mode)
     * `UNFOLDED_LANDSCAPE_FLAT` (Tablet landscape)
     * `UNFOLDED_PORTRAIT_HALF` (Book mode)
     * `UNFOLDED_PORTRAIT_FLAT` (Tablet portrait)
     * `FOLDED_PORTRAIT` (Cover screen phone mode)
     * `FOLDED_LANDSCAPE` (Cover screen landscape)
7. **JSON-Configurable & Hot-Reloadable**:
   - Layouts are plain human-readable JSON files.
   - Automatically exported on launch to:
     `/sdcard/Android/data/com.keyfold.terminal/files/layouts/`
   - Edit layout JSON files directly in Termux using `vim`, `nano`, or `helix`.
   - Hot-reload without leaving Termux via:
     ```bash
     am broadcast -a com.keyfold.terminal.RELOAD_CONFIG
     ```
   - Includes automatic layout version migration and in-app "Reset Defaults" / "Reload Layouts" buttons.

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
│       │   │   ├── unfolded_landscape_pda.json    # Default 5-row PDA layout (flex mode)
│       │   │   ├── unfolded_landscape_flat.json   # Tablet landscape layout
│       │   │   ├── unfolded_portrait.json         # Tablet portrait layout
│       │   │   ├── folded_portrait.json           # Samsung-style cover screen portrait
│       │   │   ├── folded_portrait_sym.json       # Cover screen symbols & numbers layer (!#1)
│       │   │   ├── folded_landscape.json          # Cover screen landscape
│       │   │   └── fn_layer.json                  # Function keys & navigation
│       │   ├── java/com/keyfold/terminal/
│       │   │   ├── model/LayoutModels.kt          # JSON data models (Keys, Rows, Spacers, Heights)
│       │   │   ├── posture/
│       │   │   │   ├── DevicePosture.kt           # Posture enums
│       │   │   │   └── DevicePostureDetector.kt   # WindowManager & sensor tracker
│       │   │   ├── layout/LayoutRepository.kt     # JSON loader, cache & version migration
│       │   │   ├── ime/
│       │   │   │   └── KeyFoldInputMethodService.kt # Core IME Service (insets, touch, repeat)
│       │   │   ├── ui/KeyFoldKeyboardView.kt      # Hardware Canvas view, haptics & long-press
│       │   │   └── settings/KeyFoldSettingsActivity.kt # Settings, Height Tuner & Live Preview
│       │   └── res/
│       │       ├── values/ (colors, strings, themes)
│       │       └── xml/method.xml
│       └── test/java/com/keyfold/terminal/
│           └── LayoutParsingTest.kt               # Validates all JSON layouts & postures
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

---

## Adjusting Keyboard Height & Settings

KeyFold includes a companion Settings app (accessible from your Android app launcher, or by tapping the `⚙` key in the cover screen symbol layer):

1. **Height Tuning Card**:
   - Select the target posture profile (e.g. *Tabletop PDA Mode*, *Tablet Landscape*, or *Cover Portrait*).
   - Choose your height sizing mode:
     - **Percentage of Screen** (default): Adjust between 25% and 65% using the slider or `+` / `-` 1% fine-tuning buttons.
     - **Snap Below Hinge**: Automatically anchors the keyboard to the fold crease in flex mode.
     - **Fixed DP**: Specify a fixed pixel height.
   - Adjustments write immediately to the active layout JSON file in storage and hot-reload the keyboard.
   - The embedded interactive preview scales in real time so you can verify the height without leaving the settings app.
2. **Reset Defaults**:
   - Tap **Reset Defaults** to re-export the factory JSON layouts to your storage if you ever want to revert manual edits.

