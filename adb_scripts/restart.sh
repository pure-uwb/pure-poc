if [ $# -ne 2 ];then
	echo "./restart.sh <path_to_apk> <package/package.activity>"
	return
fi
apk=$1
activity=$2
ids=$(adb devices -l | tail -n +2 | cut -d ":" -f 5)
for id in $ids; do	
	echo "Restart on device $id"
	adb -t $id  install $apk
	adb -t $id  shell am start -n $activity  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
done	
