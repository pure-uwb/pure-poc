ids=$(adb devices -l | tail -n +2 | cut -d ":" -f 5)
for id in $ids; do	
	echo "Disable NFC on device $id"
	adb -t $id shell svc nfc disable
done

for id in $ids; do
	echo "Enable NFC on device $id"
	adb -t $id shell svc nfc enable
done
