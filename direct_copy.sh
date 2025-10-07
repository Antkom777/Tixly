adb shell "rm -f /sdcard/Download/*.pdf"
adb push /home/akomarovskyi/tickets/* /sdcard/Download/
adb shell "ls /sdcard/Download/"

