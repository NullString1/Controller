//#define serialLog

#define BLYNK_TEMPLATE_ID "TMPL7Ij4jdrx"
#define BLYNK_DEVICE_NAME "Controller"
#define BLYNK_AUTH_TOKEN "j9k8lXc4rr8_vh8W5wHPzqsPMLxqBrho"

#include <ESP8266WiFi.h>
#include <BlynkSimpleEsp8266.h>

#ifdef serialLog
#define BLYNK_PRINT Serial
#define sLog(x) Serial.println(x)
#else
#define sLog(x)
#endif

char auth[] = BLYNK_AUTH_TOKEN;

// Your WiFi credentials.
// Set password to "" for open networks.
char ssid[] = "vodafoneCCC991";
char pass[] = "";

// This function is called every time the Virtual Pin 0 state changes
BLYNK_WRITE(V0)
{
  // Set incoming value from pin V0 to a variable
  sLog("V0 is now" + String(param.asInt()));
  if (param.asInt() == 1){
    sRelay(1, 1);
    sLog("Enabled relay 1");
    Blynk.logEvent("relay_1_closed");
  } else {
    sRelay(1, 0);
    sLog("Disabled relay 1");
    Blynk.logEvent("relay_1_opened");
  }
}

BLYNK_WRITE(V1)
{
  // Set incoming value from pin V0 to a variable
  sLog("V0 is now" + String(param.asInt()));
  if (param.asInt() == 1){
    sRelay(2, 1);
    sLog("Enabled relay 2");
    Blynk.logEvent("relay_2_closed");
  } else {
    sRelay(2, 0);
    sLog("Disabled relay 2");
    Blynk.logEvent("relay_2_opened");
  }
}

// This function is called every time the device is connected to the Blynk.Cloud
BLYNK_CONNECTED()
{
  Blynk.syncVirtual(V0);
  delay(10);
  Blynk.syncVirtual(V1);
}

void setup()
{
  Serial.begin(115200);
  Blynk.begin(auth, ssid, pass, "blynk.cloud", 8080);
}

void loop()
{
  Blynk.run();
}

void sRelay(byte rNum, bool state) {
  byte message[] = {0xA0, rNum, state, (0xA0+rNum+state)};
  Serial.write(message, sizeof(message));
}
