#include <adapter.h>
#include <drivers/uart.h>
#include <stdio.h>
#include <logging/log.h>

LOG_MODULE_REGISTER(uart_adapter, LOG_LEVEL_DBG);

#define BUF_SIZE 24
static K_MEM_SLAB_DEFINE(uart_slab, BUF_SIZE, 3, 4);


static void uart_callback(const struct device *dev, struct uart_event *evt, void *user_data);

struct channel{
	const struct device *lpuart;
	uint8_t rxbuf[BUF_SIZE];
	int rxlen;
	int txlen;
};


int send_bytes(channel_t channel, uint8_t*txbuf, int len){
	channel->txlen = 0;
	int err = uart_tx(channel->lpuart, txbuf, len, 10000);
	while(channel->txlen == 0){
		k_sleep(K_CYC(1));	
	}
	__ASSERT(err == 0, "Failed to initiate transmission");
    return err;
}

static void async(const struct device *lpuart, channel_t channel)
{
	int err;
	uint8_t *buf;

	err = k_mem_slab_alloc(&uart_slab, (void **)&buf, K_NO_WAIT);
	__ASSERT(err == 0, "Failed to alloc slab");

	err = uart_callback_set(lpuart, uart_callback, (void *)channel);
	__ASSERT(err == 0, "Failed to set callback");

	err = uart_rx_enable(lpuart, buf, BUF_SIZE, 10000); //TODO: reduce this value
	__ASSERT(err == 0, "Failed to enable RX");
	LOG_INF("%s", "After loop");
}

int channel_init(channel_t* channel){
 	(*channel)->rxlen = 0;
	struct uart_config cfg = {.baudrate = 115200, .data_bits=UART_CFG_DATA_BITS_8, .flow_ctrl=UART_CFG_FLOW_CTRL_NONE, .parity=UART_CFG_PARITY_NONE, .stop_bits=UART_CFG_STOP_BITS_1};
	(*channel)->lpuart = device_get_binding("ARDUINO_SERIAL");
	uart_configure((*channel)->lpuart, &cfg);
	__ASSERT((*channel)->lpuart, "Failed to get the device");
	async((*channel)->lpuart, (*channel));
	return 1;
}

int channel_create(channel_t* channel){
	*channel = (channel_t) k_malloc(sizeof(*channel));
}


int channel_destroy(channel_t channel){
	free(channel);
}


static void uart_callback(const struct device *dev,
			  struct uart_event *evt,
			  void *user_data)
{	
	struct device *uart = dev;
	int err;
	channel_t channel;

	switch (evt->type) {
	case UART_TX_DONE:
		channel = (channel_t) user_data;
		channel->txlen = evt->data.tx.len;
		LOG_INF("Tx sent %d bytes", evt->data.tx.len);
		LOG_HEXDUMP_INF(evt->data.tx.buf, evt->data.tx.len,  "Sending:");
		break;

	case UART_TX_ABORTED:
		LOG_ERR("%s", "Tx aborted");
		break;

	case UART_RX_RDY:
		// Uncomment to have loopback
		// err = uart_tx(dev, (evt->data.rx_buf.buf) + (evt->data.rx.offset),evt->data.rx.len, 10000);
		channel = (channel_t) user_data;
		if(evt->data.rx.len > BUF_SIZE){
			LOG_ERR("%s", "The received message exceeds the size of buffer");
		}
		memcpy(channel->rxbuf, (evt->data.rx_buf.buf) + (evt->data.rx.offset), evt->data.rx.len);
		channel->rxlen =  evt->data.rx.len;

		__ASSERT(err == 0, "Failed to initiate transmission");
		break;

	case UART_RX_BUF_REQUEST:
	{
		uint8_t *buf;
		err = k_mem_slab_alloc(&uart_slab, (void **)&buf, K_NO_WAIT);
		__ASSERT(err == 0, "Failed to allocate slab");

		err = uart_rx_buf_rsp(uart, buf, BUF_SIZE);
		__ASSERT(err == 0, "Failed to provide new buffer");
		break;
	}

	case UART_RX_BUF_RELEASED:
		k_mem_slab_free(&uart_slab, (void **)&evt->data.rx_buf.buf);
		break;

	case UART_RX_DISABLED:
		break;

	case UART_RX_STOPPED:
		break;
	}
}

int receive_bytes(channel_t channel, uint8_t* rxbuf){
	while(channel->rxlen == 0){
		// k_sleep(K_MSEC(500));
		k_sleep(K_CYC(1));	

	}
	memcpy(rxbuf, channel->rxbuf, channel->rxlen);
	return channel->rxlen;
}

