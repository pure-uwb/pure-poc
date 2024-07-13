if [ $# -eq 1 ]; then
	action=$1
	ids=$(adb devices -l | tail -n +2 | tr -s " " | cut -d " " -f 1)
	for id in $ids; do	
		echo "$action NFC on device $id"
		adb -s $id shell svc nfc $action 
	done	
	exit
fi

if [ $# -eq 2 ]; then
	action=$1
	tid=$2
	id=$(adb devices -l  | grep -e "$tid$" | cut -d " " -f 1)

	echo "$action NFC on device $id"
	adb -s $id shell svc nfc $action 
	exit
fi
echo "./nfc.sh [disable | enable] <device_id>*"

