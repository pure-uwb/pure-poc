/*
 * Copyright (c) 2012-2014 Wind River Systems, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <zephyr.h>
#include <uwb_ranging.h>
#include "app_main.h"

int main()
{	
	peripherals_init();
    spi_peripheral_init();

    k_sleep(K_MSEC(1000));
	
    payment_device_t payment_device = create_payment_device(DEVICE_TYPE);
    while(1){
        init_session(payment_device);
        uwb_ranging(payment_device);
    }
}
