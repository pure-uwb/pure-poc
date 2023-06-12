ids=$(adb devices -l | tail -n +2 | cut -d ":" -f 5)
tags=$(adb devices -l | tail -n +2 | tr -s " " | cut -d " " -f 1)
echo $tags
for id in $tags; do	
	echo "Disable NFC on device $id"
	adb -s $id shell svc nfc disable
done

for id in $tags; do
	echo "Enable NFC on device $id"
	adb -s $id shell svc nfc enable
done

#for id in $ids; do	
#	echo "Disable NFC on device $id"
#	adb -t $id shell svc nfc disable
#done

#for id in $ids; do
#	echo "Enable NFC on device $id"
#	adb -t $id shell svc nfc enable
#done
