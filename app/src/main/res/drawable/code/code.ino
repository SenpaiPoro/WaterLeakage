#include <Wire.h>
#include <LiquidCrystal_I2C.h>

LiquidCrystal_I2C lcd(0x27, 16, 2);

const int TRIG_PIN = 8;
const int ECHO_PIN = 9;
const unsigned long PULSE_TIMEOUT = 30000UL;
const int SAMPLES = 5;

const int FLOW_PIN1 = 2;
const int FLOW_PIN2 = 3;

volatile unsigned long pulseCount1 = 0;
volatile unsigned long pulseCount2 = 0;

const float PULSES_PER_LITER1 = 450.0;
const float PULSES_PER_LITER2 = 450.0;
const unsigned long FLOW_INTERVAL_MS = 1000;
const int tankDepth = 30;

unsigned long lastFlowTime = 0;
float flowLpm1 = 0;
float flowLpm2 = 0;

void flowPulseISR1() { pulseCount1++; }
void flowPulseISR2() { pulseCount2++; }

float singleDistanceCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  unsigned long duration = pulseIn(ECHO_PIN, HIGH, PULSE_TIMEOUT);
  if (duration == 0) return -1;
  return duration * 0.034 / 2.0;
}

float medianDistanceCm() {
  float readings[SAMPLES];
  for (int i = 0; i < SAMPLES; i++) {
    readings[i] = singleDistanceCm();
    delay(25);
  }

  for (int i = 0; i < SAMPLES - 1; i++) {
    for (int j = i + 1; j < SAMPLES; j++) {
      if (readings[j] < readings[i]) {
        float t = readings[i];
        readings[i] = readings[j];
        readings[j] = t;
      }
    }
  }
  return readings[SAMPLES / 2];
}

void setup() {
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);

  pinMode(FLOW_PIN1, INPUT_PULLUP);
  pinMode(FLOW_PIN2, INPUT_PULLUP);

  attachInterrupt(digitalPinToInterrupt(FLOW_PIN1), flowPulseISR1, RISING);
  attachInterrupt(digitalPinToInterrupt(FLOW_PIN2), flowPulseISR2, RISING);

  Serial.begin(115200);

  lcd.init();
  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Water Monitor");
  delay(1500);
  lcd.clear();

  lastFlowTime = millis();
}

void loop() {
  unsigned long now = millis();

  if (now - lastFlowTime >= FLOW_INTERVAL_MS) {
    noInterrupts();
    unsigned long p1 = pulseCount1;
    unsigned long p2 = pulseCount2;
    pulseCount1 = 0;
    pulseCount2 = 0;
    interrupts();

    flowLpm1 = (p1 / PULSES_PER_LITER1) * 60.0;
    flowLpm2 = (p2 / PULSES_PER_LITER2) * 60.0;

    lastFlowTime = now;
  }

  float distance = medianDistanceCm();
  if (distance < 0 || distance > tankDepth) distance = tankDepth;

  int waterLevel = tankDepth - (int)round(distance);
  if (waterLevel < 0) waterLevel = 0;

  int percentage = (waterLevel * 100) / tankDepth;

  lcd.setCursor(0, 0);
  lcd.print("F1:");
  lcd.print(flowLpm1, 1);
  lcd.print(" F2:");
  lcd.print(flowLpm2, 1);
  lcd.print(" ");

  lcd.setCursor(0, 1);
  lcd.print("Lvl:");
  lcd.print(waterLevel);
  lcd.print("cm ");
  lcd.print(percentage);
  lcd.print("%  ");

  Serial.print(distance, 2);
  Serial.print(",");
  Serial.print(percentage);
  Serial.print(",");
  Serial.print(flowLpm1, 2);
  Serial.print(",");
  Serial.println(flowLpm2, 2);

  delay(1000);
}
