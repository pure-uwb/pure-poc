# Android PoC for PURE

This README accompanies the PoC android application associated with the paper "PURE: Payments with UWB RElay-protection".
It is supposed to be used with two android phones together with two nrf52 boards and QorvoDWM3000 flashed with the code in [pure-uwb-board](add-link).

## How to build
The application can be build with gradle. If you want to build from source / make changes we suggest using Android Studio. Additionally, we provide an apk you can use to directly install the app.

## How to use
Install the application on two android devices. In the settings (top right) set one phone to be a card and one to be a reader. For the card, you will have to enable the application to be a payment application.
In the settings: 
* transparent: enables or disables the UWB extension. You can use it to check the negotiation of the extension. For example, on the card device click on transparent to see that the transaction will still execute but without the extension.
* mock uart: in case you do not have two nrf52 boards and  Qorvo, select this option to still run the application and execute a transaction.
  Instead of measuring the distance with uwb the reader mocks in software a measured distance. If mock uart is not set then the two uwb chips measure distance and report it back to the phones.
* prerecorded backend: leave this option ticked to execute the application with a pre-recorded transaction. For the live backend descrived in the Appendix of the paper, follow the instruction in 'Live Backend'. 

On the reader the bottom right widget allows to copy the transaction transcript. You can use tools like [tlv utils](https://emvlab.org/tlvutils/) to parse single messages.

## Live Backend
TODO


## Timings
The timings folder contains the dataset collected for 500 transactions with the standalone implementation (not yet published) and the integrated version (in this repository). The file process.py analyses the csv files and reports the timings in Table 2.

## Credits
The folder emvnfccard contains code from [android-emv-key-test](https://github.com/johnzweng/android-emv-key-test/tree/master) and  [EMV-NFC-Paycard-Enrollment
Public](https://github.com/devnied/EMV-NFC-Paycard-Enrollment).

