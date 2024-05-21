#include <zephyr.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <uwb_ranging.h>
#include <logging/log.h>
#include <range.h>
#include <adapter.h>
#include <deca_device_api.h>

LOG_MODULE_REGISTER(uwb_ranging, LOG_LEVEL_DBG);

typedef struct session{
	state_t state;	
    // dwt_sts_cp_key_t key;
    // dwt_sts_cp_iv_t iv;
	char key[KEY_SIZE];
    char iv[IV_SIZE];
	int len;
    struct ranging_measurement measure;
} session_t;

struct payment_device{
	role_t role;
	session_t session;
	channel_t board_channel;
	function_ptr_t *vtable;	
};



#define STATE(x)  x->session.state
#define BUF(x) x->session.buf
#define BUF_LEN(x) x->session.len
#define VTABLE(role) (role == CARD) ? vtable_card: vtable_terminal
#define LOCAL_PUBLIC(x) x->session.keys->local_public
#define KEY(x) x->session.key
#define IV(x) x->session.iv
#define UWB_CHANNEL(x) x->uwb_channel
#define MEASURE(x) x->session.measure
// #define UWB_TIMING

#include <zephyr/timing/timing.h>


#ifdef UWB_TIMING

timing_t start_time, end_time, timings[STATE_NUMBER +1];
uint64_t total_cycles; 
uint64_t total_ns[STATE_NUMBER +1];
int state_execution[STATE_NUMBER + 1];

void timing_report(){
    uint64_t sum = 0;
    char buf[20];
    LOG_INF("%s", "-----------------------       Timing report         --------------------");
    total_cycles = timing_cycles_get(&start_time, &end_time);
    total_ns[0] = timing_cycles_to_ns(total_cycles);     
    LOG_INF("Total time: %llu", total_ns[0]);

    for(int i = 1; i < STATE_NUMBER - 1; i++){
        total_cycles = timing_cycles_get(&timings[i-1], &timings[i]);
        total_ns[i] = timing_cycles_to_ns(total_cycles);
        sum += total_ns[i];
        // sprintf(buf, "%llu", total_ns);
        LOG_INF("State\t%s\tto %s\t%llu ns", states[state_execution[i-1]], states[state_execution[i]], total_ns[i]);
    }
    LOG_INF("Summed %llu", sum);
    LOG_INF("%s", "------------------------------------------------------------------------");
}

#endif


int uwb_ranging(payment_device_t payment_device){
    #ifdef UWB_TIMING
        int i = 0;
        timing_init();
        timing_start();

        start_time = timing_counter_get();
        timings[i] = start_time;
        state_execution[i] = STATE(payment_device);
    #endif
    while   ((STATE(payment_device) != UWB_EXIT)){
            // LOG_INF("State: %d", STATE(payment_device));
            payment_device->session.state = payment_device->vtable[STATE(payment_device)](payment_device);
            #ifdef UWB_TIMING
            i++;
            timings[i] = timing_counter_get();
            state_execution[i] = STATE(payment_device);
            #endif
    }

    #ifdef UWB_TIMING
        end_time = timing_counter_get();
        timing_stop();
        timing_report();
    #endif
    return STATE(payment_device);
}

state_t init_card(payment_device_t payment_device){
    channel_init(&(payment_device->board_channel));
    LOG_INF("State %d: Card initialized.", STATE(payment_device));
    return UWB_DERIVE_KEY;
} 

state_t init_terminal(payment_device_t payment_device){
    channel_init(&(payment_device->board_channel));
    LOG_INF("State %d: Terminal initialized.", STATE(payment_device));
    return UWB_DERIVE_KEY;
} 


state_t uwb_derive_key(payment_device_t payment_device){
    LOG_INF("%s", "derive_key");
    int rx_len = receive_bytes(payment_device->board_channel, KEY(payment_device));
    // return UWB_EXIT;
    return UWB_RANGING;
}


state_t card_range(payment_device_t payment_device){
    __ASSERT(STATE(payment_device == UWB_RANGING), "range called in state: %d", STATE(payment_device));
    init_initiator_range();
    LOG_HEXDUMP_INF(KEY(payment_device), 16, "Key:");
    //k_sleep(K_MSEC(60));
    for (int i = 0; i < 10; i++){
        if (initiator_range(KEY(payment_device), IV(payment_device), &(MEASURE(payment_device)), i) == 1){
            LOG_INF("State %d: Ranging terminated.", STATE(payment_device));
            return UWB_SUCCESS;
        }
        k_sleep(K_CYC(10));        
    }
    return UWB_FAIL;
}

state_t terminal_range(payment_device_t payment_device){
    __ASSERT(STATE(payment_device == UWB_RANGING), "range called in state: %d", STATE(payment_device));
    init_responder_range();

    int status = -1;
    LOG_HEXDUMP_INF(KEY(payment_device), 16, "Key:");
    for (int i = 0; i < 10; i++){
        if ((status = responder_range(KEY(payment_device), IV(payment_device), &(MEASURE(payment_device)), i)) == 1){
            LOG_INF("Iteration %d: Ranging terminated successfully.", i);
            return UWB_SUCCESS;            
        }
    }
    return UWB_FAIL;
}

state_t error(payment_device_t payment_device){
    LOG_ERR("%s", "Failed ranging");

    struct ranging_measurement r = {.final_tx_ts=0, 
                                    .poll_rx_ts=0,
                                    .poll_tx_ts=0,
                                    .resp_rx_ts=0,
                                    .resp_tx_ts=0};
    sprintf(r.dist, "%2.2f", 99.99);
    send_bytes(payment_device->board_channel,(uint8_t*) &r, sizeof(r));
    return UWB_EXIT;
}


state_t success(payment_device_t payment_device){
    send_bytes(payment_device->board_channel, (uint8_t*)&MEASURE(payment_device), sizeof(MEASURE(payment_device)));
    return UWB_EXIT;
}

payment_device_t  create_payment_device(role_t role){
    payment_device_t payment_device;
    payment_device = (payment_device_t) k_malloc(sizeof(*payment_device));
    payment_device->role = role;
    payment_device->vtable = VTABLE(role);
    channel_create(&(payment_device->board_channel));
    return payment_device;
}

void init_session(payment_device_t payment_device){
    memset(payment_device->session.iv, 0, KEY_SIZE);
    memset(payment_device->session.key, 0, IV_SIZE);
    payment_device->session.len = 0;
    payment_device->session.state = UWB_INIT;
}
