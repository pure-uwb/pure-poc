#define STATE_NUMBER 6
#define KEY_SIZE 16
#define IV_SIZE 16

typedef enum STATE{
	UWB_INIT, UWB_DERIVE_KEY, UWB_RANGING, UWB_FAIL, UWB_SUCCESS, UWB_EXIT
} state_t;

static const char * states[] = {
	"UWB_INIT         ", 
	"UWB_DERIVE_KEY   ", 
	"UWB_RANGING      ", 
	"UWB_FAIL         ", 
	"UWB_SUCCESS      ",
	"UWB_EXIT         "
};

typedef struct payment_device* payment_device_t;

typedef enum{
	CARD, TERMINAL
} role_t;
 
typedef struct payment_device* payment_device_t;
typedef state_t (*function_ptr_t)(payment_device_t);


payment_device_t create_payment_device(role_t rolee);
void init_session(payment_device_t device);
int destroy_payment_device(payment_device_t device);

state_t init_card(payment_device_t payment_device);
state_t init_terminal(payment_device_t payment_device);
state_t uwb_derive_key(payment_device_t payment_device);


state_t error(payment_device_t payment_device);
state_t success(payment_device_t payment_device);

int uwb_ranging(payment_device_t payment_device);

state_t terminal_range(payment_device_t payment_device);
state_t card_range(payment_device_t payment_device);

// payment_device_t * initialize_payment_device(role_t role);


state_t error(payment_device_t payment_device);
state_t success(payment_device_t  payment_device);

static function_ptr_t vtable_card[STATE_NUMBER]={
	init_card,
	uwb_derive_key,
	card_range,
	error,
	success
};

static function_ptr_t vtable_terminal[STATE_NUMBER]={
	init_terminal,
	uwb_derive_key,
	terminal_range,
	error,
	success
};



