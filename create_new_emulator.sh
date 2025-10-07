#!/bin/bash

echo "=== Створення нового емулятора PDF2Pass ==="

export ANDROID_HOME=/usr/local/pkg/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin

# Зупиняємо все
echo "1. Зупиняємо старі процеси..."
adb kill-server
pkill -f emulator
sleep 3

# Видаляємо старі емулятори
echo "2. Видаляємо старі емулятори..."
rm -rf ~/.android/avd/HelloWorldEmulator.avd 2>/dev/null
rm -rf ~/.android/avd/PDF2PassEmulator.avd 2>/dev/null
rm -f ~/.android/avd/HelloWorldEmulator.ini 2>/dev/null
rm -f ~/.android/avd/PDF2PassEmulator.ini 2>/dev/null

# Створюємо новий емулятор з API 34 (більш стабільний)
echo "3. Створюємо новий емулятор з Android API 34..."
echo "no" | avdmanager create avd -n PDF2PassNew -k "android-34" --device "pixel_3" --force

if [ $? -eq 0 ]; then
    echo "✓ Емулятор створено успішно!"

    # Запускаємо новий емулятор з оптимальними налаштуваннями
    echo "4. Запускаємо новий емулятор (це може зайняти 2-3 хвилини)..."
    emulator -avd PDF2PassNew -no-audio -no-snapshot-save -gpu swiftshader_indirect -memory 2048 &

    # Чекаємо завантаження емулятора
    echo "5. Чекаємо завантаження емулятора..."
    sleep 20  # Початкова затримка

    for i in {1..30}; do
        echo "Перевіряємо готовність емулятора... ($i/30)"

        # Запускаємо ADB якщо не запущений
        adb start-server > /dev/null 2>&1

        # Перевіряємо чи емулятор онлайн та готовий
        if adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; then
            echo "✓ Емулятор повністю завантажився!"
            sleep 5  # Додаткова затримка для стабілізації
            break
        fi

        sleep 10
    done

    # Перевіряємо фінальний статус
    echo "6. Перевіряємо підключення..."
    adb devices

    if adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; then
        echo "✓ Емулятор готовий для встановлення додатків!"

        # Встановлюємо додаток
        echo "7. Встановлюємо додаток PDF2Pass..."
        adb install -r app/build/outputs/apk/debug/app-debug.apk

        if [ $? -eq 0 ]; then
            echo "✓ Додаток встановлено!"

            # Копіюємо PDF файл
            echo "8. Копіюємо ваш PDF файл..."
            adb push /home/akomarovskyi/Downloads/1035140_1505558266.pdf /sdcard/Download/

            # Запускаємо додаток
            echo "9. Запускаємо додаток..."
            adb shell am start -n com.example.helloworld/.MainActivity

            echo ""
            echo "🎉 УСПІШНО! Все готово для тестування!"
            echo ""
            echo "📱 СПОСОБИ ТЕСТУВАННЯ:"
            echo ""
            echo "1️⃣ ЧЕРЕЗ КНОПКУ В ДОДАТКУ:"
            echo "   • Натисніть 'Додати тікет'"
            echo "   • Виберіть файл '1035140_1505558266.pdf'"
            echo ""
            echo "2️⃣ ЧЕРЕЗ СИСТЕМНУ КНОПКУ SHARE (ОСНОВНИЙ СПОСІБ):"
            echo "   • Відкрийте файловий менеджер на емуляторі"
            echo "   • Знайдіть файл '1035140_1505558266.pdf' в папці Downloads"
            echo "   • Натисніть на файл та виберіть 'Share' (Поділитися)"
            echo "   • Виберіть 'PDF2Pass' зі списку додатків"
            echo "   • Додаток автоматично обробить PDF та створить тікет"
            echo ""
            echo "3️⃣ ПЕРЕГЛЯД РЕЗУЛЬТАТІВ:"
            echo "   • Натисніть 'Переглянути тікети' для перегляду збережених тікетів"
            echo ""
            echo "🔥 Новий функціонал реалізовано!"

        else
            echo "✗ Помилка встановлення додатку"
        fi
    else
        echo "✗ Емулятор не завантажився повністю"
    fi

else
    echo "✗ Помилка створення емулятора"
    echo "Перевірте чи встановлені system images для android-34"
fi
