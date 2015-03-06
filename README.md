# README #

1. Download the config files using the following commands: 
```shell
    $ git clone http://dinghy.egr.duke.edu/root/CrowdStreamFiles.git
    $ cd CrowdStreamFiles
    $ unzip faces-new.zip
    $ unzip videoframes-new.zip
```

2. Install OpenCV Manager apk to your phone (plugged in via usb). 
```shell
    $ adb install OpenCV_2.4.9_Manager_2.18_armv7a-neon.apk
```

3. Push configuration files to the phone. 
```shell
    $ adb push config.properties /sdcard/
    $ adb push faces-new /sdcard/
    $ adb push videoframes-new /sdcard/
```
