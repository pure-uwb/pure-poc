ids=$(adb devices -l | tail -n +2 |  tr -s " " | cut -d " " -f 1)
for id in $ids; do	
	echo "Connect to $id via wifi"
	IP=$(adb -s $id shell ip addr show wlan0 | tr -d "\n" | tr -s " " | cut -d " " -f 19| cut -d "/" -f 1)
	adb -s $id tcpip 5555
	sleep 2
	adb -s $id connect $IP:5555

done

