// Test loader for DAI-LITE.
//
// The app ships as a single self-contained index.html. To test the *real*
// shipped code (not a copy), we read index.html, pull out its <script>, and
// run it. We install a minimal, deliberately "unsupported browser" set of
// global stubs first (document.getElementById returns null, so the script's
// boot() guard is false and no DOM code runs), then evaluate the script in the
// current realm so the objects it returns share this realm's prototypes and
// compare cleanly with assert.deepStrictEqual. The script attaches its pure
// logic to globalThis.DAILITE.
//
// Zero dependencies on purpose: run with `node --test`.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const indexPath = join(here, "..", "index.html");

export function loadApp() {
  const html = readFileSync(indexPath, "utf8");

  // Grab the single application <script> block.
  const match = html.match(/<script>([\s\S]*?)<\/script>/);
  if (!match) throw new Error("Could not find <script> block in index.html");
  const source = match[1];

  // The browser globals the script needs that Node lacks. We pass these as
  // function parameters so they SHADOW the real globals (Node's `navigator`
  // is read-only and, conveniently, already advertises no Bluetooth). None
  // advertise Speech support, and getElementById returns null so boot() is
  // skipped. TextEncoder/TextDecoder/globalThis come from Node's real globals,
  // so the objects the script returns share this realm's prototypes and
  // compare cleanly with assert.deepStrictEqual.
  const store = new Map();
  const stubWindow = {};
  const stubLocation = { origin: "https://dai-lite.test" };
  const stubDocument = {
    getElementById: () => null,
    querySelectorAll: () => [],
    createElement: () => ({ style: {}, classList: { add() {}, remove() {}, toggle() {} }, appendChild() {}, addEventListener() {} }),
    addEventListener: () => {},
  };
  const stubLocalStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
    clear: () => store.clear(),
  };

  // new Function runs the script in global scope with our stubs shadowing the
  // named globals; the script assigns globalThis.DAILITE.
  const runner = new Function("window", "document", "location", "localStorage", source);
  runner(stubWindow, stubDocument, stubLocation, stubLocalStorage);

  if (!globalThis.DAILITE) throw new Error("index.html did not expose globalThis.DAILITE");
  return globalThis.DAILITE;
}

// Rebuild the original string from chunked byte arrays (mirrors what BLE writes send).
export function decodeChunks(chunks) {
  const total = chunks.reduce((n, c) => n + c.length, 0);
  const joined = new Uint8Array(total);
  let off = 0;
  for (const c of chunks) { joined.set(c, off); off += c.length; }
  return new TextDecoder().decode(joined);
}
