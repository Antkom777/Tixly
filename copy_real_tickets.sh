#!/bin/bash

echo "=== Копіювання ТІЛЬКИ реальних квитків з папки tickets ==="

# Перезапускаємо ADB
adb kill-server
adb start-server

# Чекаємо підключення
adb wait-for-device

echo "✓ Емулятор підключено"

# Очищуємо попередні файли з емулятора (тільки з папки Download)
echo "Очищуємо попередні PDF файли з емулятора..."
adb shell "rm -f /sdcard/Download/*.pdf"
adb shell "rm -rf /sdcard/Download/Tickets"
adb shell "rm -rf /sdcard/Downloads/Tickets"

# Створюємо папку для реальних квитків
adb shell "mkdir -p /sdcard/Download/RealTickets"

# Шукаємо PDF файли ТІЛЬКИ в папці tickets
TICKETS_DIR="/home/akomarovskyi/tickets"

if [ ! -d "$TICKETS_DIR" ]; then
    echo "❌ Папка $TICKETS_DIR не знайдена!"
    exit 1
fi

echo "Шукаємо реальні квитки в: $TICKETS_DIR"
echo "Файли в папці tickets:"
ls -la "$TICKETS_DIR"

PDF_COUNT=0

# Копіюємо ТІЛЬКИ PDF файли з папки tickets
for pdf_file in "$TICKETS_DIR"/*.pdf; do
    if [ -f "$pdf_file" ]; then
        filename=$(basename "$pdf_file")
        echo "Копіюємо реальний квиток: $filename"

        # Копіюємо в папку Download та спеціальну папку RealTickets
        adb push "$pdf_file" "/sdcard/Download/"
        adb push "$pdf_file" "/sdcard/Download/RealTickets/"

        PDF_COUNT=$((PDF_COUNT + 1))
    fi
done

if [ $PDF_COUNT -eq 0 ]; then
    echo "❌ PDF файли не знайдено в папці tickets"
    echo "Перевіряємо альтернативні шляхи..."

    # Пробуємо альтернативні шляхи
    for alt_path in "/home/akomarovskyi/Downloads/tickets" "/home/akomarovskyi/Documents/tickets" "./tickets"; do
        if [ -d "$alt_path" ]; then
            echo "Знайдено альтернативну папку: $alt_path"
            for pdf_file in "$alt_path"/*.pdf; do
                if [ -f "$pdf_file" ]; then
                    filename=$(basename "$pdf_file")
                    echo "Копіюємо: $filename"
                    adb push "$pdf_file" "/sdcard/Download/"
                    adb push "$pdf_file" "/sdcard/Download/RealTickets/"
                    PDF_COUNT=$((PDF_COUNT + 1))
                fi
            done
            break
        fi
    done
fi

echo ""
echo "✓ Скопійовано $PDF_COUNT реальних квитків"

# Перевіряємо що скопійовано
echo "Реальні квитки на емуляторі:"
adb shell "ls -la /sdcard/Download/RealTickets/"

echo ""
echo "🎉 Тепер в додатку PDF2Pass тільки реальні квитки!"
echo "1. Натисніть 'Додати квиток з PDF'"
echo "2. Оберіть файли з папки Download або RealTickets"
echo "3. Протестуйте обробку справжніх квитків"
