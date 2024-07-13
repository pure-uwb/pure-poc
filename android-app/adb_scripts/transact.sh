if [ $# -ne 0 ]; then
	echo "adb_scripts/transact.sh"
fi

terminal_id=$(adb devices -l | grep SM | rev | cut -d ":" -f 1 | rev)
card_id=$(adb devices -l | grep Pixel | rev | cut -d ":" -f 1 | rev)

echo "Terminal: $terminal_id"
echo "Card: $card_id"

./adb_scripts/nfc.sh enable $card_id
./adb_scripts/nfc.sh enable $terminal_id

sleep 1

./adb_scripts/nfc.sh disable
