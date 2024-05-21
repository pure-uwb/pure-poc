## PURE: Payments with UWB RElay-protection

This README accompanies the PoC application associated with the paper "PURE: Payments with UWB RElay-protection".
This repository complements [pure-poc](https://github.com/daniCoppola/pure-poc) with the firmware needed for flashing the UWB boards.

## Required hardware
* 2 x [Qorvo DWM3000EVB](https://www.qorvo.com/products/p/DWM3000EVB)
* 2 x [Nordic Semiconductor nRF52 DK (pca10040)](https://www.nordicsemi.com/Products/Development-hardware/nrf52-dk)
* 2 x USB-C to USB-A adapter for connecting the boards to the phones.

## Additional resources

Please refer to the official documentation from Qorvo, and NXP to setup their devices for UWB distance measurement:
* [Qorvo DWM3000 API Software and API Guide](https://www.qorvo.com/products/d/da007992)
* [Nordic SDK](https://developer.nordicsemi.com/nRF_Connect_SDK/doc/latest/nrf/installation/install_ncs.html)

## Environment

We provide a Vagrant Virtual Machine to compile and flash our firmware.
Please refer to the official [Vagrant](https://www.vagrantup.com/) and [Virtualbox](https://www.virtualbox.org/) documentation for installing and using vagrant.
In short, on Ubuntu ```sudo apt install virtualbox vagrant```.

The Vagrant VM is configured using the ```vagrant/Vagrantfile``` and ```vagrant/bootstrap.sh``` files.
On a Linux machine you can follow the self-explanatory commands in ```vagrant/boostrap.sh``` for a native installation.
We use [west](https://docs.zephyrproject.org/latest/develop/west/index.html) to setup the necessary dependencies. 

Useful commands:

```cd pure-uwb/vagrant```
* ```vagrant up``` # Turn the VM on, the first time the VM is created (slow)
* ```vagrant up --provision``` # To reprovision ```bootstrap.sh```
* ```vagrant ssh``` # Connect to the VM
* ```vagrant halt``` # Halt the VM
* ```vagrant destroy``` # Destroy the VM
* ```VboxManage export pure-uwb -o pure-uwb.ova``` # Export .ova

The login is:
* user ```vagrant```
* password ```vagrant```

***JLink manual installation:***

Unfortunately you need to install JLink manually, as follows:
* \[Host\] Download [SEGGER JLink](https://www.segger.com/downloads/jlink/JLink_Linux_V786e_x86_64.deb) and place it the ```pure-uwb/vagrant/``` folder. You need to accept the license.
* \[VM\] ```sudo dpkg -i /vagrant/JLink_Linux_V782_x86_64.deb``` (Adjust the name if necessary)


## Usage

### Compile the firmware

Connect the Qorvo DWM3000UWB + Nordic nRF52 DK to your host machine via USB,
make sure the USB is captured by the VM (run ```lsusb``` from within the VM to
see which devices are visible, use the Virtual Box interface to add your device
if necessary). If the device is not detected, make sure your user is in the  vboxusers, and that the usb is selected in VirtualBox (Settings->Usb->Add usb->Select JLink Segger).

To build the firmware execute

```
cd ~/ncs/nrf/samples/emv_ranging
cmake -B build_card -DDEVICE_TYPE=CARD
cmake -B build_terminal -DDEVICE_TYPE=TERMINAL
cd build_card && make 
cd ../build_terminal && make
```
Prebuilt binaries are available in the `emv_ranging/release` folder.

### Flash the firmware
Use the script install_hex.sh in `~/ncs/nrf/samples` to flash the boards. Execute 
```
nrfjprog -i
```
to list the identifier of the connected boards.
Flash one board with the terminal hex and one with the card hex. E.g.:
```
cd /home/vagrant/ncs/nrf/samples
./install_hex.sh emv_ranging/build_card/zephyr/zephyr.hex <CARD-BOARD-ID>
./install_hex.sh emv_ranging/build_terminal/zephyr/zephyr.hex <TERMINAL-BOARD-id>
```
NOTE: Make sure to know which board has the card firmware and which has the terminal one. This is important to connect the correct board to the correct phone. 
Specifically, connect the terminal board with the phone running the terminal app and the card board with the phone running the card app.

***Solution if flash hangs and fails.***
Sometimes, nrfjtools have some issues running from the VM as described 
[here](https://devzone.nordicsemi.com/f/nordic-q-a/76877/nrf52840-dk-disconnects-when-programming-in-a-virtual-linux-environment).
In this case, install JLink and nrfjtools on your host machine 
from [here](https://www.nordicsemi.com/Products/Development-tools/nrf-command-line-tools/download) then run
```nrfjprog -f nRF52 -r --log``` (with the VM off). You can now turn the VM on again and 
use ```./install_hex.sh``` to flash the firmware.

### General setup

To run the PURE poc the following steps should be followed:
1. Install pure-poc on two android phones
2. Install on two boards the card firmware and the temrinal firmware
3. Connect over USB the boards to the phones. (see NOTE 1 in this README)

## Troubleshooting
* Make sure in settings of terminal and card to select Prerecorded backend and to unset Mock uart. This last setting can be used to completely abstract the UWB boards for testing purposes.
* The first time you open the app with a board connected you should get a pop up asking to `Allow EMV-UWB to access J-Link`. Select OK to give the application permission to use UART. This is needed for communication between the board and the phone. If the pop-up does not appear, please plug and unplug the board and then restart the application.
* To test the board code separately from android application (on the host machine) 
```
picocom  --baud 115200 --databits  8 --stopbits 1 --parity n --imap lfcrlf,crcrlf --flow none  </dev/tty*>
```
is useful to connect to the serial port of the boards. As an initial example one could:
    1. Connect both boards to the laptop
    2. Connect to the respective tty
    3. Write a string in both tty

The boards will interpret such string as the key used to generate the STS. Ranging will be executed and the computated distance reported over UART.
 
## Files

### In this repo
```
├── README.md
└── vagrant
    ├── bootstrap.sh
    ├── emv_ranging
    ├── install_hex.sh
    ├── Vagrantfile
    └── west.yml
```
The folder emv_ranging contains the source code for the UWB part of pure. It handles the communication over UART with the phones and executes UWB ranging between the two boards using as secret the key passed over UART by the phones. 
The `bootstrap.sh` file is executed when the VM is provisioned. Follow the steps in `bootstrap.sh` for local installation

### In the VM after installation

```.
└── ncs
    ├── ...
    ├── dwm3000 
    ├── zephyr
    └── nrf
        ├── ...
        └── samples
                ├── ...
                ├── emv_ranging
                └── install_hex.sh
```
The folder `dwm3000` contains the drivers for controlling the DWM3000 board. The `zephyr` folder contains zephyr OS. In `nrf/samples` the folder `emv_ranging` folder contains the code executed by the board to perform ranging. Finally, `install_hex.sh` is a utility script to install the hex once a sample is compiled. 

## Copyright 
The source code in emv_ranging is a modified version of the two-way ranging example released under the GNU GLP v3 License in [dwm3000](https://github.com/foldedtoad/dwm3000).
