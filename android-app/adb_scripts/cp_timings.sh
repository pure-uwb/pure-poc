#!/bin/bash
if [ $# -ne 3 ]; then 
	echo "./cp_timings <input_file> <output_file> <device_id>"
	exit
fi
input_file=$1
output_file=$2
device_id=$3
app_name=ch.ethz.nfcrelay

adb -t $device_id shell "run-as $app_name  cat /data/data/$app_name/files/$input_file" > $output_file
adb -t $device_id shell "run-as $app_name rm /data/data/$app_name/files/$input_file"
#adb -t $device_id pull /sdcard/Download/$input_file $output_file
