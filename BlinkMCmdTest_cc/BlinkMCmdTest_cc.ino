#include <Wire.h>

void setup() {
  Wire.begin(); //set up I2C
}

void loop() {
  Wire.beginTransmission(0x09);  //join I2C, talk to BlinkM 0x09
  Wire.write('c');  //'c' == fade to color
  Wire.write(0xff);  //value for red channel
  Wire.write(0xff);  //value for blue channel
  Wire.write(0xff);  //value for green channel
  Wire.endTransmission(); //leave I2C bus
}
