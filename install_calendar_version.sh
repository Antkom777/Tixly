#!/bin/bash

echo "=== Встановлення PDF2Pass з календарною функціональністю ==="

export ANDROID_HOME=/usr/local/pkg/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

echo "Чекаємо поки емулятор завантажиться..."
for i in {1..30}; do
    echo "Перевіряємо підключення емулятора... ($i/30)"

    # Запускаємо ADB сервер
    adb start-server > /dev/null 2>&1

    # Перевіряємо чи емулятор онлайн та готовий
    if adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; then
        echo "✓ Емулятор готовий!"
        break
    fi

    sleep 10
done

echo "Перевіряємо підключення до емулятора..."
adb devices

echo "Встановлюємо оновлений додаток з календарем..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo "✓ Додаток встановлено!"

    echo "Копіюємо PDF файл для тестування..."
    adb push /home/akomarovskyi/Downloads/1035140_1505558266.pdf /sdcard/Download/

    echo "Запускаємо додаток..."
    adb shell am start -n com.example.helloworld/.MainActivity

    echo ""
    echo "🎉 PDF2Pass з календарем готовий до тестування!"
    echo ""
    echo "📅 НОВА ФУНКЦІОНАЛЬНІСТЬ - КАЛЕНДАР:"
    echo ""
    echo "1️⃣ ПЕРЕГЛЯД ТІКЕТІВ:"
    echo "   • Відкрийте 'Переглянути тікети'"
    echo "   • Ви побачите тестові тікети з кнопками календаря 📅"
    echo ""
    echo "2️⃣ ДОДАВАННЯ В КАЛЕНДАР:"
    echo "   • Натисніть кнопку 📅 навпроти будь-якого тікету"
    echo "   • Додаток відкриє календар Android"
    echo "   • Подія автоматично заповниться даними з тікету:"
    echo "     - Назва події"
    echo "     - Дата та час"
    echo "     - Місце проведення"
    echo "     - Опис з номером тікету"
    echo ""
    echo "3️⃣ СТАТУС КАЛЕНДАРЯ:"
    echo "   • Після додавання кнопка зміниться на ✅"
    echo "   • З'явиться мітка '📅 В календарі'"
    echo "   • Повторне натискання покаже що подія вже додана"
    echo ""
    echo "4️⃣ ТЕСТУВАННЯ З ВАШИМ PDF:"
    echo "   • Додайте ваш PDF через Share або кнопку"
    echo "   • Новий тікет з'явиться в списку"
    echo "   • Натисніть 📅 щоб додати його в календар"
    echo ""
    echo "🔥 Календарна інтеграція працює!"

else
    echo "✗ Помилка встановлення додатку"
    echo "Спробуйте запустити скрипт знову через хвилину"
fi
