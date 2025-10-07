#!/bin/bash

# Швидкий запуск без перевірки емулятора
echo "=== Швидкий запуск PDF2Pass ==="

export ANDROID_HOME=/usr/local/pkg/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

echo "Перевіряємо статус емулятора..."
adb devices

# Чекаємо поки емулятор буде онлайн
echo "Чекаємо поки емулятор завантажиться..."
for i in {1..30}; do
    DEVICE_STATUS=$(adb devices | grep emulator | grep -v offline | grep device)
    if [ ! -z "$DEVICE_STATUS" ]; then
        echo "✓ Емулятор готовий!"
        break
    fi
    echo "Чекаємо... ($i/30) - емулятор ще завантажується"
    sleep 5
done

# Перевіряємо чи емулятор онлайн
DEVICE_STATUS=$(adb devices | grep emulator | grep -v offline | grep device)
if [ -z "$DEVICE_STATUS" ]; then
    echo "✗ Емулятор досі offline. Спробуйте перезапустити емулятор."
    echo "Команда для перезапуску: adb kill-server && adb start-server"
    exit 1
fi

echo "Встановлюємо додаток..."
INSTALL_RESULT=$(adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1)

if [[ $INSTALL_RESULT == *"Success"* ]]; then
    echo "✓ Додаток встановлено!"

    echo "Запускаємо додаток..."
    adb shell am start -n com.example.helloworld/.MainActivity

    echo "Копіюємо ваш PDF файл на емулятор..."
    adb push /home/akomarovskyi/Downloads/1035140_1505558266.pdf /sdcard/Download/

    echo "✓ Готово! Тепер в додатку:"
    echo "1. Натисніть 'Сканувати PDF'"
    echo "2. Виберіть файл '1035140_1505558266.pdf' з папки Downloads"
    echo "3. Подивіться результат обробки PDF"
else
    echo "✗ Помилка встановлення: $INSTALL_RESULT"
fi
