#!/bin/bash
# Простий скрипт для копіювання файлів з tickets
adb shell "rm -f /sdcard/Download/*.pdf"
adb push /home/akomarovskyi/tickets/* /sdcard/Download/
adb shell "ls -la /sdcard/Download/"

