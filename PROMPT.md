# Set up StreamHome

These are instructions for the coding assistant doing the setup. The owner only pastes the one line from the
[README](README.md#set-it-up-with-one-prompt). Ask them for what you need, in plain words, one step at a time.

## 1. Check the TV can run it

Ask for the TV's brand and model, and which extras they want (none is fine): IMDb ratings and title details, the
portfolio chip (INDmoney), news in the screensaver.

Look the model up in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md). If it runs Tizen, webOS, Fire OS, Roku OS, VIDAA or
anything else that is not Android TV / Google TV, stop and say so plainly before they change anything. Do not try to
work around it. Point Samsung, LG and Roku owners to https://cleanyour.tv/not-android for turning off tracking. If the
model is not listed and they are unsure, ask them to read the home screen or Settings > About.

Say how well the model is known: only the Sony BRAVIA BF1 has run all of StreamHome, so on any other TV you are checking
it for the first time.

## 2. Get ready

- Clone https://github.com/rohan9405/streamhome-tv.git unless you are already in it, and work from there.
- The computer must be on the same network as the TV. Tell the owner what to do on the TV: Settings > Device Preferences
  > About > press OK on **Build** seven times > back > Developer options > turn on **USB debugging** (and **Network
  debugging** or **ADB debugging** if it is there). Keep the TV on.
- The computer needs Python 3, a JDK from 17 to 22, and Android SDK platform 34 with build-tools 34. Install what is
  missing, or build in Docker with [tools/docker-build.md](tools/docker-build.md).

## 3. Install

Follow the README's "For coding agents" section exactly, in order. Rules:

- Use this repo's scripts (tools/tv.py, launcher/build-and-install.sh, tools/install-addon.sh, my-tv/api-keys.sh,
  my-tv/indmoney-signin.py, my-tv/indmoney-pair.sh). Do not write your own replacements.
- Find the TV's IP address (ask if you cannot), export TV_HOST, run `tools/tv.py --probe`, and ask the owner to accept
  "Allow USB debugging?" on the TV when it appears. If the port stays closed, see docs/TROUBLESHOOTING.md, "Connecting".
- Check what the probe reports against step 1. A model string starting with `AFT`, or `com.amazon.tv.launcher` in
  `pm list packages`, means Fire OS: stop, whatever the brand on the box says.
- Never print, log, commit or paste any API key, token or sign-in. Keys are entered only through
  my-tv/api-keys.sh save (hidden prompt) and stay in ~/.cleantv-private and on the TV.
- Any connector they skip must stay off without breaking anything else. Tell them what each skipped one would add.
- Before installing the launcher or pressing remote keys, check nothing is playing on the TV
  (dumpsys media_session state=3). If something is, wait and tell them.
- After each step, verify it on the TV (screenshot with tools/tv.py --screenshot) and say what you saw.
- Install the launcher with STOCK_LAUNCHER=none first. Until the hand-over, the Home button still opens the stock
  launcher; StreamHome opens from the app list.
- Only after they confirm StreamHome, apps, HDMI and the remote work, offer the stock-launcher hand-over.

## 4. Anything but a Sony BRAVIA BF1

my-tv/ was written for that one TV. On any other model:

- Do not run `my-tv/restore.sh`, or apply `my-tv/debloat.txt`. Undo with `tools/switch-home.sh stock`.
- Read the model's row in docs/COMPATIBILITY.md and "Working on the TV" in docs/TROUBLESHOOTING.md before the hand-over.
  Some boards must keep the stock launcher, and on others disabling it kills remote buttons or drops Wi-Fi. If it is
  unclear for this board, leave Home alone: StreamHome still runs from the app list.
- For news in the screensaver, run the preview and show them the result. It has not been tested on other models; if the
  story does not appear or the remote misbehaves, turn it off in Apps > Ambient News and say so.
- If they are outside India, tell them before they add keys: catalogue rows are India's only, so new releases, genres and
  title links may not match their services. Continue watching and the apps' own rows are unaffected.

## 5. Finish

Report what is installed, which extras are on, which are off and what key each needs, and how to undo it. Remind them to
turn network debugging off. If the TV is not in docs/COMPATIBILITY.md, offer to send its model, Android version and what
worked as a GitHub issue, and send it only if they agree.

## Keys

Each has a free tier, and they only need the ones for the extras they chose.

| Extra | Key | Where to get it | Without it |
|---|---|---|---|
| IMDb ratings | OMDb API key | [omdbapi.com/apikey.aspx](https://www.omdbapi.com/apikey.aspx) | Only new catalogue titles get IMDb's rating; app tiles have none |
| New releases, genres, search, title logos, Read more | TMDB API Read Access Token | [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) | The header uses JustWatch and written titles; genres, search and Read more say they need TMDB |
| Portfolio and market chip | Their INDmoney sign-in | `python3 my-tv/indmoney-signin.py` (INDmoney's own page) | The chip stays hidden |
| A second news source | TheNewsAPI token | [thenewsapi.com](https://www.thenewsapi.com/) | Google News alone, which needs no key |

Every missing or refused key is reported where it matters (the Feed screen, the Read more popup, the Ambient News
settings) with the command that fixes it. Nothing else stops working.
