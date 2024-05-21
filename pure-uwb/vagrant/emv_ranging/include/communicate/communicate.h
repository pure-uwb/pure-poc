#include <deca_device_api.h>
#include <deca_regs.h>
#include <deca_spi.h>
#include <port.h>
#include <shared_defines.h>

#include <zephyr.h>
#include <sys/printk.h>

#define UWB_COMMUNICATE_SUCCESS (1)
#define UWB_COMMUNICATE_FAIL (-1)

#define MAX_FRAME_LEN 127
#define MAX_PAYLOAD_LEN 116
#define MAX_MSG_LEN 256
#define LEN_LEN 2
#define FCTRL_ACK_REQ_MASK 0x20
#define RX_RESP_TO_UUS  3000//2200
/* Index to access the sequence number and frame control fields 
 *  * in frames sent and received. */
#define FRAME_FC_IDX    0
#define FRAME_SN_IDX    2
/* ACK frame control value. */
#define ACK_FC_0        0x02
#define ACK_FC_1        0x00

/* Buffer to store received frame. See NOTE 4 below. */
#define ACK_FRAME_LEN   5

/*Useful for developing*/

#define PAN_ID_TX 0xDECA
#define SHORT_ADDR_TX 0x5259

#define PAN_ID_RX 0xDECA
#define SHORT_ADDR_RX 0x5151

#define MAX_RETRY 3
// NOTE: channel is defined such that its byte representation can be used as header for the PHY frame.
struct Channel{
	uint16_t frame_control;
	uint8_t seq_number;
	uint16_t PAN;
	uint16_t dst_addr;
	uint16_t src_addr;
}__attribute__((packed));

typedef struct Channel uwb_channel_t;
typedef struct Channel header_t;

/* Default communication configuration. We use default non-STS DW mode. */
static dwt_config_t config = {
    5,               /* Channel number. */
    DWT_PLEN_128,    /* Preamble length. Used in TX only. */
    DWT_PAC8,        /* Preamble acquisition chunk size. Used in RX only. */
    9,               /* TX preamble code. Used in TX only. */
    9,               /* RX preamble code. Used in RX only. */
    1,               /* 0 to use standard 8 symbol SFD, 
                      *   1 to use non-standard 8 symbol, 
                      *   2 for non-standard 16 symbol SFD and 
                      *   3 for 4z 8 symbol SDF type */
    DWT_BR_6M8,      /* Data rate. */
    DWT_PHRMODE_STD, /* PHY header mode. */
    DWT_PHRRATE_STD, /* PHY header rate. */
    (129 + 8 - 8),   /* SFD timeout (preamble length + 1 + SFD length - PAC size). Used in RX only. */
    DWT_STS_MODE_OFF, /* STS disabled */
    DWT_STS_LEN_64,  /* STS length see allowed values in Enum dwt_sts_lengths_e */
    DWT_PDOA_M0      /* PDOA mode off */
};

/* Values for the PG_DELAY and TX_POWER registers reflect the bandwidth and power of the spectrum at the current
 * temperature. These values can be calibrated prior to taking reference measurements. See NOTE 4 below. */
extern dwt_txconfig_t txconfig_options;

/**
 *  * @brief Initialize the channel object with the configuration for transmission
 *   * 
 *    * @param channel object containing 
 *     * @param config default configuration
 *      * @param tx_config_options configuration of TX power pulses 
 *       * @return int 1 on success, 0 on failure.
 *        */
int initialize(uwb_channel_t* channel, dwt_config_t config, dwt_txconfig_t tx_config_options, uint16_t pan_id, uint16_t src_short_addr, uint16_t dst_short_addr);

/**
 *  * @brief Sends the content of buffer over the channel.  
 *   * 
 *    * @param buffer content to be sent (max 127 bytes)
 *     * @param len len of the buffer
 *      * @return int number of bytes sent, -1 in case of an error 
 *       */
int tx_data(uwb_channel_t* channel, uint8_t* tx_buffer, int tx_msg_len);

/**
 *  * @brief Receive data over the channel
 *   * 
 *    * @param buffer where data received over the channel is saved 
 *     * @return int number of bytes received, -1 in case of an error
 *      */
int rx_data(uint8_t* buffer);
int rx_data_h(header_t* header, uint8_t* buffer);


int destroy(uwb_channel_t* channel);








