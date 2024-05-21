# SPDX-License-Identifier: Apache-2.0

cmake_minimum_required(VERSION 3.20.0)

set(WORKSPACE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../")
set(DW_BOARD_ROOT "${WORKSPACE_ROOT}dwm3000/")
set(BOARD_ROOT "${WORKSPACE_ROOT}dwm3000/")
set(DTS_ROOT "${WORKSPACE_ROOT}dwm3000/")
set(SHIELD qorvo_dwm3000)

set(BOARD  nrf52dk_nrf52832)
find_package(Zephyr REQUIRED HINTS $ENV{ZEPHYR_BASE})
project(emv_ranging)

#set(DEVICE_TYPE TERMINAL)
configure_file(${PROJECT_SOURCE_DIR}/include/app_main.h.in app_main.h)
target_include_directories(app PUBLIC "${PROJECT_BINARY_DIR}")

message("PROJECT SOURCE DIR: ${PROJECT_SOURCE_DIR}")
target_sources(app PRIVATE ${PROJECT_SOURCE_DIR}/src/app_main.c ${PROJECT_SOURCE_DIR}/src/uwb_ranging/uwb_ranging.c ${PROJECT_SOURCE_DIR}/src/communicate/communicate.c)
#target_sources(app PRIVATE ../../main.c)
target_sources(app PRIVATE ${PROJECT_SOURCE_DIR}/src/range/range.c)
target_sources(app PRIVATE ${PROJECT_SOURCE_DIR}/src/uwb_ranging/uart_adapter.c)

target_include_directories(app PRIVATE ${PROJECT_SOURCE_DIR}/include ${PROJECT_SOURCE_DIR}/include/uwb_ranging  ${PROJECT_SOURCE_DIR}/include/communicate)
target_include_directories(app PRIVATE ${PROJECT_SOURCE_DIR}/include/range)

# DECAWAVE part
target_sources(app PRIVATE ${DW_BOARD_ROOT}/decadriver/deca_device.c)
target_sources(app PRIVATE ${DW_BOARD_ROOT}/platform/port.c)
target_sources(app PRIVATE ${DW_BOARD_ROOT}/platform/deca_sleep.c)
target_sources(app PRIVATE ${DW_BOARD_ROOT}/platform/deca_spi.c)
target_sources(app PRIVATE ${DW_BOARD_ROOT}/config_options.c)

target_sources(app PRIVATE ${DW_BOARD_ROOT}/shared_data/shared_functions.c)
target_include_directories(app PRIVATE ${DW_BOARD_ROOT})
target_include_directories(app PRIVATE ${DW_BOARD_ROOT}/decadriver/)
target_include_directories(app PRIVATE ${DW_BOARD_ROOT}/platform/)
target_include_directories(app PRIVATE ${DW_BOARD_ROOT}/compiler/)
target_include_directories(app PRIVATE ${DW_BOARD_ROOT}/shared_data/)


