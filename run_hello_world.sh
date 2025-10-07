#!/bin/bash

# Налаштування змінних середовища Android SDK
export ANDROID_HOME=/usr/local/pkg/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin

echo "=== Android Hello World Project Launcher ==="
echo "Android SDK: $ANDROID_HOME"

# Збираємо проект
echo "1. Збираємо Android проект..."
./gradlew assembleDebug

# Перевіряємо чи створився APK
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "2. APK створений успішно!"

    # Запускаємо ADB server
    echo "3. Запускаємо ADB server..."
    adb start-server

    # Перевіряємо підключені пристрої
    echo "4. Перевіряємо підключені пристрої..."
    adb devices

    # Встановлюємо APK на емулятор (якщо є підключений)
    echo "5. Встановлюємо додаток на емулятор..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk

    # Запускаємо додаток
    echo "6. Запускаємо Hello World додаток..."
    adb shell am start -n com.example.helloworld/.MainActivity

    echo "=== Додаток Hello World запущений! ==="
else
    echo "Помилка: APK файл не створений. Перевірте помилки збірки."
fi
