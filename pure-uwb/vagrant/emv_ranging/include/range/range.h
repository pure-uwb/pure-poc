struct __attribute__((__packed__)) ranging_measurement{
    uint32_t poll_tx_ts, resp_rx_ts, poll_rx_ts, resp_tx_ts, final_tx_ts;
    char dist[5];
};

void send_tx_poll_msg(void);
void init_initiator_range();
int initiator_range(uint8_t* cp_key, uint8_t* cp_iv, struct ranging_measurement* measure, int iteration);
int responder_range(uint8_t* cp_key, uint8_t* cp_iv, struct ranging_measurement* measure, int iteration);
void init_responder_range();