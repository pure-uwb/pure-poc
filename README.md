# PoC for PURE

This README accompanies the PoC associated with the paper "PURE: Payments with UWB RElay-protection".

## Content 
- `android-app` contains the sources of the android applications used to implement PURE
- `pure-uwb` contains the sources necessary to flash the UWB boards 

We refer to the README in the respective folder for the intstruction on how to use this.
Additionally, the folder `android-app/timings` contains the collected transaction timings. 


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
