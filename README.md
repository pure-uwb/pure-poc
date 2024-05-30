# PoC for PURE

This README accompanies the PoC associated with the paper "PURE: Payments with UWB RElay-protection".

## Content 
- `android-app` contains the sources of the android applications used to implement PURE
- `pure-uwb` contains the sources necessary to flash the UWB boards
## Required hardware
The android app runs on Android supporting android API > 31. Threfore two android phones are necessary.

For the UWB ranging the following hardware is necessary 
- 2 x Qorvo DWM3000EVB
- 2 x Nordic Semiconductor nRF52 DK (pca10040)
- 2 x USB-C to USB-A adapter for connecting the boards to the phones.

Both the android app and the UWB firware can be tested separately in case the phones or the boards are missing. The interface between the android app and the UWB implementation is such that:
- The android phones write a ranging key over UART and expect timing inforation from the board.
- The board expects a ranging key over UART and reports the timings to the phones. 

## Steps to run a relay protected transaction
Flash two UWB Qorvo boards following the instructions in the README in `pure-uwb`.
Install either the standalone or the integrated app following the instruction in the README in `android-app`.

Note: as explained in the `android-app` README it is possible to run a trnasaction with a mocked board in case you do not have the required hardware listed in `pure-uwb`. 

## Timings
To run print the average and standard deviation of the collected timings run:
```
cd timings
pip install -r requirements.txt
python3 process.py
```

## Licence

Copyright (C) ETH Zurich

pure-poc is available under the GNU GLP v3 license. See the LICENSE file for more info.
Certain files in this project may have specific licenses or copyright
restrictions, as this project uses multiple open-source projects.
