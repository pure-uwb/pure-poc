# Copyright (c) 2022 ETH Zurich

sudo apt update
sudo apt-get install -y vim git tig unzip libimobiledevice-utils
mkdir ~/tools/

# Install nordic tools (see https://github.com/eurecom-s3/screaming_channels/blob/master/docker/Dockerfile)
cd ~/tools/ 
wget https://www.nordicsemi.com/-/media/Software-and-other-downloads/Desktop-software/nRF-command-line-tools/sw/Versions-10-x-x/10-13-0/nRF-Command-Line-Tools_10_13_0_Linux64.zip 
unzip nRF-Command-Line-Tools_10_13_0_Linux64.zip 
rm nRF-Command-Line-Tools_10_13_0_Linux64.zip
cd nRF-Command-Line-Tools_10_13_0_Linux64 
tar -xvzf nRF-Command-Line-Tools_10_13_0_Linux-amd64.tar.gz 
sudo dpkg -i nRF-Command-Line-Tools_10_13_0_Linux-amd64.deb

# Install Zephyr OS (see https://docs.zephyrproject.org/latest/develop/getting_started/index.html)
cd ~/tools/ 
wget https://apt.kitware.com/kitware-archive.sh
sudo bash kitware-archive.sh
sudo apt install -y --no-install-recommends libx32asan5 git cmake ninja-build gperf \
  ccache dfu-util device-tree-compiler wget \
  python3-dev python3-pip python3-setuptools python3-tk python3-wheel xz-utils file \
  make gcc gcc-multilib g++-multilib libsdl2-dev libmagic1
pip3 install --user -U west
pip3 install pyelftools
echo 'export PATH=~/.local/bin:"$PATH"' >> ~/.bashrc
source ~/.bashrc

cd ~
wget https://github.com/zephyrproject-rtos/sdk-ng/releases/download/v0.15.1/zephyr-sdk-0.15.1_linux-x86_64.tar.gz
wget -O - https://github.com/zephyrproject-rtos/sdk-ng/releases/download/v0.15.1/sha256.sum | shasum --check --ignore-missing
tar xvf zephyr-sdk-0.15.1_linux-x86_64.tar.gz
rm zephyr-sdk-0.15.1_linux-x86_64.tar.gz
cd zephyr-sdk-0.15.1
./setup.sh -t all -h -c
sudo cp ~/zephyr-sdk-0.15.1/sysroots/x86_64-pokysdk-linux/usr/share/openocd/contrib/60-openocd.rules /etc/udev/rules.d
sudo udevadm control --reload

# Install zephyr
echo "Install Zephyr"
base_path=/home/vagrant/
west_file_path=/vagrant/west.yml
cd $base_path
mkdir ncs
cd ncs
~/.local/bin/west init -m https://github.com/nrfconnect/sdk-nrf --mr v1.9-branch
cp $west_file_path nrf/west.yml
~/.local/bin/west update

# 4. Handle changes in paths
cd $base_path/ncs/zephyr/include
cp zephyr/types.h .
rm -r zephyr
ln -s . zephyr
~/.local/bin/west zephyr-export

# Move emv_ranging code to the nrf samples folder
cp -r /vagrant/emv_ranging $base_path/ncs/nrf/samples
cp /vagrant/install_hex.sh $base_path/ncs/nrf/samples
chmod +x $base_path/ncs/nrf/samples/install_hex.sh


# Reminder
sudo apt-get install -y libxcb1-dev
sudo apt-get install libxcb-render0
sudo apt --fix-broken install --yes
echo ""
echo "REMINDER!!!!!"
echo "Install JLink, see README.md"
echo ""
