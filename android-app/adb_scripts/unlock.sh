if [ $# -eq 0 ]; then
	ids=$(adb devices -l | tail -n +2 | tr -s " " | cut -d " " -f 1)
	for id in $ids; do	
		unlocked=$(adb -s $id  shell dumpsys power | grep 'mHoldingDisplaySuspendBlocker' | cut -d "=" -f 2)
		if [ "$unlocked" = "true" ]; then
			echo "Unlocked $id"
		else
			echo "Unlocking $id"
			adb -s $id shell input keyevent 26
			sleep 1
			adb -s $id shell input touchscreen swipe 931 880 930 280
		fi
	done
	exit
fi

