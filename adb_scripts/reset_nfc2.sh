#ids=$(adb devices -l | tail -n +2 | cut -d ":" -f 5)
#while true; do
ids="10 24 275"
for id in $ids; do
	echo $id
	adb -t $id shell svc nfc disable
	adb -t $id shell svc nfc enable
done	
