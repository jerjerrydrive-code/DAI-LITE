/*
  DAI-LITE ESP32-S3 Bridge  ·  MIT License  ·  (c) 2026 DAI-LITE contributors

  This is the "module" half of DAI-LITE — think of it as the reusable AIPI-Lite
  style bridge that a Flipper Zero (or any serial gadget) plugs into.

  What it does
  ------------
  The DAI-LITE web app (the AI + voice brain) connects to THIS board two ways:
    • Bluetooth LE  — Nordic UART Service (matches DAI-LITE's built-in ESP32
                      profile, so "Auto-detect" finds it).
    • WiFi          — a WebSocket server, so you can leave the board plugged in
                      to power/PC and reach it over the LAN.

  Whatever text arrives from the app is:
    1. handled locally if it's one of this board's own commands (so you get
       replies even with nothing else wired — great for testing), and
    2. forwarded out a hardware UART (Serial1) to a connected device such as a
       Flipper Zero's GPIO serial.

  Anything the wired device prints back on Serial1 is streamed to the app over
  whichever transport is connected (BLE notifications and/or WebSocket).

  Why bridge the Flipper over UART instead of its own Bluetooth?
  -------------------------------------------------------------
  Stock Flipper firmware speaks a binary RPC protocol over BLE, NOT a raw text
  CLI — so plain text over Bluetooth gets no response. Over its GPIO serial the
  Flipper CLI is reachable as plain text, which is exactly what DAI-LITE speaks.
  (Enabling CLI-over-UART depends on your Flipper firmware; see firmware/README.md.
  Even if you don't wire a Flipper at all, this board answers its own commands.)

  Libraries
  ---------
    • BLE: the ESP32 Arduino core's built-in "BLEDevice" (no extra install).
    • WebSocket: "WebSockets" by Markus Sattler (Links2004) — install via the
      Arduino Library Manager. Set WIFI_ENABLED to 0 to skip WiFi entirely.

  Board: any ESP32-S3 dev board. Select it in Tools → Board.
*/

// ======================= USER CONFIG (edit these) =======================
#define WIFI_ENABLED   1                 // set to 0 to build BLE-only
const char* WIFI_SSID  = "YOUR_WIFI";    // your 2.4GHz network name
const char* WIFI_PASS  = "YOUR_PASS";    // your WiFi password
const int   WS_PORT    = 81;             // DAI-LITE connects to ws://<board-ip>:81

const char* BLE_NAME   = "ESP32-DAI";    // advertised name (DAI-LITE filters "ESP32")

// Hardware UART to the wired device (e.g. Flipper GPIO 13=TX, 14=RX).
// Pick pins that are free on your board; these are safe defaults for S3.
const int   UART_TX_PIN = 17;            // ESP32 TX  -> device RX
const int   UART_RX_PIN = 18;            // ESP32 RX  <- device TX
const long  UART_BAUD   = 115200;        // match your device's serial speed

const int   STATUS_LED  = LED_BUILTIN;   // lights when a client is connected
// ========================================================================

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#if WIFI_ENABLED
  #include <WiFi.h>
  #include <WebSocketsServer.h>
  WebSocketsServer webSocket = WebSocketsServer(WS_PORT);
#endif

// Nordic UART Service UUIDs — these MUST match DAI-LITE's ESP32 profile.
#define NUS_SERVICE "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define NUS_RX      "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  // app WRITES here (commands in)
#define NUS_TX      "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  // we NOTIFY here (data out)

BLECharacteristic* txChar = nullptr;   // notify -> app
bool bleConnected = false;

HardwareSerial DeviceSerial(1);        // Serial1 to the wired device

String cmdLine = "";                   // accumulates an incoming command until CR/LF

// ---- Send text back to whoever is connected (BLE + WebSocket) ----
void sendToApp(const String& text) {
  if (text.length() == 0) return;

  // BLE notify in <=20 byte chunks (safe for the default MTU).
  if (bleConnected && txChar) {
    const char* buf = text.c_str();
    size_t len = text.length();
    for (size_t i = 0; i < len; i += 20) {
      size_t n = min((size_t)20, len - i);
      txChar->setValue((uint8_t*)(buf + i), n);
      txChar->notify();
      delay(4);                        // small gap so the phone keeps up
    }
  }

#if WIFI_ENABLED
  webSocket.broadcastTXT(text);        // all WebSocket clients
#endif
}

// ---- This board's OWN commands. Returns true if handled locally. ----
bool handleLocalCommand(const String& raw) {
  String c = raw; c.trim();
  String lc = c; lc.toLowerCase();

  if (lc == "help") {
    sendToApp(
      "DAI bridge commands:\r\n"
      "  id           board id + IP\r\n"
      "  ping         replies pong\r\n"
      "  led on|off   built-in LED\r\n"
      "  uart <text>  send raw text to the wired device\r\n"
      "Anything else is forwarded to the wired device's serial port.\r\n");
    return true;
  }
  if (lc == "ping") { sendToApp("pong\r\n"); return true; }
  if (lc == "id") {
    String msg = String("DAI-LITE ESP32-S3 bridge · BLE '") + BLE_NAME + "'";
#if WIFI_ENABLED
    msg += String(" · ws://") + WiFi.localIP().toString() + ":" + WS_PORT;
#endif
    sendToApp(msg + "\r\n");
    return true;
  }
  if (lc == "led on")  { digitalWrite(STATUS_LED, HIGH); sendToApp("led on\r\n");  return true; }
  if (lc == "led off") { digitalWrite(STATUS_LED, LOW);  sendToApp("led off\r\n"); return true; }
  if (lc.startsWith("uart ")) { DeviceSerial.print(c.substring(5)); DeviceSerial.print("\r"); return true; }

  return false;   // not a local command — let it be forwarded
}

// ---- Process one complete command line from the app ----
void processCommand(const String& line) {
  if (line.length() == 0) return;
  if (handleLocalCommand(line)) return;
  // Forward to the wired device (Flipper CLI, etc.) with a CR terminator.
  DeviceSerial.print(line);
  DeviceSerial.print("\r");
}

// Feed raw incoming bytes; split into lines on CR or LF.
void feedIncoming(const String& chunk) {
  for (size_t i = 0; i < chunk.length(); i++) {
    char ch = chunk[i];
    if (ch == '\r' || ch == '\n') {
      if (cmdLine.length()) { processCommand(cmdLine); cmdLine = ""; }
    } else {
      cmdLine += ch;
      if (cmdLine.length() > 512) cmdLine = "";   // guard against runaway input
    }
  }
}

// ---- BLE callbacks ----
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*) override { bleConnected = true;  digitalWrite(STATUS_LED, HIGH); }
  void onDisconnect(BLEServer* s) override {
    bleConnected = false;
#if WIFI_ENABLED
    if (webSocket.connectedClients() == 0) digitalWrite(STATUS_LED, LOW);
#else
    digitalWrite(STATUS_LED, LOW);
#endif
    s->getAdvertising()->start();     // allow reconnects
  }
};
class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    String v = String(c->getValue().c_str());
    feedIncoming(v);
  }
};

void setupBLE() {
  BLEDevice::init(BLE_NAME);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* svc = server->createService(NUS_SERVICE);
  txChar = svc->createCharacteristic(NUS_TX, BLECharacteristic::PROPERTY_NOTIFY);
  txChar->addDescriptor(new BLE2902());

  BLECharacteristic* rxChar = svc->createCharacteristic(
      NUS_RX, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rxChar->setCallbacks(new RxCallbacks());

  svc->start();
  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(NUS_SERVICE);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();
}

#if WIFI_ENABLED
void onWsEvent(uint8_t num, WStype_t type, uint8_t* payload, size_t length) {
  if (type == WStype_TEXT) {
    feedIncoming(String((char*)payload).substring(0, length));
  } else if (type == WStype_CONNECTED) {
    digitalWrite(STATUS_LED, HIGH);
    // Greet only the client that just connected — not everyone.
    webSocket.sendTXT(num, String("Connected to DAI bridge at ") + WiFi.localIP().toString() + "\r\n");
  } else if (type == WStype_DISCONNECTED) {
    // Turn the indicator off only when nothing is connected anymore.
    if (!bleConnected && webSocket.connectedClients() == 0) digitalWrite(STATUS_LED, LOW);
  }
}
void setupWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("WiFi connecting");
  for (int i = 0; i < 40 && WiFi.status() != WL_CONNECTED; i++) { delay(250); Serial.print("."); }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("WiFi OK. DAI-LITE → ws://");
    Serial.print(WiFi.localIP()); Serial.print(":"); Serial.println(WS_PORT);
  } else {
    Serial.println("WiFi failed — BLE still works. Check SSID/PASS (2.4GHz only).");
  }
  webSocket.begin();
  webSocket.onEvent(onWsEvent);
}
#endif

void setup() {
  pinMode(STATUS_LED, OUTPUT);
  digitalWrite(STATUS_LED, LOW);
  Serial.begin(115200);                                  // USB serial (debug)
  DeviceSerial.begin(UART_BAUD, SERIAL_8N1, UART_RX_PIN, UART_TX_PIN);
  Serial.println("\nDAI-LITE ESP32-S3 bridge starting…");
  setupBLE();
#if WIFI_ENABLED
  setupWiFi();
#endif
  Serial.println("Ready. Connect from DAI-LITE (BLE 'ESP32' or WiFi ws://<ip>:81).");
}

void loop() {
#if WIFI_ENABLED
  webSocket.loop();
#endif
  // Stream anything the wired device says back up to the app.
  while (DeviceSerial.available()) {
    String chunk = "";
    while (DeviceSerial.available() && chunk.length() < 180) chunk += (char)DeviceSerial.read();
    sendToApp(chunk);
  }
}
