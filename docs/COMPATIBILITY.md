# Will it work on my TV?

StreamHome is Android software, so the first question is what your TV runs. Samsung and LG don't run Android, so
StreamHome can't be installed on them.

| Brand | Runs | StreamHome |
|---|---|---|
| **Samsung** | Tizen, on every current model | **No** |
| **LG** | webOS, on every current model | **No** |
| **Sony** | Google TV on current models, Android TV on older ones | **Expected to work.** The only brand StreamHome has fully run on |
| **TCL** | Mostly Google TV; some Fire TV and Roku | **Google TV models only** |
| **Xiaomi / Redmi** | Google TV, Fire TV or (China) HyperOS, depending on the series | **Google TV models only** |
| **Hisense** | VIDAA, Google TV, Fire TV or Roku, depending on model and country | **Google TV models only** |
| **OnePlus, Vu, Philips, Sharp, Panasonic, Acer** | Mostly Android TV or Google TV | **Expected to work**, with limits on some boards |

Not sure? On the TV, open Settings > About, or look for "Google TV" or "Android TV" on the box. Brand alone doesn't
settle it: TCL and Xiaomi sell both Google TV and Fire TV sets.

## What has actually been tested

StreamHome has three parts, and they are not equally proven:

- **Launcher:** the home screen. It comes from CleanYourTV, whose owners have run it on many TVs.
- **Feed:** new releases, ratings and Continue watching. It uses standard Android TV data, with no brand-specific code,
  but has only run on the BF1.
- **Ambient News:** stories drawn over the screensaver. It depends the most on the TV's firmware, and has only run on the
  BF1.

The notes below come from reports by CleanYourTV owners, not from StreamHome running on those sets. **Launcher reported**
means the launcher ran there. It doesn't mean the Feed or Ambient News did.

## Current models

### Sony

| Model | System | Status |
|---|---|---|
| BRAVIA BF1 | Google TV 12 | **Tested: all of StreamHome** |
| BRAVIA 9 II (K-65XR90M2, 2026) | Google TV 14 | Launcher reported: Home, Inputs, Input key and voice checked the same evening, not yet used for long |
| BRAVIA 4K AE2 | Google TV 14 | Launcher hand-over notes exist. One China-firmware set came up with a doubled UI density |
| A95L (BRAVIA 4K AE1) | Google TV 12 | Debloat list exists; launcher not confirmed |
| X75K (BRAVIA VU3) | Google TV 14 | Launcher reported. Netflix, YouTube and Prime Video remote buttons die if the stock launcher is disabled, so keep it enabled |
| BRAVIA TL2 | Android TV 11 | Debloat list exists; launcher not confirmed. About 1.7 GB RAM, see [low memory](#limits-on-every-brand) |

### TCL

| Model | System | Status |
|---|---|---|
| QM8L, C8L (G17 board, 2025) | Google TV 14 | Launcher hand-over notes exist |
| C845X2 (G08) | Google TV 14 | Launcher reported: Home and dock apps worked |
| QM6K, C7L, C7K, C755 (G08) | Google TV 14 | Same board as the C845X2. QM6K: Gear and YouTube buttons die with the stock launcher disabled; some sets need Wireless debugging pairing |
| RM7L, C89B (G08) | Google TV 14 | Debloat lists exist; the RM7L owner kept the stock launcher |
| **Any TCL Fire TV** (S5, S4, F series) | Fire OS | **No** |
| **Any TCL Roku TV** | Roku OS | **No** |

### Xiaomi and Redmi

| Model | System | Status |
|---|---|---|
| TV A Pro 2026 (MiTV-MZTU0) | Google TV 14 | Debloat applied on an India set; launcher result not recorded |
| TV A43 2025 (MiTV-MOOQ1) | Google TV 11 | Debloat list exists. The same code is also sold with PatchWall, so check which you have |
| X Pro QLED 2026 | Google TV | Not reported yet |
| TV Box S 3rd gen (MiTV-AFMU0) | Google TV 14 | Debloat list exists. USB devices go dead while USB debugging is on |
| Redmi 43" (MiTV-MOOQ3, India) | Android TV 11 | Debloat list exists. Low-memory mode, 1.7 GB RAM |
| **F and FX series** | Fire OS | **No** |
| **China sets on HyperOS TV 3** (S55 Mini LED tested) | HyperOS, Android 14 | **Home stays on PatchWall** on the set tested, and disabling it left the TV with no usable screen. Other HyperOS sets differ |

### Others

| Model | System | Status |
|---|---|---|
| Vu VIBE TV (India) | Google TV 14 | Launcher reported: stayed Home across two reboots, HDMI switching checked |
| Vu 4K (lushan, India) | Google TV 12 | Debloat list exists; launcher not confirmed |
| OnePlus TV (Cebu, Y1, India) | Android TV 10, 11 | Debloat lists exist; launcher not confirmed. The Y1 has about 0.9 GB RAM |
| Hisense U75 / 75U75N | Google TV 12 | Launcher can only own Home with the setup wizard disabled; check its Wi-Fi first |
| Philips R8G | Google TV 14 | Debloat list exists. About 1.8 GB RAM; disabling the setup wizard can drop Wi-Fi |
| Sharp AQUOS TVE23A | Google TV 14 | Launcher reported: stayed Home across two reboots |
| Panasonic 4KTV-3MUP, Acer R4G | Google TV 14 | **Never disable the stock launcher or setup wizard.** Home went black after a power cut on these boards. StreamHome stays in the app list |
| Google TV Streamer, onn. 4K Plus, NVIDIA Shield | Google TV 14 / Android TV 11 | Debloat lists exist; launcher notes in [Troubleshooting](TROUBLESHOOTING.md) |
| **Any Fire TV** set or stick | Fire OS, Vega OS | **No.** Amazon keeps the Home button |
| **Hisense VIDAA, any Roku TV** | VIDAA, Roku OS | **No** |

## Limits on every brand

- **Android 8 or newer** is required. Older sets refuse the install.
- **The Home button:** on some boards the stock launcher can't be replaced safely. StreamHome still opens from the app
  list; read "Working on the TV" in [Troubleshooting](TROUBLESHOOTING.md) before the hand-over.
- **Remote buttons:** on several Google TV boards, the Netflix, YouTube or Prime Video buttons stop working once the
  stock launcher is disabled.
- **The cleanup list** in `my-tv/` was written for the BF1. Don't apply it to any other TV.
- **Low memory:** StreamHome has only run on a TV with 2.4 GB of RAM. Sets under 2 GB are untested; artwork and animation
  may be slow, and low-memory mode may block the screensaver overlay.
- **Outside India:** the catalogue rows are hard-wired to India. Continue watching and the apps' own rows still work.

## Help fill this in

If you try StreamHome on a TV listed here or missing from it, please
[open an issue](https://github.com/rohan9405/streamhome-tv/issues) with the model, Android version, and whether Home,
the Feed and Ambient News worked.
