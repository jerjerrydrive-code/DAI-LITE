// DAI-LITE unit tests — run with:  node --test   (or)   npm test
//
// These exercise the pure logic exported on window.DAILITE by index.html.
// Goal: prove the tricky, safety-critical bits (BLE framing/chunking, the
// RUN: command parser, request building, and device profiles) behave exactly
// as intended — kept as simple and readable as possible.

import { test } from "node:test";
import assert from "node:assert/strict";
import { loadApp, decodeChunks } from "./load-app.mjs";

const D = loadApp();

/* ========================================================================
   Device profiles + the exact Flipper UUIDs (regression guard)
   ======================================================================== */
test("Flipper UUIDs match the required spec values exactly", () => {
  assert.equal(D.SERIAL_SERVICE, "8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000");
  assert.equal(D.TX_CHAR,        "19ed82ae-ed21-4c9d-4145-228e62fe0000");
  assert.equal(D.RX_CHAR,        "19ed82ae-ed21-4c9d-4145-228e61fe0000");

  const f = D.DEVICE_PROFILES.flipper;
  assert.equal(f.service, D.SERIAL_SERVICE);
  assert.equal(f.txChar,  D.TX_CHAR);
  assert.equal(f.rxChar,  D.RX_CHAR);
  assert.equal(f.namePrefix, "Flipper");
  assert.equal(f.lineEnding, "\r");
  assert.equal(f.chunkSize, 20);
});

test("ESP32-S3 profile uses the Nordic UART Service UUIDs", () => {
  const e = D.DEVICE_PROFILES.esp32;
  assert.equal(e.service, "6e400001-b5a3-f393-e0a9-e50e24dcca9e");
  assert.equal(e.txChar,  "6e400002-b5a3-f393-e0a9-e50e24dcca9e"); // we write here
  assert.equal(e.rxChar,  "6e400003-b5a3-f393-e0a9-e50e24dcca9e"); // notifications
  assert.equal(e.lineEnding, "\n");
});

test("getActiveProfile defaults to Flipper", () => {
  assert.equal(D.getActiveProfile(undefined).key, "flipper");
  assert.equal(D.getActiveProfile({}).key, "flipper");
  assert.equal(D.getActiveProfile({ deviceProfile: "nope" }).key, "flipper");
});

test("getActiveProfile returns the built-in ESP32 profile", () => {
  const p = D.getActiveProfile({ deviceProfile: "esp32" });
  assert.equal(p.key, "esp32");
  assert.equal(p.service, "6e400001-b5a3-f393-e0a9-e50e24dcca9e");
});

test("getActiveProfile builds a custom profile from settings (lowercased, trimmed)", () => {
  const p = D.getActiveProfile({
    deviceProfile: "custom",
    custom: {
      label: "MyBoard", namePrefix: "MyBoard",
      service: "  ABCD  ", txChar: "1234", rxChar: "5678", lineEnding: "lf",
    },
  });
  assert.equal(p.key, "custom");
  assert.equal(p.label, "MyBoard");
  assert.equal(p.service, "abcd");     // trimmed + lowercased
  assert.equal(p.txChar, "1234");
  assert.equal(p.rxChar, "5678");
  assert.equal(p.lineEnding, "\n");    // "lf" -> \n
});

/* ---- Auto-detection ---- */
test("candidateProfiles offers the built-ins by default", () => {
  const list = D.candidateProfiles({});
  assert.deepEqual(list.map((p) => p.key), ["flipper", "esp32"]);
});

test("candidateProfiles appends a complete saved custom profile", () => {
  const list = D.candidateProfiles({
    custom: { namePrefix: "MyBoard", service: "abcd", txChar: "1234", rxChar: "5678" },
  });
  assert.deepEqual(list.map((p) => p.key), ["flipper", "esp32", "custom"]);
});

test("candidateProfiles ignores an incomplete custom profile", () => {
  const list = D.candidateProfiles({ custom: { namePrefix: "X" } }); // no UUIDs
  assert.deepEqual(list.map((p) => p.key), ["flipper", "esp32"]);
});

test("buildRequestOptions for a single Flipper matches the spec filter exactly", () => {
  const opts = D.buildRequestOptions([D.DEVICE_PROFILES.flipper]);
  assert.deepEqual(opts, {
    filters: [{ namePrefix: "Flipper" }],
    optionalServices: ["8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000"],
  });
});

test("buildRequestOptions in auto mode filters by both names and both services", () => {
  const opts = D.buildRequestOptions([D.DEVICE_PROFILES.flipper, D.DEVICE_PROFILES.esp32]);
  assert.ok(opts.filters.some((f) => f.namePrefix === "Flipper"));
  assert.ok(opts.filters.some((f) => f.namePrefix === "ESP32"));
  assert.ok(opts.filters.some((f) => Array.isArray(f.services) && f.services[0] === D.DEVICE_PROFILES.flipper.service));
  assert.ok(opts.filters.some((f) => Array.isArray(f.services) && f.services[0] === D.DEVICE_PROFILES.esp32.service));
  assert.equal(opts.optionalServices.length, 2);
});

test("buildRequestOptions falls back to a service filter when a profile has no name", () => {
  const custom = { key: "custom", label: "C", namePrefix: "", service: "svc-x", txChar: "t", rxChar: "r" };
  const opts = D.buildRequestOptions([custom]);
  assert.deepEqual(opts, { filters: [{ services: ["svc-x"] }], optionalServices: ["svc-x"] });
});

test("buildRequestOptions shows all devices when nothing is filterable", () => {
  const bare = { key: "custom", label: "C", namePrefix: "", service: "", txChar: "", rxChar: "" };
  const opts = D.buildRequestOptions([bare]);
  assert.equal(opts.acceptAllDevices, true);
});

test("matchProfileByServices picks the profile whose service is present (case-insensitive)", () => {
  const profiles = [D.DEVICE_PROFILES.flipper, D.DEVICE_PROFILES.esp32];
  assert.equal(D.matchProfileByServices(profiles, [D.DEVICE_PROFILES.esp32.service]).key, "esp32");
  assert.equal(D.matchProfileByServices(profiles, ["8FE5B3D5-2E7F-4A98-2A48-7ACC60FE0000"]).key, "flipper"); // upper-case
  assert.equal(D.matchProfileByServices(profiles, ["nothing-known"]), null);
  assert.equal(D.matchProfileByServices(profiles, []), null);
});

test("profileIsComplete requires all three UUIDs", () => {
  assert.equal(D.profileIsComplete(D.DEVICE_PROFILES.flipper), true);
  assert.equal(D.profileIsComplete({ service: "x", txChar: "y" }), false);
  assert.equal(D.profileIsComplete({ service: "", txChar: "", rxChar: "" }), false);
  assert.equal(D.profileIsComplete(null), false);
});

test("lineEndingFromCode maps codes to characters", () => {
  assert.equal(D.lineEndingFromCode("cr"), "\r");
  assert.equal(D.lineEndingFromCode("lf"), "\n");
  assert.equal(D.lineEndingFromCode("crlf"), "\r\n");
  assert.equal(D.lineEndingFromCode(undefined), "\r"); // default
});

/* ========================================================================
   BLE framing + 20-byte chunking
   ======================================================================== */
test("frameCommand appends the line ending and encodes UTF-8", () => {
  assert.deepEqual([...D.frameCommand("ab", "\r")], [97, 98, 13]);
  assert.deepEqual([...D.frameCommand("ab", "\n")], [97, 98, 10]);
  assert.deepEqual([...D.frameCommand("ab", "\r\n")], [97, 98, 13, 10]);
  assert.deepEqual([...D.frameCommand("x")], [120, 13]); // default CR
});

test("chunkBytes splits into <=20-byte chunks by default", () => {
  const bytes = new Uint8Array(45);
  const chunks = D.chunkBytes(bytes);
  assert.equal(chunks.length, 3);            // 20 + 20 + 5
  assert.equal(chunks[0].length, 20);
  assert.equal(chunks[1].length, 20);
  assert.equal(chunks[2].length, 5);
});

test("chunkBytes handles boundaries: empty, exact, and one-over", () => {
  assert.equal(D.chunkBytes(new Uint8Array(0)).length, 0);
  assert.equal(D.chunkBytes(new Uint8Array(20)).length, 1);
  const over = D.chunkBytes(new Uint8Array(21));
  assert.equal(over.length, 2);
  assert.equal(over[1].length, 1);
});

test("chunkBytes respects a custom chunk size", () => {
  const chunks = D.chunkBytes(new Uint8Array(10), 4);
  assert.deepEqual(chunks.map((c) => c.length), [4, 4, 2]);
});

test("frame -> chunk -> reassemble round-trips a long command", () => {
  const cmd = "storage read /ext/subghz/a_very_long_capture_name.sub";
  const chunks = D.chunkBytes(D.frameCommand(cmd, "\r"), 20);
  assert.ok(chunks.length > 1, "should span multiple BLE writes");
  assert.equal(decodeChunks(chunks), cmd + "\r");
});

test("chunking never corrupts multi-byte UTF-8 on reassembly", () => {
  // Emoji + accents deliberately straddle 20-byte chunk boundaries.
  const cmd = "note café ☕ signal 📡 straddling the twenty byte edge!!";
  const chunks = D.chunkBytes(D.frameCommand(cmd, "\n"), 20);
  assert.equal(decodeChunks(chunks), cmd + "\n");
});

/* ========================================================================
   RUN: command parser — the ONLY path from AI text to hardware
   ======================================================================== */
test("parseAssistant returns a single text segment for plain prose", () => {
  const segs = D.parseAssistant("Your battery is fine.");
  assert.deepEqual(segs, [{ type: "text", text: "Your battery is fine." }]);
});

test("parseAssistant extracts a RUN: command", () => {
  const segs = D.parseAssistant("Checking battery.\nRUN: power info");
  assert.deepEqual(segs, [
    { type: "text", text: "Checking battery." },
    { type: "run", command: "power info" },
  ]);
});

test("parseAssistant handles multiple commands interleaved with prose", () => {
  const segs = D.parseAssistant("First:\nRUN: device_info\nThen list files:\nRUN: storage list /ext");
  assert.equal(segs.length, 4);
  assert.deepEqual(segs.map((s) => s.type), ["text", "run", "text", "run"]);
  assert.equal(segs[1].command, "device_info");
  assert.equal(segs[3].command, "storage list /ext");
});

test("parseAssistant trims surrounding whitespace on the command", () => {
  const segs = D.parseAssistant("RUN:    vibro 1   ");
  assert.deepEqual(segs, [{ type: "run", command: "vibro 1" }]);
});

test("parseAssistant is case-sensitive: lowercase 'run:' is NOT a command", () => {
  const segs = D.parseAssistant("run: not a command");
  assert.deepEqual(segs, [{ type: "text", text: "run: not a command" }]);
});

test("parseAssistant tolerates empty / null input", () => {
  assert.deepEqual(D.parseAssistant(""), []);
  assert.deepEqual(D.parseAssistant(null), []);
  assert.deepEqual(D.parseAssistant(undefined), []);
});

test("parseAssistant ignores a bare 'RUN:' with no command", () => {
  const segs = D.parseAssistant("RUN:");
  assert.deepEqual(segs, [{ type: "text", text: "RUN:" }]); // no command captured
});

test("stripRunLines removes commands and collapses whitespace for TTS", () => {
  const spoken = D.stripRunLines("Let me check.\nRUN: power info\nDone.");
  assert.equal(spoken, "Let me check. Done.");
});

test("pronounceForSpeech makes the brand say 'daylight'", () => {
  assert.equal(D.pronounceForSpeech("Hi, DAI-LITE here."), "Hi, Daylight here.");
  assert.equal(D.pronounceForSpeech("dai-lite / DAI LITE / Dai"), "Daylight / Daylight / Day");
  assert.equal(D.pronounceForSpeech("the sundial is fine"), "the sundial is fine"); // no false match
});

test("Puter preset uses the keyless sentinel base URL", () => {
  assert.equal(D.PRESETS.puter.baseUrl, "puter");
});

/* ========================================================================
   Device output -> AI (closing the loop)
   ======================================================================== */
test("appendCapture accumulates streamed text", () => {
  let buf = "";
  buf = D.appendCapture(buf, "line1\n");
  buf = D.appendCapture(buf, "line2");
  assert.equal(buf, "line1\nline2");
});

test("appendCapture keeps only the tail once past the cap", () => {
  const buf = D.appendCapture("", "abcdefghij", 4); // cap = 4
  assert.equal(buf, "ghij");
  const grown = D.appendCapture("wxyz", "1234", 4);
  assert.equal(grown, "1234");
});

test("appendCapture tolerates null inputs", () => {
  assert.equal(D.appendCapture(null, null), "");
  assert.equal(D.appendCapture(undefined, "x"), "x");
});

test("formatDeviceOutput fences the output and names the command", () => {
  const msg = D.formatDeviceOutput("power info", "  Battery: 87%  ");
  assert.equal(msg, "Device output for `power info`:\n```\nBattery: 87%\n```");
});

test("formatDeviceOutput handles a missing command", () => {
  const msg = D.formatDeviceOutput("", "hello");
  assert.equal(msg, "Device output:\n```\nhello\n```");
});

/* ========================================================================
   AI request building
   ======================================================================== */
test("resolveChatUrl joins the path without doubling slashes", () => {
  assert.equal(D.resolveChatUrl("https://openrouter.ai/api/v1"), "https://openrouter.ai/api/v1/chat/completions");
  assert.equal(D.resolveChatUrl("https://openrouter.ai/api/v1/"), "https://openrouter.ai/api/v1/chat/completions");
  assert.equal(D.resolveChatUrl("http://LAN-IP:11434/v1///"), "http://LAN-IP:11434/v1/chat/completions");
});

test("buildHeaders sets Authorization only when a key is present", () => {
  assert.equal(D.buildHeaders({ apiKey: "sk-123", baseUrl: "x" }).Authorization, "Bearer sk-123");
  assert.equal(D.buildHeaders({ apiKey: "", baseUrl: "x" }).Authorization, undefined);
  assert.equal(D.buildHeaders({ baseUrl: "x" })["Content-Type"], "application/json");
});

test("buildHeaders adds OpenRouter attribution headers only for openrouter.ai", () => {
  const or = D.buildHeaders({ apiKey: "k", baseUrl: "https://openrouter.ai/api/v1" }, "https://site.test");
  assert.equal(or["HTTP-Referer"], "https://site.test");
  assert.equal(or["X-Title"], "DAI-LITE");

  const other = D.buildHeaders({ apiKey: "k", baseUrl: "https://integrate.api.nvidia.com/v1" }, "https://site.test");
  assert.equal(other["HTTP-Referer"], undefined);
  assert.equal(other["X-Title"], undefined);
});

test("buildRequestBody puts the system prompt first and bounds max_tokens", () => {
  const body = D.buildRequestBody("SYS", [{ role: "user", content: "hi" }], { model: "m" });
  assert.equal(body.model, "m");
  assert.equal(body.messages[0].role, "system");
  assert.equal(body.messages[0].content, "SYS");
  assert.equal(body.messages[1].content, "hi");
  assert.equal(body.max_tokens, 800);   // prevents 402 on low-credit accounts
  assert.equal(body.stream, false);
});

test("buildRequestBody falls back to a default model", () => {
  const body = D.buildRequestBody("SYS", [], {});
  assert.equal(body.model, "gpt-4o-mini");
  assert.equal(body.messages.length, 1); // just the system message
});

/* ---- Streaming (SSE) ---- */
test("buildRequestBody sets stream only when asked", () => {
  assert.equal(D.buildRequestBody("s", [], {}).stream, false);
  assert.equal(D.buildRequestBody("s", [], {}, true).stream, true);
});

test("parseSseEvents returns complete events and keeps the partial tail", () => {
  const r = D.parseSseEvents('data: {"a":1}\n\ndata: {"b":2}\n\ndata: {"c"');
  assert.deepEqual(r.events, ['{"a":1}', '{"b":2}']);
  assert.equal(r.rest, 'data: {"c"');   // incomplete — carried to the next read
});

test("parseSseEvents handles CRLF and the [DONE] sentinel", () => {
  const r = D.parseSseEvents('data: {"a":1}\r\n\r\ndata: [DONE]\r\n\r\n');
  assert.deepEqual(r.events, ['{"a":1}', "[DONE]"]);
  assert.equal(r.rest, "");
});

test("parseSseEvents ignores comments/keep-alives and is safe on junk", () => {
  const r = D.parseSseEvents(": keep-alive\n\ndata: {\"a\":1}\n\n");
  assert.deepEqual(r.events, ['{"a":1}']);
  assert.deepEqual(D.parseSseEvents("").events, []);
  assert.deepEqual(D.parseSseEvents(null).events, []);
});

test("parseSseEvents joins multi-line data blocks per the SSE spec", () => {
  const r = D.parseSseEvents('data: {"choices":[{"delta":\ndata: {"content":"hi"}}]}\n\n');
  assert.equal(r.events.length, 1);
  assert.equal(r.events[0], '{"choices":[{"delta":\n{"content":"hi"}}]}');
});

test("extractDelta pulls streamed text and is safe on malformed chunks", () => {
  assert.equal(D.extractDelta({ choices: [{ delta: { content: "Hel" } }] }), "Hel");
  assert.equal(D.extractDelta({ choices: [{ delta: {} }] }), "");   // role-only first chunk
  assert.equal(D.extractDelta({ choices: [] }), "");
  assert.equal(D.extractDelta(null), "");
});

test("a full streamed reply reassembles into the same text", () => {
  // Simulate chunks split at awkward byte boundaries.
  const wire = 'data: {"choices":[{"delta":{"role":"assistant"}}]}\n\n' +
               'data: {"choices":[{"delta":{"content":"Checking.\\n"}}]}\n\n' +
               'data: {"choices":[{"delta":{"content":"RUN: power info"}}]}\n\n' +
               "data: [DONE]\n\n";
  let buf = "", full = "";
  for (const piece of [wire.slice(0, 40), wire.slice(40, 130), wire.slice(130)]) {
    buf += piece;
    const p = D.parseSseEvents(buf);
    buf = p.rest;
    for (const ev of p.events) {
      if (ev === "[DONE]") continue;
      full += D.extractDelta(JSON.parse(ev));
    }
  }
  assert.equal(full, "Checking.\nRUN: power info");
  // and it still parses into prose + a confirmable command
  assert.deepEqual(D.parseAssistant(full), [
    { type: "text", text: "Checking." },
    { type: "run", command: "power info" },
  ]);
});

test("extractReply pulls content and is safe on malformed responses", () => {
  assert.equal(D.extractReply({ choices: [{ message: { content: "hello" } }] }), "hello");
  assert.equal(D.extractReply({}), "");
  assert.equal(D.extractReply({ choices: [] }), "");
  assert.equal(D.extractReply(null), "");
});

/* ========================================================================
   Terminal command history (shell-style Up/Down)
   ======================================================================== */
test("pushHistory appends, skips blanks and consecutive duplicates", () => {
  let h = [];
  h = D.pushHistory(h, "device_info");
  h = D.pushHistory(h, "   ");          // blank -> ignored
  h = D.pushHistory(h, "device_info");  // same as last -> ignored
  h = D.pushHistory(h, "power info");
  assert.deepEqual(h, ["device_info", "power info"]);
});

test("pushHistory trims entries and caps the list length", () => {
  assert.deepEqual(D.pushHistory([], "  vibro 1  "), ["vibro 1"]);
  let h = [];
  for (let i = 0; i < 10; i++) h = D.pushHistory(h, "cmd" + i, 4);
  assert.deepEqual(h, ["cmd6", "cmd7", "cmd8", "cmd9"]); // oldest dropped
});

test("navigateHistory walks back through older commands and stops at the start", () => {
  const h = ["a", "b", "c"];
  let r = D.navigateHistory(h, 3, -1);      // from the fresh line, Up
  assert.deepEqual(r, { index: 2, value: "c" });
  r = D.navigateHistory(h, r.index, -1);
  assert.deepEqual(r, { index: 1, value: "b" });
  r = D.navigateHistory(h, 0, -1);          // already oldest -> clamp
  assert.deepEqual(r, { index: 0, value: "a" });
});

test("navigateHistory walks forward and lands on an empty fresh line", () => {
  const h = ["a", "b", "c"];
  let r = D.navigateHistory(h, 1, 1);
  assert.deepEqual(r, { index: 2, value: "c" });
  r = D.navigateHistory(h, 2, 1);           // past the newest -> blank line
  assert.deepEqual(r, { index: 3, value: "" });
  r = D.navigateHistory(h, 3, 1);           // clamp at the fresh line
  assert.deepEqual(r, { index: 3, value: "" });
});

test("navigateHistory is safe with empty history", () => {
  assert.deepEqual(D.navigateHistory([], 0, -1), { index: 0, value: "" });
  assert.deepEqual(D.navigateHistory(null, 0, 1), { index: 0, value: "" });
});

/* ========================================================================
   Models + saved provider library
   ======================================================================== */
test("resolveModelsUrl joins the /models path without doubling slashes", () => {
  assert.equal(D.resolveModelsUrl("https://openrouter.ai/api/v1"), "https://openrouter.ai/api/v1/models");
  assert.equal(D.resolveModelsUrl("https://openrouter.ai/api/v1/"), "https://openrouter.ai/api/v1/models");
});

test("parseModels handles the OpenAI {data:[{id}]} shape (de-duped, sorted)", () => {
  const ids = D.parseModels({ data: [{ id: "gpt-4o-mini" }, { id: "gpt-4o" }, { id: "gpt-4o-mini" }] });
  assert.deepEqual(ids, ["gpt-4o", "gpt-4o-mini"]);
});

test("parseModels handles {models:[...]}, bare arrays, and objects", () => {
  assert.deepEqual(D.parseModels({ models: ["llama3.1", "mistral"] }), ["llama3.1", "mistral"]);
  assert.deepEqual(D.parseModels(["b", "a"]), ["a", "b"]);
  assert.deepEqual(D.parseModels([{ name: "x" }]), ["x"]);
});

test("parseModels is safe on junk input", () => {
  assert.deepEqual(D.parseModels(null), []);
  assert.deepEqual(D.parseModels({}), []);
  assert.deepEqual(D.parseModels({ data: "nope" }), []);
});

test("provider library: upsert adds then replaces by name", () => {
  let list = [];
  list = D.upsertProvider(list, { name: "A", apiKey: "k1" });
  list = D.upsertProvider(list, { name: "B", apiKey: "k2" });
  assert.deepEqual(list.map((p) => p.name), ["A", "B"]);
  list = D.upsertProvider(list, { name: "A", apiKey: "k1-new" }); // replace, not duplicate
  assert.equal(list.length, 2);
  assert.equal(D.findProvider(list, "A").apiKey, "k1-new");
});

test("mergeProviders imports arrays and {dailite_providers} and upserts by name", () => {
  const existing = [{ name: "A", apiKey: "old" }];
  let r = D.mergeProviders(existing, { dailite_providers: [{ name: "A", apiKey: "new" }, { name: "B", apiKey: "k" }] });
  assert.equal(r.count, 2);
  assert.deepEqual(r.list.map(p => p.name), ["A", "B"]);
  assert.equal(D.findProvider(r.list, "A").apiKey, "new");   // replaced, not duplicated
  // bare array + junk entries
  r = D.mergeProviders([], [{ name: "X" }, null, { noName: 1 }]);
  assert.deepEqual(r.list.map(p => p.name), ["X"]);
  assert.equal(r.count, 1);
});

test("provider library: remove and find", () => {
  const list = [{ name: "A" }, { name: "B" }];
  assert.deepEqual(D.removeProvider(list, "A").map((p) => p.name), ["B"]);
  assert.equal(D.findProvider(list, "B").name, "B");
  assert.equal(D.findProvider(list, "Z"), null);
  assert.deepEqual(D.removeProvider(null, "A"), []);
});

/* ========================================================================
   Neural TTS (better voices)
   ======================================================================== */
test("resolveTtsUrl joins the /audio/speech path without doubling slashes", () => {
  assert.equal(D.resolveTtsUrl("https://api.openai.com/v1"), "https://api.openai.com/v1/audio/speech");
  assert.equal(D.resolveTtsUrl("https://api.openai.com/v1/"), "https://api.openai.com/v1/audio/speech");
});

test("ttsHeaders sets Authorization only when a key is present", () => {
  assert.equal(D.ttsHeaders({ ttsKey: "sk-x" }).Authorization, "Bearer sk-x");
  assert.equal(D.ttsHeaders({}).Authorization, undefined);
  assert.equal(D.ttsHeaders({})["Content-Type"], "application/json");
});

test("buildTtsBody carries model/voice/input and includes instructions when set", () => {
  const body = D.buildTtsBody({ ttsModel: "gpt-4o-mini-tts", ttsVoice: "onyx", ttsInstructions: "  calm narrator  " }, "hello");
  assert.equal(body.model, "gpt-4o-mini-tts");
  assert.equal(body.voice, "onyx");
  assert.equal(body.input, "hello");
  assert.equal(body.response_format, "mp3");
  assert.equal(body.instructions, "calm narrator"); // trimmed
});

test("buildTtsBody omits empty instructions and falls back to defaults", () => {
  const body = D.buildTtsBody({}, "hi");
  assert.equal(body.model, "gpt-4o-mini-tts");
  assert.equal(body.voice, "nova");
  assert.equal("instructions" in body, false);
});

test("voice personas map to a real voice id and a tone instruction", () => {
  const voiceIds = new Set(D.TTS_VOICES.map((v) => v.id));
  for (const key of Object.keys(D.VOICE_PERSONAS)) {
    const p = D.VOICE_PERSONAS[key];
    assert.ok(voiceIds.has(p.ttsVoice), key + " uses a known voice id");
    assert.ok(p.ttsInstructions.length > 0, key + " has a tone instruction");
  }
});

test("styleToProsody returns sane rate/pitch and a natural default", () => {
  assert.deepEqual(D.styleToProsody("natural"), { rate: 1.0, pitch: 1.0 });
  assert.deepEqual(D.styleToProsody("deep"), { rate: 0.9, pitch: 0.75 });
  assert.deepEqual(D.styleToProsody("whatever"), { rate: 1.0, pitch: 1.0 });
});

test("normalizeVoice coerces engine and fills defaults (Warm-woman persona seeded)", () => {
  assert.equal(D.normalizeVoice({ engine: "neural" }).engine, "neural");
  assert.equal(D.normalizeVoice({ engine: "puter" }).engine, "puter");
  assert.equal(D.normalizeVoice({ engine: "bogus" }).engine, "device");
  const d = D.normalizeVoice(undefined);
  assert.equal(d.ttsVoice, "nova");
  assert.equal(d.ttsModel, "gpt-4o-mini-tts");
  assert.equal(d.ttsInstructions, D.VOICE_PERSONAS.warmWoman.ttsInstructions);
  // A user who deliberately clears the tone box keeps it empty.
  assert.equal(D.normalizeVoice({ ttsInstructions: "" }).ttsInstructions, "");
});

/* ========================================================================
   System prompt is device-aware and always teaches the RUN: protocol
   ======================================================================== */
test("buildSystemPrompt uses a custom persona but keeps the RUN: mechanics", () => {
  const p = D.buildSystemPrompt(D.DEVICE_PROFILES.flipper, "  I am Jerry's helper.  ");
  assert.match(p, /^I am Jerry's helper\./);          // custom intro, trimmed, on top
  assert.doesNotMatch(p, /You are DAI-LITE — a direct/); // default persona replaced
  assert.match(p, /RUN: /);                            // app mechanics still present
  assert.match(p, /"→ AI" button/);
});

test("buildSystemPrompt falls back to the default persona when none given", () => {
  const p = D.buildSystemPrompt(D.DEVICE_PROFILES.flipper, "");
  assert.ok(p.startsWith(D.DEFAULT_PERSONA));
  assert.match(p, /RUN: /);
});

test("TTS_PRESETS include no-key options with /v1 base URLs", () => {
  assert.equal(D.TTS_PRESETS.edge.needsKey, false);
  assert.equal(D.TTS_PRESETS.freetts.needsKey, false);
  assert.equal(D.TTS_PRESETS.openai.needsKey, true);
  for (const k of Object.keys(D.TTS_PRESETS)) assert.match(D.TTS_PRESETS[k].ttsBaseUrl, /\/v1$/);
});

test("buildSystemPrompt always explains the RUN: protocol and confirmation", () => {
  const p = D.buildSystemPrompt(D.DEVICE_PROFILES.esp32);
  assert.match(p, /RUN: /);
  assert.match(p, /Run" button/);
  assert.match(p, /NEVER invent command output/);
});

test("buildSystemPrompt includes the Flipper cheat-sheet only for Flipper", () => {
  const flip = D.buildSystemPrompt(D.DEVICE_PROFILES.flipper);
  assert.match(flip, /Flipper Zero CLI commands/);
  assert.match(flip, /subghz/);

  const esp = D.buildSystemPrompt(D.DEVICE_PROFILES.esp32);
  assert.doesNotMatch(esp, /subghz/);
  assert.match(esp, /ESP32-S3 \(Nordic UART\)/); // names the active device
});

/* ========================================================================
   Provider presets match the spec
   ======================================================================== */
test("provider presets have the exact spec Base URLs", () => {
  assert.equal(D.PRESETS.openrouter.baseUrl, "https://openrouter.ai/api/v1");
  assert.equal(D.PRESETS.nvidia.baseUrl, "https://integrate.api.nvidia.com/v1");
  assert.equal(D.PRESETS.ollama.baseUrl, "http://LAN-IP:11434/v1");
});

/* ========================================================================
   Settings normalization
   ======================================================================== */
test("normalizeSettings shapes bad / partial input safely (defaults to auto-detect)", () => {
  const defaults = {
    baseUrl: "", apiKey: "", model: "", deviceProfile: "auto", custom: {},
    connection: "ble", wsUrl: "", autoSendOutput: false, streaming: true, persona: "",
    voice: {
      engine: "device", deviceVoice: "", style: "natural",
      ttsBaseUrl: "", ttsKey: "", ttsModel: "gpt-4o-mini-tts", ttsVoice: "nova",
      ttsInstructions: D.VOICE_PERSONAS.warmWoman.ttsInstructions,
    },
  };
  assert.deepEqual(D.normalizeSettings(null), defaults);
  assert.deepEqual(D.normalizeSettings("not json"), defaults);
  const s = D.normalizeSettings('{"baseUrl":"u","deviceProfile":"esp32"}');
  assert.equal(s.baseUrl, "u");
  assert.equal(s.deviceProfile, "esp32");
  assert.deepEqual(s.custom, {});
});

test("normalizeSettings coerces autoSendOutput to a boolean", () => {
  assert.equal(D.normalizeSettings('{"autoSendOutput":true}').autoSendOutput, true);
  assert.equal(D.normalizeSettings('{"autoSendOutput":1}').autoSendOutput, true);
  assert.equal(D.normalizeSettings("{}").autoSendOutput, false);
});
