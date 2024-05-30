# Android PoC for PURE

This README accompanies the PoC android application associated with the paper "PURE: Payments with UWB RElay-protection".
It is supposed to be used with two android phones together with two nrf52 boards and QorvoDWM3000 flashed as described in the README in the `pure-uwb` folder.

## Content

This folder contains:
- The stand-alone version of the protocol in the `stand-alone` folder
- The integrated version at the root of the repo.

## How to build
Both the stand alone and the integrated versions can be built with gradle. We suggest using Android Studio to open and build the project.

# Stand-alone
## How to use
- Flash the boards with the firware in [pure-uwb-board](git@github.com:daniCoppola/pure-uwb.git)
- Connect each board to an android device
- Install the reader and card application on two android devices making sure it matches the firmware of the board. At this point you will be asked to grant the application the permission to access the boars.In case you install the application before connecting the boards, please restart the application in order to enable USB access. 
- Place the devices in contact and see the transaction execution. 

# Integrated version
## How to use
Install the application on two android devices. In the settings (top right) set one phone to be a card and one to be a reader. For the card, you will have to enable the application to be a payment application.
In the settings: 
* transparent: enables or disables the UWB extension. You can use it to check the negotiation of the extension. For example, on the card device click on transparent to see that the transaction will still execute but without the extension.
* mock uart: in case you do not have two nrf52 boards and  Qorvo, select this option to still run the application and execute a transaction.
  Instead of measuring the distance with uwb the reader mocks in software a measured distance. If mock uart is not set then the two uwb chips measure distance and report it back to the phones.
* prerecorded backend: leave this option ticked to execute the application with a pre-recorded transaction. For the live backend descrived in the Appendix of the paper, follow the instruction in 'Live Backend'. 

On the reader the bottom right widget allows to copy the transaction transcript. You can use tools like [tlv utils](https://emvlab.org/tlvutils/) to parse single messages.

## Live Backend
To use live backends the following is necessary:
- 4 android phones
- 2 UWB board (as in the prerecorded case)
- 1 real mastercard card
- 1 real reader

The following diagram shows how each phone should be set:
<pre>
┌───────────┐                                                                                               ┌──────────────┐              
│           │                                                                                               │              │              
│ Real Card │                                                                                               │  Real Reader │              
│           │                                                                                               │              │              
└───────────┘                                                                                               └──────────────┘              
      ▲                                                                                                             ▲                     
      │ NFC                                                                                                         │ NFC                 
      ▼                                                                                                             ▼                     
┌─────────────────────────┐                  ┌─────────────────────┐         ┌────────────────────┐         ┌────────────────────────────┐
│Reader                   │                  │ UWB-Card            │         │ UWB-Reader         │         │ Card                       │
│                         │   Connects to    │                     │         │                    │         │                            │
│transparent: true        │ server backend   │ transparent: false  │   NFC   │ transparent: false │         │ transparent: true          │
│mock uart: true          │◄─────────────────┤ mock uart: false    │◄───────►│ mock uart: false   │◄────────┤ mock uart: true            │
│prerec:false             │                  │ prerec: false       │         │ prerec: false      │         │ prerec: false              │
│                         │                  │                     │         │                    │         │                            │
│This acts as the server  │                  └─────────────────────┘         │                    │         │ This acts as backend       │
│backend for the uwb-card │                                                  └────────────────────┘         │ for the UWB-reader.        │
│                         │                                                                                 │ Connects to the server on  │
└─────────────────────────┘                                                                                 │ the UWB-reader and sends   │
                                                                                                            │ the command received from  │
                                                                                                            │ the real reader.           │
                                                                                                            │                            │
                                                                                                            └────────────────────────────┘
</pre>
UWB-Card and UWB-reader should be attached to UWB boards flashed with the respective code.
In order to ensure communication (Reader <-> UWB-card), and (UWB-Reader <-> Card) all phones should be connected to the same network and on the Card and UWB-card the IP of the respective reader should be set via the settings.
Connectivty between phones can be checked in the following way:
1. Connect to the shell on one of the phones (adb -t <transport id> shell)
2. Check the phoen IP (ip addr)
3. Connect to the shell of the other phone and ping the previous IP.

The UWB enabled transactions will take place between the UWB-reader and UWB-card. 

Once all phones are set with the above configuration and wifi communication was checked, to execute the transaction:
1. Place Real Card and Reader in contact such that the screen on the reader turns green.
2. Place UWB-Card and UWB-reader in contact such the the UWB-reader screen turns green.
3. Start the transaction on the reader and place Card and Real Reader in contact.

You can check that the transaction between UWB-card and UWB-reader contains the extension and fails if a relay is in the middle (we checked with a coax cable soldiered with NFC antennas at the two ends).

## High-level software description
The application consists of the following modules:
- emvnfccard contains utils code from [android-emv-key-test](https://github.com/johnzweng/android-emv-key-test/tree/master) and  [EMV-NFC-Paycard-Enrollment
Public](https://github.com/devnied/EMV-NFC-Paycard-Enrollment)
- emvextension contains the code responsible for the extension. CardController and ReaderController generate the extension messages according to the protocol and orchestrate the exchange over NFC, and the communication with the UWB boards.
- app contains the code responsible for the EMV transaction, either using pre-recorded transaction or with live backends.

Following is a high level description of the code control flow:
0. The app starts and the reader waits for an NFC connection.
1. When an NFC connection is established the function onTagDiscovered in MainActivity is executed.
2. Until the end of the protocol the reader:
  2.1 Wait for a connection from the backend containing the following command (run method of PosEmulator class).
  2.2 Sends the command to via NFC and waits for the response.
  2.3 Provides the emvextension with the command / response and, if needed exchanges the additional messages of the extension (protocolModifierImpl).
  
  Until the end of the protocol the card:
  2.1 Receives a command over NFC (processCommandApdu in EmvRaceApduService).
  2.2 Connects to the server backend and forwards the command (ResponseResolver).
  2.3 Forwards the response over NFC to the reader.
  2.4 Provides the emvextension with the command / response and, if needed exchanges the additional messages of the extension (protocolModifierImpl).


## Timings
The timings folder contains the dataset collected for 500 transactions with the standalone implementation (not yet published) and the integrated version (in this repository). The file process.py analyses the csv files and reports the timings in Table 2.

## Credits
The folder emvnfccard contains code from [android-emv-key-test](https://github.com/johnzweng/android-emv-key-test) and  [EMV-NFC-Paycard-Enrollment
Public](https://github.com/devnied/EMV-NFC-Paycard-Enrollment) respectively released under the GNU GLP v3 license and apache 2 license.
We made minor modifications to the sources of these libraries. 

