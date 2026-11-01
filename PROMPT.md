# Set up StreamHome with one prompt

Open this repository in any coding assistant that can run shell commands,
with the computer on the same Wi-Fi as the TV, and paste everything in the box below. Fill in the two lines at the
top first. The assistant does the rest and asks you before anything you would want a say in.

On the TV beforehand: Settings → Device Preferences → About → press OK on **Build** seven times → back → Developer
options → turn on **USB debugging** (and **Network debugging** or **ADB debugging** if the TV has it). Keep the TV on.

```text
Set up StreamHome on my TV from this repository.

My TV: <brand and model, for example Sony BRAVIA BF1>
Optional extras I want: <none | IMDb ratings and title details | portfolio chip (INDmoney) | news in the screensaver>

Follow the README's "For coding agents" section exactly, in order. Rules:
- Use this repo's scripts (tools/tv.py, launcher/build-and-install.sh, tools/install-addon.sh, my-tv/api-keys.sh,
  my-tv/indmoney-signin.py, my-tv/indmoney-pair.sh). Do not write your own replacements.
- Find the TV's IP address (ask me if you cannot), export TV_HOST, run `tools/tv.py --probe`, and ask me to accept
  "Allow USB debugging?" on the TV when it appears.
- Never print, log, commit or paste any API key, token or sign-in. Keys are entered only through
  my-tv/api-keys.sh save (hidden prompt) and stay in ~/.cleantv-private and on the TV.
- Any connector I skip must stay off without breaking anything else. Tell me what each skipped one would add.
- Before installing the launcher or pressing remote keys, check nothing is playing on the TV
  (dumpsys media_session state=3). If something is, wait and tell me.
- Install the launcher with STOCK_LAUNCHER=none first. Only after I confirm Home, apps, HDMI and the remote work,
  offer the stock-launcher hand-over and the optional debloat, showing me the package list first.
- After each step, verify it on the TV (screenshot with tools/tv.py --screenshot) and tell me what you saw.
- Finish with: what is installed, which extras are on, which are off and what key each needs, how to undo
  (tools/switch-home.sh stock, my-tv/restore.sh), and remind me to turn network debugging off.
```

## What you need on the computer

- Python 3, a JDK from 17 to 22, and Android SDK platform 34 with build-tools 34. The assistant can install them; or
  build in Docker with [tools/docker-build.md](tools/docker-build.md).
- Free keys only for the extras you want (each has a free tier):

| Extra | Key | Where to get it | Without it |
|---|---|---|---|
| IMDb ratings | OMDb API key | [omdbapi.com/apikey.aspx](https://www.omdbapi.com/apikey.aspx) | Only new catalogue titles get IMDb's rating; app tiles have none |
| New releases, genres, Read more | TMDB API Read Access Token | [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) | The header uses JustWatch; genres and Read more say they need TMDB |
| Portfolio and market chip | Your INDmoney sign-in | `python3 my-tv/indmoney-signin.py` (INDmoney's own page) | The chip stays hidden |
| A second news source | TheNewsAPI token | [thenewsapi.com](https://www.thenewsapi.com/) | Google News alone, which needs no key |

Every missing or refused key is reported where it matters (the Feed screen, the Read more popup, the Ambient News
settings) with the command that fixes it. Nothing else stops working.
