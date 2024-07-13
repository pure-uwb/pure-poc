## How to connect

Execute `ssh -X  dan@5.tcp.eu.ngrok.io -p 10083`
Note: "-X" is useful to see the screen of the phones


## List devices
Execute `adb devices -l`. Following is the expected output
```
List of devices attached
10.42.0.14:5555        device product:flame model:Pixel_4 device:flame transport_id:2
10.42.0.24:5555        device product:t2sxxx model:SM_G996B device:t2s transport_id:5
```
Note that the transport_id may change. The Pixel is set to run as the terminal while the Samsung phone is set to run as a card.

## Installation
Change diractory to adoird-app executing `cd android-app`
To install the application on the phones execute 
`./adb_scripts/install.sh`.

As a preparation step for the transactio execution run `adb_scripts/unlock.sh`, this ensures that the devices are unlocked ./and ready for the transaction.

## Run a transaction

Execute `adb_scripts/transact.sh` to execute a transaction. The phones are physically next to each other, the transact script turns on the NFC to start the transaction. At the end of the transaction the NFC is switched off again on both devices.


## Get outputs 
The command `scrcpy -s <DEVICE_ID>` e.g. `scrcpy -s 10.42.0.14:5555` mirror the screen of the phone on your machine. Note that depending on the connection this can be rather laggy.

Execute `adb -t <transport_id_terminal> logcat |  grep -e "Timer" -e "PosEmulator"` e.g. `adb -t 2 logcat |  grep -e "Timer" -e "PosEmulator"` to see the log output of the terminal.

## Troubleshoot
If no transaction takes place after running the transact.sh script, try executing again the unlock.sh to ensure that devices are unlocked.

