/*
 * MusicSM Desktop: runs inside the hidden browser tab that BrowserPlayerScript drives.
 *
 * window.__msmSolverSrc (yt-dlp's ejs solver) and botguard.js are injected first. The solver runs
 * in a worker because it assigns globalThis.location, which would navigate a page away.
 */
(() => {
  if (window.__msm) return;

  const workerSource = window.__msmSolverSrc + `
    let cache = null;
    async function load(url) {
      if (cache && cache.url === url) return cache;
      const response = await fetch(url);
      if (!response.ok) throw new Error("player script HTTP " + response.status);
      const player = await response.text();
      const sts = player.match(/signatureTimestamp[:=]\\s*(\\d+)/) || player.match(/[,{]sts:(\\d+)/);
      const out = jsc({ type: "player", player, requests: [], output_preprocessed: true });
      cache = { url, preprocessed: out.preprocessed_player, sts: sts ? Number(sts[1]) : null };
      return cache;
    }
    onmessage = async (event) => {
      const { id, op, url, sig, n } = event.data;
      try {
        const player = await load(url);
        if (op === "sts") {
          if (player.sts == null) throw new Error("no signature timestamp in player script");
          postMessage({ id, result: player.sts });
          return;
        }
        const out = jsc({
          type: "preprocessed",
          preprocessed_player: player.preprocessed,
          requests: [{ type: "sig", challenges: sig }, { type: "n", challenges: n }],
        });
        const [sigResponse, nResponse] = out.responses;
        if (sigResponse.type !== "result") throw new Error("sig: " + sigResponse.error);
        if (nResponse.type !== "result") throw new Error("n: " + nResponse.error);
        postMessage({ id, result: { sig: sigResponse.data, n: nResponse.data } });
      } catch (error) {
        postMessage({ id, error: String((error && error.message) || error) });
      }
    };`;

  const worker = new Worker(URL.createObjectURL(new Blob([workerSource], { type: "text/javascript" })));
  const waiting = new Map();
  let nextId = 0;
  let workerError = null;
  worker.onmessage = (event) => {
    const entry = waiting.get(event.data.id);
    if (!entry) return;
    waiting.delete(event.data.id);
    if (event.data.error) entry.reject(new Error(event.data.error));
    else entry.resolve(event.data.result);
  };
  worker.onerror = (event) => {
    workerError = new Error("solver worker failed: " + (event.message || "unknown error"));
    for (const entry of waiting.values()) entry.reject(workerError);
    waiting.clear();
  };
  const call = (message) => new Promise((resolve, reject) => {
    if (workerError) return reject(workerError);
    const id = ++nextId;
    waiting.set(id, { resolve, reject });
    worker.postMessage({ id, ...message });
  });

  let minterState = null;
  async function minter() {
    if (minterState && Date.now() < minterState.refreshAt) return minterState.minter;
    const created = await window.__msmBotGuard.createMinter();
    const usableSecs = Math.max(300, created.ttlSecs - created.refreshThresholdSecs);
    minterState = { minter: created.minter, refreshAt: Date.now() + usableSecs * 1000 };
    return minterState.minter;
  }

  window.__msm = {
    signatureTimestamp: (url) => call({ op: "sts", url }),
    solve: (url, sig, n) => call({ op: "solve", url, sig, n }),
    async mintPoToken(contentBinding) {
      try {
        return await (await minter()).mintAsWebsafeString(contentBinding);
      } catch (error) {
        minterState = null;
        throw error;
      }
    },
  };
})();
