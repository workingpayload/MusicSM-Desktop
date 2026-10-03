# Signed-in YouTube playback scripts

These run inside the hidden Edge/Chrome tab that `BrowserPlayerScript` drives. They are loaded
from the classpath at run time; nothing here is evaluated by the JVM.

| File | What it is | Source / licence |
|------|------------|------------------|
| `solver-lib.js`, `solver-core.js` | yt-dlp's ejs solver: finds the signature and `n` functions in YouTube's player script | `yt_dlp_ejs/yt/solver/{lib,core}.min.js` from the `yt-dlp-ejs` 0.8.0 wheel on PyPI. Unlicense; bundles meriyah (ISC) and astring (MIT), notices kept in the file |
| `botguard.js` | Mints proof-of-origin (PO) tokens with YouTube's BotGuard | bgutils-js 4.0.3 (MIT), bundled with esbuild from the entry below |
| `player-host.js` | MusicSM glue: solver worker, PO-token minter cache | This project |

## Updating

**Solver:** download the newest `yt_dlp_ejs-*.whl` from https://pypi.org/project/yt-dlp-ejs/,
unzip it and copy `lib.min.js` -> `solver-lib.js` and `core.min.js` -> `solver-core.js`.

**BotGuard:** in an empty folder, `npm i bgutils-js esbuild`, save the entry below as
`bg-entry.js` and run
`npx esbuild bg-entry.js --bundle --format=iife --minify --legal-comments=none --outfile=botguard.js`.
Keep the licence header at the top of the committed file.

```js
import { BotGuardClient, getChallenge } from 'bgutils-js/botguard';
import { WebPoMinter } from 'bgutils-js/webpo';
import { buildURL, getHeaders } from 'bgutils-js/utils';

// YouTube web player's request key for the Web Anti-Abuse API.
const REQUEST_KEY = 'O43z0dpjhgX20SCx4KAo';

window.__msmBotGuard = {
  async createMinter() {
    const challenge = await getChallenge({ requestKey: REQUEST_KEY, fetchFunction: (url, init) => fetch(url, init) });
    const script = challenge.interpreterJavascript && challenge.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;
    if (!script) throw new Error('BotGuard interpreter unavailable');
    new Function(script)();
    const client = await BotGuardClient.create({ program: challenge.program, globalName: challenge.globalName, globalObject: window });
    const webPoSignalOutput = [];
    const botguardResponse = await client.snapshot({ webPoSignalOutput });
    const response = await fetch(buildURL('GenerateIT'), {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify([REQUEST_KEY, botguardResponse]),
    });
    if (!response.ok) throw new Error('Integrity token request failed: ' + response.status);
    const [integrityToken, ttlSecs, refreshThresholdSecs] = await response.json();
    if (!integrityToken) throw new Error('No integrity token');
    const minter = await WebPoMinter.create({ integrityToken }, webPoSignalOutput);
    return { minter, ttlSecs: ttlSecs || 0, refreshThresholdSecs: refreshThresholdSecs || 0 };
  },
};
```