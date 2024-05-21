#include <communicate.h>
#include <debug/thread_analyzer.h>

#define COMMUNICATE_LOG_LEVEL 4
#include <logging/log.h>
LOG_MODULE_REGISTER(communicate, COMMUNICATE_LOG_LEVEL);
#define APP_NAME "TX v1"

int tx_frame(uwb_channel_t* channel, uint8_t* tx_buffer, int tx_msg_len);
int rx_frame(uwb_channel_t* channel, uint8_t* buffer);

/**
 * @brief Initialize the channel object with the configuration for transmission
 * 
 * @param channel object containing 
 * @param config default configuration
 * @param tx_config_options configuration of TX power pulses 
 * @return int 1 on success, 0 on failure.
 */

int initialize(uwb_channel_t* channel, dwt_config_t config, dwt_txconfig_t tx_config_options, uint16_t pan_id, uint16_t src_short_addr, uint16_t dst_short_addr){


    /* Configure SPI rate, DW3000 supports up to 38 MHz */
    port_set_dw_ic_spi_fastrate();

    /* Reset DW IC */
    reset_DWIC();
    dwt_setleds(DWT_LEDS_ENABLE | DWT_LEDS_INIT_BLINK );

    Sleep(2);
    LOG_INF("%s", "After sleep");
    /* Need to make sure DW IC is in IDLE_RC before proceeding */
    while (!dwt_checkidlerc()) { /* spin */ };

    if (dwt_initialise(DWT_DW_IDLE) == DWT_ERROR) {
        LOG_ERR("%s","INIT FAILED");
        while (1) { /* spin */ };
    }

    /* Configure DW IC. See NOTE 11 below. */
    if (dwt_configure(&config)) {
        LOG_ERR("%s", "CONFIG FAILED");
        while (1) { /* spin */ };
    }

    /* Values for the PG_DELAY and TX_POWER registers reflect the bandwidth and power of the spectrum at the current
    * temperature. These values can be calibrated prior to taking reference measurements. See NOTE 4 below. */

    /* Configure the TX spectrum parameters (power, PG delay and PG count) */
    dwt_configuretxrf(&tx_config_options);

    /* Set PAN ID and short address. See NOTE 2 below. */
    dwt_setpanid(pan_id);   
    //dwt_seteui(eui);
    dwt_setaddress16(src_short_addr);

    /* Can enable TX/RX states output on GPIOs 5 and 6 to help debug. */
    dwt_setlnapamode(DWT_LNA_ENABLE | DWT_PA_ENABLE);

    /* Configure frame filtering. Only data frames are enabled in this example. 
     * Frame filtering must be enabled for Auto ACK to work. */
    dwt_configureframefilter(DWT_FF_ENABLE_802_15_4, DWT_FF_DATA_EN | DWT_FF_ACK_EN);

    /* Activate auto-acknowledgement. Time is set to 0 so that the ACK is 
     * sent as soon as possible after reception of a frame. */
    dwt_enableautoack(0, 1);
    

    channel->frame_control = 0x8861;
    channel->seq_number = 0;
    channel->PAN = pan_id;
    channel->dst_addr = dst_short_addr;
    channel->src_addr = src_short_addr;
    return UWB_COMMUNICATE_SUCCESS;
      
}

/**
 * @brief Sends the content of buffer over the channel.  
 * 
 * @param buffer content to be sent (max 127 bytes)
 * @param len len of the buffer
 * @return int number of bytes sent, -1 in case of an error 
 */
int tx_frame(uwb_channel_t* channel, uint8_t* tx_msg, int tx_msg_len){
    // thread_analyzer_print();
    header_t header = *channel;
    LOG_HEXDUMP_DBG(&header, 9, "Channel:");

    // header_t header = *channel;    
    int tx_frame_len = tx_msg_len + FCS_LEN;
    int header_len = sizeof(header);

    if (header_len + tx_frame_len > MAX_FRAME_LEN){
        LOG_ERR("Frame of len %d exceeds maximum length %d",header_len + tx_frame_len, MAX_FRAME_LEN);
        return UWB_COMMUNICATE_FAIL;
    }

    LOG_INF("Sizeof header: %d", sizeof(header));
    LOG_INF("Sizeof msg: %d", tx_frame_len - FCS_LEN);

    dwt_setrxtimeout(RX_RESP_TO_UUS);
    dwt_writetxdata(sizeof(header), (uint8_t *)(&header), 0); 
    dwt_writetxdata(tx_frame_len-FCS_LEN, tx_msg, sizeof(header)); /* Zero offset in TX buffer. */
    dwt_writetxfctrl(tx_frame_len + sizeof(header), 0, 0); /* Zero offset in TX buffer, no ranging. */

    /* Start transmission, indicating that a response is expected so that
        * reception is enabled immediately after the frame is sent. */
    dwt_starttx(DWT_START_TX_IMMEDIATE | DWT_RESPONSE_EXPECTED);

    /* We assume that the transmission is achieved normally, now poll for 
        * reception of a frame or error/timeout. See NOTE 8 below. */
    uint32_t status_reg = 0;
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | 
                                                                SYS_STATUS_ALL_RX_TO | 
                                                                SYS_STATUS_ALL_RX_ERR)))
    { /* spin */ };
    // LOG_DBG("RX_FCG:%d\tRX_TO:%d\tRX_ERR:%d", 
    //  (dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK),
    //  (dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_ALL_RX_TO),
    //  (dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_ALL_RX_ERR));

    if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {

        /* Clear good RX frame event in the DW IC status register. */
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);

        /* A frame has been received, check frame length is correct for ACK,
            * then read and verify the ACK. */
        uint16_t frame_len = dwt_read32bitreg(RX_FINFO_ID) & RX_FINFO_RXFLEN_BIT_MASK;
        if (frame_len == ACK_FRAME_LEN) {

            uint8_t rx_buffer[ACK_FRAME_LEN];
            dwt_readrxdata(rx_buffer, frame_len, 0);

            /* Check if it is the expected ACK. */
            if ((rx_buffer[FRAME_FC_IDX] == ACK_FC_0)     && 
                (rx_buffer[FRAME_FC_IDX + 1] == ACK_FC_1) && 
                (rx_buffer[FRAME_SN_IDX] == header.seq_number)) {

                /* Increment the sent frame sequence number 
                    * (modulo 256). */
                channel->seq_number++;

                return tx_msg_len;
            }
        }
    }
    else {
        /* Clear RX error/timeout events in the DW IC status register. */
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO |
                                            SYS_STATUS_ALL_RX_ERR);
        LOG_ERR("%s", "Timeout");
        return UWB_COMMUNICATE_FAIL;
    }
    return UWB_COMMUNICATE_FAIL;
}


/**
 * @brief Receive data over the channel
 * 
 * @param buffer where data received over the channel is saved 
 * @return int number of bytes received, -1 in case of an error
 */
int rx_frame(header_t* header, uint8_t* buffer){

    /* Hold copy of status register state here for reference so that it can be examined at a debug breakpoint. */
    static uint32_t status_reg = 0;

    /* Hold copy of frame length of frame received (if good) so that it can be examined at a debug breakpoint. */
    static uint16_t frame_len = 0;
    /* Clear previous received data flag */
    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);

    LOG_DBG("%s", "PRE SPIN LOOP");

    /* Activate reception immediately. See NOTE 5 below. */
    dwt_setrxtimeout(0);
    dwt_rxenable(DWT_START_RX_IMMEDIATE);
    /* Poll until a frame is properly received or an RX error occurs. 
        * See NOTE 6 below.
        * STATUS register is 5 bytes long but we are not interested in 
        * the high byte here, so we read a more manageable 32-bits with 
        * this API call. */
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | 
                                                                SYS_STATUS_ALL_RX_ERR)))
    { /* spin */ };
    LOG_DBG("%s", "OUT OF SPIN LOOP");
    
    dwt_readrxdata( (uint8_t*) (header), sizeof(*header), 0);
    LOG_HEXDUMP_DBG(&header, 9, "Channel:");
    
    if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
        /* Clear good RX frame event in the DW IC status register. */
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);

        /* A frame has been received, read it into the local buffer. */
        frame_len = dwt_read32bitreg(RX_FINFO_ID) & EXT_FRAME_LEN;
        int payload_len =  frame_len - sizeof(*header) - FCS_LEN;

        if (frame_len <= FRAME_LEN_MAX) {
            // PACKET = HEADER(9 bytes) || PAYLOAD(MAX 116) || FCS(2)
            dwt_readrxdata( (uint8_t*) (header), sizeof(*header), 0);
            dwt_readrxdata(buffer, payload_len,  sizeof(*header));
        }
        // LOG_INF("Frame len:%d", frame_len);
        // LOG_INF("Payload len:%d", payload_len);
        LOG_INF("Received frame %d", header->seq_number);
        LOG_HEXDUMP_INF(buffer, payload_len, "PAYLOAD:");
        /* Since the auto ACK feature is enabled, an ACK should be sent 
            * if the received frame requests it, so we await the ACK TX 
            * completion before taking next action. See NOTE 8 below. */
        if (header->frame_control & FCTRL_ACK_REQ_MASK) {

            /* Poll DW IC until confirmation of transmission of the ACK frame. */
            while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & SYS_STATUS_TXFRS_BIT_MASK))
            { /* spin */ };
            /* Clear TXFRS event. */
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);

            /*Separate header from */
            return payload_len;
        }
    }
    else {
        /* Clear RX error events in the DW IC status register. */
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_ERR);
        return UWB_COMMUNICATE_FAIL;
    }
    return UWB_COMMUNICATE_FAIL;
}


int tx_data(uwb_channel_t* channel, uint8_t* tx_msg, int tx_msg_len){
    int tot_tx = 0;
    int tx = 0;
    int tx_success;

    uint8_t tx_buffer[MAX_MSG_LEN];
    
    //Write length of the message at the beginning of the buffer.
    tx_msg_len+=LEN_LEN;
    memcpy(tx_buffer, &tx_msg_len, LEN_LEN);
    memcpy(&(tx_buffer[LEN_LEN]), tx_msg, tx_msg_len-LEN_LEN);
    LOG_HEXDUMP_INF(tx_buffer, tx_msg_len, "Transmit:");
    //Reset sequence number for the message
    channel->seq_number = 0;

    while(tot_tx < tx_msg_len){
        tx_success = 0;
        for(int i = 0; i < MAX_RETRY; i++){

            int to_tx =  MIN(MAX_PAYLOAD_LEN, tx_msg_len - tot_tx);
            tx = tx_frame(channel, &tx_buffer[tot_tx], to_tx);
            LOG_INF("To send: %d, Sent: %d", to_tx, tx);
            if (tx == to_tx){
                LOG_INF("Frame %d sent...", channel->seq_number);
                tot_tx += tx;
                tx_success = 1;
                break;
            }else{
                dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO |
                                             SYS_STATUS_ALL_RX_ERR);
                LOG_WRN("Error sending frame: %d, on attempt %d", channel->seq_number, i);
            }
        }

        if(tx_success == 0){
            LOG_ERR("%s", "Failed transmission.");
            break;
        }
    }
    // k_free(tx_buffer);
    return tot_tx - LEN_LEN;
}

int rx_data(uint8_t* buffer){
    header_t header;
    return rx_data_h(&header, buffer);
}

int rx_data_h(header_t* header, uint8_t* buffer){
    int tot_rx = 0;
    int rx = 0;
    int rx_success;
    uint16_t msg_len;
    uint8_t rx_buffer[MAX_FRAME_LEN];

    //First 2 bytes of the frame encode the length of the message
    rx = rx_frame(header, rx_buffer);
    
    if (rx < LEN_LEN){
        LOG_ERR("%s", "Message too short, message struct should be MSG_LEN(LEN_LEN) || MSG(MSG_LEN)");
        return UWB_COMMUNICATE_FAIL;
    }

    if (header->seq_number != 0){
        LOG_ERR("%s", "Initial frame was lost, stop reception.");
        return UWB_COMMUNICATE_FAIL;
    }

    // Receive first fragment and determine the full message length
    memcpy(&msg_len, rx_buffer, sizeof(msg_len));
    if( msg_len > MAX_MSG_LEN){
        LOG_ERR("Message of length %d exceeds maximum length %d",msg_len, MAX_MSG_LEN);
    }
    memcpy(buffer, &rx_buffer[sizeof(msg_len)], rx - sizeof(msg_len));
    tot_rx += rx;

    LOG_INF("Receiving message of len %d", msg_len);

    while(tot_rx < msg_len){
        rx_success = 0;
        for(int i = 0; i < MAX_RETRY; i++){

            rx = rx_frame(header, rx_buffer);
            if (rx > 0){
                LOG_INF("Received frame of len %d...", rx);
                memcpy(&buffer[tot_rx - LEN_LEN], rx_buffer, rx);
                tot_rx += rx;
                rx_success = 1;
                break;
            }else{
                LOG_WRN("Error receiveing frame: %d, on attempt %d", header->seq_number, i);
            }
        }
        /*
        Sleep necessary to avoid overloading of the receiver.
        Without the sleep, the sender transmits before the receiver '
        has turned on reception and therefore the first transmission attempt fails.
        */
        Sleep(10);
        if(rx_success == 0){
            /* Clear RX error/timeout events in the DW IC status register. */
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO |
                                             SYS_STATUS_ALL_RX_ERR);
            LOG_INF("%s", "Failed");    
            LOG_ERR("%s", "Failed transmission.");
            break;
        }
    }
    return tot_rx - LEN_LEN;
}


int destroy(uwb_channel_t* channel){
    k_free(channel);
}
