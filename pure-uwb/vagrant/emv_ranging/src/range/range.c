/*! ----------------------------------------------------------------------------
 *  @file    ss_twr_initiator_sts_no_data.c
 *  @brief   Single-sided two-way ranging (SS TWR) initiator example code
 *
 *           A "packet" refers to a IEEE 802.15.4z STS Mode 3 frame that contains no payload.
 *           A "frame" refers to a IEEE 802.15.4z STS Mode 0/1/2 frame that contains a payload.
 *
 *           This example utilises the 802.15.4z STS to accomplish secure timestamps between the initiator and responder. A 32-bit STS counter
 *           is part of the STS IV used to generate the scrambled timestamp sequence (STS) in the transmitted packet and to cross correlate in the
 *           receiver. This count normally advances by 1 for every 1024 chips (~2�s) of STS in BPRF mode, and by 1 for every 512 chips (~1�s) of STS
 *           in HPRF mode. If both devices (initiator and responder) have count values that are synced, then the communication between devices should
 *           result in secure timestamps which can be used to calculate distance. If not, then the devices need to re-sync their STS counter values.
 *
 *           In these examples (ss_twr_initiator_sts_no_data/ss_twr_responder_sts_no_data), the initiator will send an SP3 mode "poll" packet to the
 *           responder while the initiator will save the TX timestamp of the "poll" packet. The responder will await the "poll" packet from the initiator
 *           and check that the STS quality is correct. If it is correct, it will respond with a "resp" packet that is also in SP3 mode. The responder
 *           will save the RX and TX timestamps of the packets. Finally, the initiator and responder will re-configure to send/receive SP0 packets.
 *           The responder will send a "report" frame to the initiator that contains the RX timestamp of the "poll" packet and the TX timestamp of the
 *           "resp" packet.
 *
 *           STS Packet Configurations:
 *           STS packet configuration 0 (SP0)
 *           ----------------------------------
 *           | SYNC | SFD | PHR | PHY Payload |
 *           ----------------------------------
 *           STS packet configuration 1 (SP1)
 *           ----------------------------------------
 *           | SYNC | SFD | STS | PHR | PHY Payload |
 *           ----------------------------------------
 *           STS packet configuration 2 (SP2)
 *           -----------------------------------------
 *           | SYNC | SFD |  PHR | PHY Payload | STS |
 *           -----------------------------------------
 *           STS packet configuration 3 (SP3)
 *           --------------------
 *           | SYNC | SFD | STS |
 *           --------------------
 *
 * @attention
 *
 * Copyright 2019 - 2020 (c) Decawave Ltd, Dublin, Ireland.
 *
 * All rights reserved.
 *
 * @author Decawave
 */

#include <stdlib.h>
#include <deca_device_api.h>
#include <deca_regs.h>
#include <deca_spi.h>
#include <deca_types.h>
#include <port.h>
#include <shared_defines.h>
#include <shared_functions.h>
#include <config_options.h>
#include <range.h>

//zephyr includes
#include <zephyr.h>
#include <sys/printk.h>

#define LOG_LEVEL 4
#include <logging/log.h>
LOG_MODULE_REGISTER(range);

/* Example application name */
#define APP_NAME "DS TWR"

#define TX_ANT_DLY 16385
#define RX_ANT_DLY 16385

#define ALL_MSG_COMMON_LEN 10
/* Indexes to access some of the fields in the frames defined above. */
#define ALL_MSG_SN_IDX 2
#define RESP_MSG_POLL_RX_TS_IDX 10
#define RESP_MSG_RESP_TX_TS_IDX 14
#define RESP_MSG_TS_LEN 4
#define FINAL_MSG_POLL_TX_TS_IDX 10
#define FINAL_MSG_RESP_RX_TS_IDX 14
#define FINAL_MSG_FINAL_TX_TS_IDX 18
int responder_range_rx_poll(uint8_t* cp_key, uint8_t* cp_iv, struct ranging_measurement* measure, int iteration);
int responder_range_rx_final(uint8_t* cp_key, uint8_t* cp_iv, struct ranging_measurement* measure);

/* Frame sequence number, incremented after each transmission. */
static uint8_t frame_seq_nb = 0;

/* Buffer to store received response message.
 * Its size is adjusted to longest frame that this example code is supposed 
 * to handle. */
#define RX_BUF_LEN 24
static uint8_t rx_buffer[RX_BUF_LEN];


/* Hold copy of status register state here for reference so that it can be 
 * examined at a debug breakpoint. */
static uint32_t status_reg = 0;

/* Delay between frames, in UWB microseconds. See NOTE 1 below. */
#define POLL_TX_TO_RESP_RX_DLY_UUS (290 + CPU_COMP)

/* This is the delay from Frame RX timestamp to TX reply timestamp used for 
 * calculating/setting the DW IC's delayed TX function. This includes the
 * frame length of approximately 550 us with above configuration. */
#define RESP_RX_TO_FINAL_TX_DLY_UUS (480*2 + CPU_COMP) //(480 + CPU_COMP)

/*Delay between the response frame and final frame. */
#define RESP_TX_TO_FINAL_RX_DLY_UUS (100 + CPU_COMP)

/* Delay between frames, in UWB microseconds. See NOTE 1 below. */
#define POLL_RX_TO_RESP_TX_DLY_UUS (800 + CPU_COMP)

/*Delay between the response frame and final frame. */
#define RESP_TX_TO_FINAL_RX_DLY_UUS (100 + CPU_COMP)


/* Receive response timeout. See NOTE 5 below. */
#define RESP_RX_TIMEOUT_UUS 2000000

/* Frames used in the ranging process. See NOTE 3 below. */
static uint8_t tx_poll_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'W', 'A', 'V', 'E', 0xE0, 0, 0};
static uint8_t rx_resp_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'V', 'E', 'W', 'A', 0xE1, 0, 0};
static uint8_t tx_final_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'D', 'E', 'C', 'A', 0xE2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

static uint8_t rx_poll_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'W', 'A', 'V', 'E', 0xE0, 0, 0};
static uint8_t tx_resp_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'V', 'E', 'W', 'A', 0xE1, 0, 0};
static uint8_t rx_final_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'D', 'E', 'C', 'A', 0xE2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};


/* Hold the amount of errors that have occurred */
static uint32_t errors[23] = {0};

extern dwt_config_t config_options;
extern dwt_txconfig_t txconfig_options;
extern dwt_txconfig_t txconfig_options_ch9;

void send_tx_poll_msg(void)
{
    /* Write frame data to DW IC and prepare transmission. See NOTE 7 below. */
    tx_poll_msg[ALL_MSG_SN_IDX] = frame_seq_nb;

    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
    dwt_writetxdata(sizeof(tx_poll_msg), tx_poll_msg, 0); /* Zero offset in TX buffer. */
    dwt_writetxfctrl(sizeof(tx_poll_msg), 0, 1); /* Zero offset in TX buffer, ranging. */

    /* Start transmission. */
    dwt_starttx(DWT_START_TX_IMMEDIATE | DWT_RESPONSE_EXPECTED);

    /* Poll DW IC until TX frame sent event set. See NOTE 8 below. */
    while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK))
    { };

    /* Clear TXFRS event. */
    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
}

void init_initiator_range(){
        /* Display application name on UART. */
    LOG_INF("%s", "Initialize init range");
       /* Configure SPI rate, DW3000 supports up to 38 MHz */
#ifdef CONFIG_SPI_FAST_RATE
    port_set_dw_ic_spi_fastrate();
#endif /* CONFIG_SPI_FAST_RATE */
#ifdef CONFIG_SPI_SLOW_RATE
    port_set_dw_ic_spi_slowrate();
#endif /* CONFIG_SPI_SLOW_RATE */

/* Reset DW IC */
    /* Target specific drive of RSTn line into DW IC low for a period. */
    reset_DWIC(); 

    /* Time needed for DW3000 to start up (transition from INIT_RC to IDLE_RC) */
    Sleep(2); 

    /* Need to make sure DW IC is in IDLE_RC before proceeding */
    while (!dwt_checkidlerc()) { };

    if (dwt_initialise(DWT_DW_IDLE) == DWT_ERROR) {
        LOG_INF("%s", "INIT FAILED");
        return;
        while (1) { };
    }

    /* Enabling LEDs here for debug so that for each TX the D1 LED will flash 
     * on DW3000 red eval-shield boards.
     * Note, in real low power applications the LEDs should not be used. */
    dwt_setleds(DWT_LEDS_ENABLE | DWT_LEDS_INIT_BLINK) ;

    /* Configure DW IC. See NOTE 15 below. */
    /* If the dwt_configure returns DWT_ERROR either the PLL or RX calibration 
     * has failed the host should reset the device */
    if(dwt_configure(&config_options)) {
        LOG_ERR("%s", "CONFIG FAILED");
        return;
        while (1) { };
    }

    /* Configure the TX spectrum parameters (power, PG delay and PG count) */
    if (config_options.chan == 5) {
        dwt_configuretxrf(&txconfig_options);
    }
    else {
        dwt_configuretxrf(&txconfig_options_ch9);
    }

    /* Apply default antenna delay value. See NOTE 2 below. */
    dwt_setrxantennadelay(RX_ANT_DLY);
    dwt_settxantennadelay(TX_ANT_DLY);

    /* Set expected response's delay and timeout. See NOTE 14, 17 and 18 below.
     * As this example only handles one incoming frame with always the same 
     * delay and timeout, those values can be set here once for all. */
    dwt_setrxaftertxdelay(POLL_TX_TO_RESP_RX_DLY_UUS);
    dwt_setrxtimeout(RESP_RX_TIMEOUT_UUS);

    /* Set expected response's timeout. See NOTE 1 and 5 below.
     * As this example only handles one incoming frame with always the same
     * delay, this value can be set here once for all. */
    set_resp_rx_timeout(RESP_RX_TIMEOUT_UUS, &config_options);

    /* Next can enable TX/RX states output on GPIOs 5 and 6 to help 
     * diagnostics, and also TX/RX LEDs */
    dwt_setlnapamode(DWT_LNA_ENABLE | DWT_PA_ENABLE);

    LOG_INF("%s", "Initiator ready");
}
int initiator_range(uint8_t* cp_key, uint8_t* cp_iv, struct ranging_measurement* measure, int iteration){
    int16_t stsQual; /* This will contain STS quality index and status */
    int goodSts = 0; /* Used for checking STS quality in received signal */
    uint32_t poll_tx_ts, resp_rx_ts, poll_rx_ts, resp_tx_ts;
    int32_t rtd_init, rtd_resp;
    float clockOffsetRatio;

     if (iteration == 0) {
        /*
            * On first loop, configure the STS key & IV, then load them.
            */
        LOG_HEXDUMP_INF(cp_key, 16, "Key:");
        dwt_configurestskey(cp_key);
        dwt_configurestsiv(cp_iv);
        dwt_configurestsloadiv();
    }
    else {
        /*
            * On subsequent loops, we only need to reload the lower 32 bits of STS IV.
            */
        dwt_writetodevice(STS_IV0_ID, 0, 4, (uint8_t *)cp_iv);
        dwt_configurestsloadiv();
    }

    /*
        * Send the poll message to the responder.
        */
    send_tx_poll_msg();
    
    /* We assume that the transmission is achieved correctly, poll for 
        * reception of a frame or error/timeout. See NOTE 8 below. */
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & 
                                            (SYS_STATUS_RXFCG_BIT_MASK | 
                                                SYS_STATUS_ALL_RX_TO      | 
                                                SYS_STATUS_ALL_RX_ERR)))
    { };

    /* Need to check the STS has been received and is good. */
    goodSts = dwt_readstsquality(&stsQual);

    /* Increment frame sequence number after transmission of the 
        * poll message (modulo 256). */
    frame_seq_nb++;

    /*
        * Here we are checking for a good frame and good STS quality.
        */
    if ((status_reg & SYS_STATUS_RXFCG_BIT_MASK) && (goodSts >= 0)) {
        LOG_INF("%s", "Received response...");
        /* Clear good RX frame event in the DW IC status register. */
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_GOOD);

        /* A frame has been received, read it into the local buffer. */
        uint32_t frame_len = dwt_read32bitreg(RX_FINFO_ID) & RXFLEN_MASK;

        if (frame_len <= sizeof(rx_buffer)) {
            dwt_readrxdata(rx_buffer, frame_len, 0);

            /* Check that the frame is the expected response from the 
                * companion "DS TWR responder STS" example.
                * As the sequence number field of the frame is not relevant, 
            * it is cleared to simplify the validation of the frame. */
            rx_buffer[ALL_MSG_SN_IDX] = 0;

            if (memcmp(rx_buffer, rx_resp_msg, ALL_MSG_COMMON_LEN) == 0) {
                uint32_t final_tx_time;
                uint64_t poll_tx_ts, resp_rx_ts, final_tx_ts;
                int ret = DWT_ERROR;

                /* Retrieve poll transmission and response reception 
                    * timestamps. See NOTE 9 below. */
                poll_tx_ts = get_tx_timestamp_u64();
                resp_rx_ts = get_rx_timestamp_u64();

                /* Compute final message transmission time. See NOTE 19 below. */
                final_tx_time = (resp_rx_ts + (RESP_RX_TO_FINAL_TX_DLY_UUS * UUS_TO_DWT_TIME)) >> 8;
                dwt_setdelayedtrxtime(final_tx_time);

                final_tx_ts = (((uint64_t)(final_tx_time & 0xFFFFFFFEUL)) << 8) + TX_ANT_DLY;
                measure->poll_tx_ts = poll_tx_ts;
                measure->resp_rx_ts = resp_rx_ts;
                measure->final_tx_ts = final_tx_ts;

                /* Write all timestamps in the final message. See NOTE 19 below. */
                final_msg_set_ts(&tx_final_msg[FINAL_MSG_POLL_TX_TS_IDX], poll_tx_ts);
                final_msg_set_ts(&tx_final_msg[FINAL_MSG_RESP_RX_TS_IDX], resp_rx_ts);
                final_msg_set_ts(&tx_final_msg[FINAL_MSG_FINAL_TX_TS_IDX], final_tx_ts);

                /* Write and send final message. See NOTE 7 below. */
                tx_final_msg[ALL_MSG_SN_IDX] = frame_seq_nb;
                dwt_writetxdata(sizeof(tx_final_msg), tx_final_msg, 0); /* Zero offset in TX buffer. */
                dwt_writetxfctrl(sizeof(tx_final_msg), 0, 1); /* Zero offset in TX buffer, ranging bit set. */

                ret = dwt_starttx(DWT_START_TX_DELAYED);
                /* If dwt_starttx() returns an error, abandon this ranging
                    * exchange and proceed to the next one. 
                    * See NOTE 13 below. */
                if (ret == DWT_SUCCESS) {
                    LOG_INF("%s", "Transmission success");
                    /* Poll DW IC until TX frame sent event set. See NOTE 8 below. */
                    while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK))
                    { };

                    /* Clear TXFRS event. */
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);

                    /* Increment frame sequence number after transmission of 
                    * the final message (modulo 256). */
                    frame_seq_nb++;
                    return 1;
                }else{
                    LOG_ERR("%s", "Failed transmission");
                }
            }
            else {
                errors[BAD_FRAME_ERR_IDX] += 1;
            }
        }
        else {
            errors[RTO_ERR_IDX] += 1;
        }
    }
    else {
        LOG_ERR("%s", "Failed transmission, timeout");
        check_for_status_errors(status_reg, errors);

        if (!(status_reg & SYS_STATUS_RXFCG_BIT_MASK)) {
            errors[BAD_FRAME_ERR_IDX] += 1;
        }
        if (goodSts < 0) {
            errors[PREAMBLE_COUNT_ERR_IDX] += 1;
        }
        if (stsQual <= 0) {
            errors[CP_QUAL_ERR_IDX] += 1;
        }
    }

    /* Clear RX error/timeout events in the DW IC status register. */
    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_GOOD | 
                                        SYS_STATUS_ALL_RX_TO   |
                                        SYS_STATUS_ALL_RX_ERR);
    return 0;

}


int responder_range_rx_poll(uint8_t* cp_key, uint8_t* cp_iv, struct ranging_measurement* measure, int iteration){
    uint8_t frame_seq_nb = 0;
    uint64_t poll_rx_ts, resp_tx_ts;
    int16_t stsQual; /* Thss will contain STS quality index and status */
    int goodSts = 0; /* Used for checking STS quality in received signal */

    if (iteration == 0) {
        /*
            * On first loop, configure the STS key & IV, then load them.
            */
        LOG_HEXDUMP_INF(cp_key, 16, "Key inner:");
        dwt_configurestskey(cp_key);
        dwt_configurestsiv(cp_iv);
        dwt_configurestsloadiv();
    }
    else {
        /*
            * On subsequent loops, we only need to reload the lower 32 bits of STS IV.
            */
        dwt_writetodevice(STS_IV0_ID, 0, 4, (uint8_t *)cp_iv);
        dwt_configurestsloadiv();
    }

    /* Responder will enable the receive when waiting for Poll message,
        * the receiver will be automatically enabled (DWT_RESPONSE_EXPECTED)
        * when waiting for Final message */
    dwt_rxenable(DWT_START_RX_IMMEDIATE);

    /* Poll for reception of a frame or error/timeout. See NOTE 6 below. */
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR)))
    { /* spin */ };
    LOG_INF("%s", "Out of spin loop...");
    /*
    * Need to check the STS has been received and is good.
    */
    goodSts = dwt_readstsquality(&stsQual);

    /*
    * Check for a good frame and STS count.
    */
    if ((status_reg & SYS_STATUS_RXFCG_BIT_MASK) && (goodSts >= 0)) {
        LOG_INF("%s", "Received good package...");
        /* Clear good RX frame event in the DW IC status register. */
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);

        /* A frame has been received, read it into the local buffer. */
        uint32_t frame_len = dwt_read32bitreg(RX_FINFO_ID) & RXFLEN_MASK;
            dwt_readrxdata(rx_buffer, frame_len, 0);

        /* Check that the frame is a poll sent by "SS TWR initiator STS" example.
            * As the sequence number field of the frame is not relevant,
            * it is cleared to simplify the validation of the frame. */
        rx_buffer[ALL_MSG_SN_IDX] = 0;
        if (memcmp(rx_buffer, rx_poll_msg, ALL_MSG_COMMON_LEN) == 0) {
            LOG_INF("%s", "Received poll message");	
            uint32_t resp_tx_time;

            /* Retrieve poll reception timestamp. */
            poll_rx_ts = get_rx_timestamp_u64();
            measure->poll_rx_ts = poll_rx_ts;
            resp_tx_time = (poll_rx_ts                               /* Received timestamp value */
                    + ((POLL_RX_TO_RESP_TX_DLY_UUS                   /* Set delay time */
                            + get_rx_delay_time_data_rate()          /* Added delay time for data rate set */
                            + get_rx_delay_time_txpreamble()         /* Added delay for TX preamble length */
                            + ((1<<(config_options.stsLength+2))*8)) /* Added delay for STS length */
                            * UUS_TO_DWT_TIME)) >> 8;                /* Converted to time units for chip */

            dwt_setdelayedtrxtime(resp_tx_time);

            /* Response TX timestamp is the transmission time we
                * programmed plus the antenna delay. */
            resp_tx_ts = (((uint64_t)(resp_tx_time & 0xFFFFFFFEUL)) << 8) + TX_ANT_DLY;
            measure->resp_tx_ts = resp_tx_ts;
            /* Write and send the response message. See NOTE 9 below. */
            tx_resp_msg[ALL_MSG_SN_IDX] = frame_seq_nb;
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
            dwt_writetxdata(sizeof(tx_resp_msg), tx_resp_msg, 0); /* Zero offset in TX buffer. */
            dwt_writetxfctrl(sizeof(tx_resp_msg), 0, 1); /* Zero offset in TX buffer, ranging. */

            /*
                * As described above, we will be delaying the transmission of the RESP message
                * with a set value that is also with reference to the timestamp of the received
                * POLL message.
                */
                /* Receiver can be delayed as Final message (will not come immediately. */
            dwt_setrxaftertxdelay(100);

            int ret = dwt_starttx(DWT_START_TX_DELAYED | DWT_RESPONSE_EXPECTED);

            /* If dwt_starttx() returns an error, abandon this ranging
                * exchange and proceed to the next one. See NOTE 10 below. */
            if (ret == DWT_SUCCESS) {
                LOG_INF("%s", "Response sent...");
                /* Poll DW IC until TX frame sent event set. See NOTE 6 below. */
                while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK))
                { /* spin */ };

                /* Clear TXFRS event. */
                dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);

                /* Increment frame sequence number after transmission
                    * of the poll message (modulo 256). */
                frame_seq_nb++;
                return 1;
                /*
                    * This flag is set high here so that we do not reset
                    * the STS count before receiving the final message
                    * from the initiator. Otherwise, the STS count would
                    * be bad and we would be unable to receive it.
                    */
            }
        }else{
            LOG_ERR("%s", "Wrong message");
            return 0;
        }
    }
    return 0;
}

int responder_range_rx_final(uint8_t* cp_key, uint8_t* cp_iv, struct ranging_measurement* measure){
    int16_t stsQual; /* This will contain STS quality index and status */
    int goodSts = 0; /* Used for checking STS quality in received signal */
    

    /* Poll for reception of a frame or error/timeout. See NOTE 6 below. */
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR)))
    { /* spin */ };
    LOG_INF("%s", "Out of spin loop...");
    /*
        * Need to check the STS has been received and is good.
        */
    goodSts = dwt_readstsquality(&stsQual);

    /*
        * Check for a good frame and STS count.
        */
    if ((status_reg & SYS_STATUS_RXFCG_BIT_MASK) && (goodSts >= 0)) {
        LOG_INF("%s", "Received good package...");
        /* Clear good RX frame event in the DW IC status register. */
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);

        /* A frame has been received, read it into the local buffer. */
        uint32_t frame_len = dwt_read32bitreg(RX_FINFO_ID) & RXFLEN_MASK;
        if (frame_len <= sizeof(rx_buffer)) {
            
            dwt_readrxdata(rx_buffer, frame_len, 0);
            rx_buffer[ALL_MSG_SN_IDX] = 0;

            if (memcmp(rx_buffer, rx_final_msg, ALL_MSG_COMMON_LEN) == 0) {
                LOG_INF("%s", "Received final message");
                uint64_t final_rx_ts, resp_tx_ts;
                uint32_t poll_tx_ts, resp_rx_ts, final_tx_ts;
                uint32_t poll_rx_ts_32, resp_tx_ts_32, final_rx_ts_32;
                double Ra, Rb, Da, Db, tof, distance;
                int64_t tof_dtu;

                /* Retrieve response transmission and final reception timestamps. */
                resp_tx_ts = get_tx_timestamp_u64();
                final_rx_ts = get_rx_timestamp_u64();

                /* Get timestamps embedded in the final message. */
                final_msg_get_ts(&rx_buffer[FINAL_MSG_POLL_TX_TS_IDX], &poll_tx_ts);
                final_msg_get_ts(&rx_buffer[FINAL_MSG_RESP_RX_TS_IDX], &resp_rx_ts);
                final_msg_get_ts(&rx_buffer[FINAL_MSG_FINAL_TX_TS_IDX], &final_tx_ts);

                /* Compute time of flight. 32-bit subtractions give correct
                    * answers even if clock has wrapped. See NOTE 15 below. */
                poll_rx_ts_32 = (uint32_t)measure->poll_rx_ts;
                resp_tx_ts_32 = (uint32_t)measure->resp_tx_ts;
                final_rx_ts_32 = (uint32_t)final_rx_ts;
                Ra = (double)(resp_rx_ts - poll_tx_ts);
                Rb = (double)(final_rx_ts_32 - resp_tx_ts_32);
                Da = (double)(final_tx_ts - resp_rx_ts);
                Db = (double)(resp_tx_ts_32 - poll_rx_ts_32);
                tof_dtu = (int64_t)((Ra * Rb - Da * Db) / (Ra + Rb + Da + Db));

                tof = tof_dtu * DWT_TIME_UNITS;
                distance = tof * SPEED_OF_LIGHT;

                /* Display computed distance. */
                char dist[20] = {0};
                sprintf(dist, "dist %3.2f m", distance);
                LOG_INF("%s", log_strdup(dist));

                measure->poll_tx_ts = poll_tx_ts;
                measure->resp_rx_ts = resp_rx_ts;
                measure->final_tx_ts = final_tx_ts;
                //TODO include final
                sprintf(measure->dist, "%2.2f", distance);
                return 1;
            }else{
                LOG_ERR("%s", "Wrong message");
                return 0;
            }
        }
    }
    return 0;
}


int responder_range(uint8_t* cp_key, uint8_t* cp_iv, struct ranging_measurement* measure, int iteration){
    if(responder_range_rx_poll(cp_key, cp_iv, measure, iteration)){
        return responder_range_rx_final(cp_key, cp_iv, measure);
    }
   return 0;
}

void init_responder_range(){
    /* Configure SPI rate, DW3000 supports up to 38 MHz */
    #ifdef CONFIG_SPI_FAST_RATE
        port_set_dw_ic_spi_fastrate();
    #endif /* CONFIG_SPI_FAST_RATE */
    #ifdef CONFIG_SPI_SLOW_RATE
        port_set_dw_ic_spi_slowrate();
    #endif /* CONFIG_SPI_SLOW_RATE */
      /* Reset DW IC */
    reset_DWIC();

    Sleep(2);

    /* Need to make sure DW IC is in IDLE_RC before proceeding */
    while (!dwt_checkidlerc()) { /* spin */ };

    if (dwt_initialise(DWT_DW_IDLE) == DWT_ERROR) {
        LOG_ERR("%s", "INIT FAILED");
        return;
        // while (1) { /* spin */ };
    }

    /* Enabling LEDs here for debug so that for each TX the D1 LED
     * will flash on DW3000 red eval-shield boards.
     * Note, in real low power applications the LEDs should not be used.
     */
    dwt_setleds(DWT_LEDS_ENABLE | DWT_LEDS_INIT_BLINK) ;

    /* Configure DW IC. See NOTE 14 below. */
    if (dwt_configure(&config_options)) {
        LOG_ERR("%s", "CONFIG FAILED");
        return;
        // while (1) { /* spin */ };
    }

    /* Configure the TX spectrum parameters (power, PG delay and PG count) */
    if (config_options.chan == 5) {
        dwt_configuretxrf(&txconfig_options);
    }
    else {
        dwt_configuretxrf(&txconfig_options_ch9);
    }

    /* Apply default antenna delay value. See NOTE 2 below. */
    dwt_setrxantennadelay(RX_ANT_DLY);
    dwt_settxantennadelay(TX_ANT_DLY);

    /* Next can enable TX/RX states output on GPIOs 5 and 6 to help
     * diagnostics, and also TX/RX LEDs */
    dwt_setlnapamode(DWT_LNA_ENABLE | DWT_PA_ENABLE);

    /* Delay between the response frame and final frame */
    dwt_setrxaftertxdelay(RESP_TX_TO_FINAL_RX_DLY_UUS);
    dwt_setrxtimeout(RESP_RX_TIMEOUT_UUS);

}
