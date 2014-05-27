#include "Wire.h"
#include "BlinkM_funcs.h"

byte addresses[7] = {-1, -1, -1, -1, -1, -1, -1};
const byte master_blinkm_1[3] = {255, 0, 0}; // red
const byte master_blinkm_2[3] = {0, 0, 255}; // blue

void setup()
{
	BlinkM_beginWithPower();
	for (byte i = B1; i < B1000; i++) {
		BlinkM_stopScript(i);  // turn off startup script
	}
}

int get_all_blinkms(byte (&addresses)[7]) // Pass array of addresses by value
{
	int count = 0;
	for (int i = 1; i < 8; i++) {
		if (BlinkM_receiveBytes(i, NULL, 1) == 0) { // Found a blinkm with that address.
			addresses[i] = (byte) i;
			count++;
		} else {
			addresses[i] = -1;
		}
	}
	return count;
}

void loop()
{
	int number_found = get_all_blinkms(addresses);
	for (int i=0; i < 7; i++) {
		if (addresses[i] != -1) { // If address is valid
			if (number_found > 1) {
				BlinkM_setRGB(addresses[i], master_blinkm_1[0], master_blinkm_1[1], master_blinkm_1[2]);
				number_found--;
			} else {
				BlinkM_setRGB(addresses[i], master_blinkm_2[0], master_blinkm_2[1], master_blinkm_2[2]);
			}
		}
	}
}
