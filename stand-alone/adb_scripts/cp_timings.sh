#!/bin/bash
if [ $# -ne 4 ]; then 
	echo "./cp_timings [reader|card] <input_file> <output_file> <device_id>"
	exit
fi
role=$1
input_file=$2
output_file=$3
device_id=$4
app_name=com.example.$1

adb -t $device_id shell "run-as $app_name  cat /data/data/$app_name/files/$input_file" > $output_file
adb -t $device_id shell "run-as $app_name rm /data/data/$app_name/files/$input_file"
#adb -t $device_id pull /sdcard/Download/$input_file $output_file
