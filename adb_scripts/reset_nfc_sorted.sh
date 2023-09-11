#!/bin/bash
devices="motorola Pixel_4 SM_G996B Pixel_2 HWMAR"
for dev in $devices; do	
	id=$(adb devices -l | grep $dev | tr -s " " | cut -d " " -f 1)
	echo Reset $dev with id: $id
	adb -s $id shell svc nfc disable
done

read -p "Press any key to reactivate ..."
for dev in $devices; do	
	id=$(adb devices -l | grep $dev | tr -s " " | cut -d " " -f 1)
	echo Reset $dev with id: $id
	adb -s $id shell svc nfc enable
done
