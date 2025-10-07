#!/bin/bash

echo "=== Запуск додатку PDF2Pass ==="

# Налаштування Android SDK
export ANDROID_HOME=/usr/local/pkg/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin

echo "1. Збираємо додаток..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✓ APK зібрано успішно!"

    echo "2. Перевіряємо підключення до емулятора..."
    DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)

    if [ $DEVICES -eq 0 ]; then
        echo "⚠ Емулятор не знайдено. Запускаємо емулятор..."

        # Перевіряємо чи є доступні AVD
        AVD_LIST=$(emulator -list-avds)
        if [ -z "$AVD_LIST" ]; then
            echo "Створюємо новий емулятор..."
            avdmanager create avd -n PDF2PassEmulator -k "android-35" --device "pixel_5" --force
            AVD_NAME="PDF2PassEmulator"
        else
            AVD_NAME=$(echo "$AVD_LIST" | head -1)
            echo "Використовуємо існуючий емулятор: $AVD_NAME"
        fi

        # Запускаємо емулятор у фоновому режимі
        echo "Запускаємо емулятор $AVD_NAME..."
        emulator -avd $AVD_NAME -no-audio -no-snapshot-save &
        EMULATOR_PID=$!

        # Чекаємо поки емулятор завантажиться
        echo "Чекаємо запуску емулятора (це може зайняти кілька хвилин)..."
        for i in {1..60}; do
            sleep 5
            DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
            if [ $DEVICES -gt 0 ]; then
                echo "✓ Емулятор підключено!"
                break
            fi
            echo "Чекаємо... ($i/60)"
        done

        if [ $DEVICES -eq 0 ]; then
            echo "✗ Не вдалося підключитися до емулятора"
            exit 1
        fi
    else
        echo "✓ Емулятор вже підключено"
    fi

    # Показуємо підключені пристрої
    echo "Підключені пристрої:"
    adb devices

    echo "3. Встановлюємо додаток на емулятор..."
    INSTALL_RESULT=$(adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1)

    if [[ $INSTALL_RESULT == *"Success"* ]]; then
        echo "✓ Додаток встановлено успішно!"

        echo "4. Запускаємо додаток..."
        adb shell am start -n com.example.helloworld/.MainActivity

        echo "✓ Додаток PDF2Pass запущено на емуляторі!"
        echo ""
        echo "Тепер ви можете:"
        echo "- Натиснути 'Сканувати PDF' для обробки PDF файлів"
        echo "- Натиснути 'Переглянути пропуски' для перегляду збережених квитків"
        echo "- Натиснути 'Вихід' для закриття додатку"
        echo ""
        echo "Для тестування з вашим PDF файлом виконайте:"
        echo "adb push /home/akomarovskyi/Downloads/1035140_1505558266.pdf /sdcard/Download/"

    else
        echo "✗ Помилка встановлення додатку: $INSTALL_RESULT"
    fi

else
    echo "✗ Помилка збірки додатку"
fi
