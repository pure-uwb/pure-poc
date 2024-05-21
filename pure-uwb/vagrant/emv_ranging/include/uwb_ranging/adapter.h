
#include <stdlib.h>
#include <inttypes.h>

typedef struct channel * channel_t;

int channel_init(channel_t* channel);
int channel_destroy(channel_t channel);
int send_bytes(channel_t channel, uint8_t* txbuf, int len);
int receive_bytes(channel_t channel, uint8_t* rxbuf);