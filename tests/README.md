# DAI-LITE tests

Zero-dependency unit tests for the pure logic inside `index.html`.

## Run them

```sh
npm test
# or directly:
node --test "tests/*.test.mjs"
```

No install step, no build, no network. Requires Node 18+ (developed on Node 22).

## How it works

`index.html` ships as one self-contained file, so the tests treat it as the
source of truth: [`load-app.mjs`](load-app.mjs) reads `index.html`, extracts its
`<script>` block, and runs it with a few tiny "unsupported browser" stubs. The
script attaches its DOM-free logic to `window.DAILITE`, and the boot code is
guarded so nothing UI-related runs headlessly.

That means the tests exercise the **exact code that ships** — if a refactor
breaks the RUN: parser, the BLE 20-byte chunking, the request builder, or the
Flipper/ESP32 device profiles, `npm test` goes red.

## What's covered

- Device profiles, incl. a regression guard that the **exact Flipper UUIDs**
  and the ESP32-S3 Nordic UART Service UUIDs never drift.
- BLE framing + 20-byte chunking, including multi-byte UTF-8 that straddles
  chunk boundaries reassembling losslessly.
- The `RUN:` command parser (the only path from AI text to hardware):
  interleaving, whitespace, case sensitivity, and empty/null input.
- AI request building: URL joining, headers (incl. OpenRouter attribution),
  bounded `max_tokens`, model fallback, and safe reply extraction.
- Device-aware system prompt and provider presets.
- Settings normalization of bad/partial input.
