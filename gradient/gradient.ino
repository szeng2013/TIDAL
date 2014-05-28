#include "Wire.h"
#include "BlinkM_funcs.h"

byte addresses[7] = {240, 240, 240, 240, 240, 240, 240};
const byte master_blinkm_1[3] = {255, 0, 0}; // red
const byte master_blinkm_2[3] = {255, 0, 255}; // purple
const byte master_blinkm_3[3] = {0, 0, 255}; // blue

void setup()
{
	BlinkM_beginWithPower();
        BlinkM_stopScript(0);
}

int get_all_blinkms(byte* addresses) // Pass array of addresses by value
{
	int count = 0;
	for (int i = 0; i < 7; i++) {
		if (BlinkM_getAddress(i+1) != -1) { // Found a blinkm with that address.
			addresses[i] = (byte) (i+1);
			count++;
		} else {
			addresses[i] = 240;
		}
	}
	return count;
}

void loop()
{
        BlinkM_stopScript(0);
	int number_found = get_all_blinkms(addresses);
        int count = number_found;
	for (int i=0; i < 7; i++) {
		if (addresses[i] != 240) { // If address is valid
                  if (count == 3) {
			if (number_found > 2) {
                                BlinkM_setRGB(addresses[i], master_blinkm_3[0], master_blinkm_3[1], master_blinkm_3[2]);
                                number_found--;
                        }
                        else if (number_found > 1) {
				BlinkM_setRGB(addresses[i], master_blinkm_2[0], master_blinkm_2[1], master_blinkm_2[2]);
				number_found--;
			} else {
				BlinkM_setRGB(addresses[i], master_blinkm_1[0], master_blinkm_1[1], master_blinkm_1[2]);
			}
                  }
                  if (count == 2) {
                        if (number_found > 1) {
                                BlinkM_setRGB(addresses[i], master_blinkm_3[0], master_blinkm_3[1], master_blinkm_3[2]);
                                number_found--;
                        }
                        else {
                                BlinkM_setRGB(addresses[i], master_blinkm_1[0], master_blinkm_1[1], master_blinkm_1[2]);
                        }
                  }
                  if (count == 1) {
                        BlinkM_setRGB(addresses[i], master_blinkm_1[0], master_blinkm_1[1], master_blinkm_1[2]);
                  }
		}
	}
}
