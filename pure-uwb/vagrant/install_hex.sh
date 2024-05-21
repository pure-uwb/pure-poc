if [ $# != 2 ];then
 	echo "./install_hex.sh <path_to_bin> <board_serial_number>"
	echo
    echo e.g. /install_hex.sh emv_ranging/build_card/zephyr/zephyr.hex 682218717
    echo
    echo "Use nrfjprog -i to get the boards' serial number"
    
    exit
fi
serial_number=$2
nrfjprog -f nRF52 -e -s $serial_number
nrfjprog -f nRF52 --program $1 -s $serial_number
nrfjprog -f nRF52 -r -s $serial_number
