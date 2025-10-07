#!/bin/bash

echo "=== Встановлення PDF2Pass після завантаження емулятора ==="

export ANDROID_HOME=/usr/local/pkg/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

echo "Емулятор щойно завантажився. Чекаємо стабілізації..."
sleep 15

echo "Перезапускаємо ADB сервер..."
adb kill-server
sleep 2
adb start-server
sleep 3

echo "Перевіряємо підключення..."
adb devices

echo "Чекаємо поки емулятор буде готовий для встановлення додатків..."
for i in {1..20}; do
    echo "Спроба $i/20..."

    # Перевіряємо чи емулятор онлайн
    if adb shell pm list packages > /dev/null 2>&1; then
        echo "✓ Емулятор готовий для встановлення додатків!"
        break
    fi

    echo "Емулятор ще не готовий, чекаємо..."
    sleep 5
done

echo "Встановлюємо додаток PDF2Pass..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo "✓ Додаток встановлено успішно!"

    echo "Копіюємо ваш PDF файл на емулятор..."
    adb push /home/akomarovskyi/Downloads/1035140_1505558266.pdf /sdcard/Download/

    echo "Запускаємо додаток..."
    adb shell am start -n com.example.helloworld/.MainActivity

    echo ""
    echo "🎉 ГОТОВО! Додаток PDF2Pass запущено!"
    echo ""
    echo "Тепер на емуляторі:"
    echo "1. Натисніть кнопку 'Сканувати PDF'"
    echo "2. Виберіть файл '1035140_1505558266.pdf' з папки Downloads"
    echo "3. Подивіться як додаток витягує інформацію з вашого PDF файлу"
    echo "4. Перевірте створений пропуск в меню 'Переглянути пропуски'"

else
    echo "✗ Помилка встановлення додатку"
    echo "Спробуйте запустити скрипт знову через кілька хвилин"
fi
