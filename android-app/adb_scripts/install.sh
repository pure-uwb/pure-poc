if [ $# -ne 2 ]; then 
	echo "./install.sh <card_terminal_id> <terminal_device_id>"
	exit
fi
card_id=$1
terminal_id=$2
app_name=ch.ethz.pure

adb_scripts/nfc.sh disable

# Build application
./gradlew clean
./gradlew assembleDebug

# Install
adb_scripts/restart.sh  ./app/build/outputs/apk/debug/app-debug.apk ch.ethz.pure/.MainActivity
