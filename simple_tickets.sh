#!/bin/bash

echo "Копіювання з /home/akomarovskyi/tickets"

# Очищуємо Download від PDF
adb shell rm -f /sdcard/Download/*.pdf

# Створюємо папку
adb shell mkdir -p /sdcard/Download/Tickets

# Копіюємо файли
cd /home/akomarovskyi/tickets
for file in *; do
    echo "Копіюю: $file"
    adb push "$file" /sdcard/Download/
done

echo "Готово!"
