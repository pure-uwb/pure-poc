app_name=ch.ethz.pure

adb_scripts/nfc.sh disable

# Build application
./gradlew clean
./gradlew assembleDebug

# Install
adb_scripts/restart.sh  ./app/build/outputs/apk/debug/app-debug.apk ch.ethz.pure/.MainActivity
