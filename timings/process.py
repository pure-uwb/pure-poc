import numpy as np
import pandas as pd
from matplotlib import pyplot as plt


def process_prerecorded(file ="./recorded_timings/prerecorded.csv"):
    df = pd.read_csv(file)
    messages = ["SEL1", "SEL2", "GPO", "RR1", "RR2", "RR3", "RR4", "GEN_AC"]
    df["total"] = df[messages].sum(axis = 1)
    df["GPO"] = df["GPO"] - df["GPO_MOD"] 
    df["GEN_AC"] = df["GEN_AC"] - df["GEN_AC_MOD"]
    # In the extended version the DH exchange is executed after GPO.
    # The sequence of event are the following
    # 1. Backend sends GPO command -> GPO timer start
    # 2. GPO exchagne
    # 3. GPO_MOD timer start -> DH exchange
    # 4. GPO_MOD timer finish
    # 5. GPO timer finish 
    df["DH"] = df["GPO_MOD"] 
    # Similarly for the GPO and DH commands, 
    # the GEN_AC measurement contains both the GEN_AC and the AUTH
    # GEN_AC_MOD measures the time to execute the AUTH command.
    df["AUTH"] = df["GEN_AC_MOD"]
    df["EMV_SPEC"] = df["total"] - df["AUTH"] - df["DH"]
    # Skip first transaction because it is slower.
    # All singletons and services have to initialized.
    df = df.iloc[1:]
    return df

def process_standalone():
    messages = ["SELECT", "SEND_HELLO", "RECEIVE_HELLO", "RANGE", "CERT", "AUTH", "FINISH"]
    standalone_df = pd.read_csv("./recorded_timings/stand-alone.csv")
    standalone_df["summed"] = standalone_df[messages].sum(axis = 1)
    standalone_df["DH"] = standalone_df[["SEND_HELLO", "RECEIVE_HELLO"]].sum(axis = 1)
    return standalone_df    

if __name__ == "__main__":
    prerec_df = process_prerecorded()
    standalone_df = process_standalone()
    
    print("Stand-alone timings (ms)\n")
    print(standalone_df[["DH", "CERT", "AUTH", "TOTAL"]].describe())
    print("\n")


    print("Integrated pre-recordeded timings (ms)\n")
    print(prerec_df[["DH", "AUTH", "EMV_SPEC"]].describe())
