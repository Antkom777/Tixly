#!/bin/bash

echo "=== Копіювання тестових PDF тікетів в емулятор ==="

# Перевіряємо підключення емулятора
echo "Перевіряємо підключення емулятора..."
adb devices

# Перевіряємо наявність папки tickets
TICKETS_DIR="/home/akomarovskyi/tickets"
if [ ! -d "$TICKETS_DIR" ]; then
    echo "❌ Папка $TICKETS_DIR не знайдена"
    echo "Перевіряємо альтернативні місця..."

    # Шукаємо папку tickets в різних місцях
    for dir in "/home/akomarovskyi/tickets" "/home/akomarovskyi/Downloads/tickets" "/home/akomarovskyi/Documents/tickets" "./tickets"; do
        if [ -d "$dir" ]; then
            TICKETS_DIR="$dir"
            echo "✓ Знайдено папку: $TICKETS_DIR"
            break
        fi
    done
fi

if [ ! -d "$TICKETS_DIR" ]; then
    echo "❌ Папка з тікетами не знайдена"
    exit 1
fi

echo "Використовуємо папку: $TICKETS_DIR"

# Показуємо файли в папці
echo "Файли в папці tickets:"
ls -la "$TICKETS_DIR"

# Створюємо папку на емуляторі для PDF файлів
echo "Створюємо папку на емуляторі..."
adb shell mkdir -p /sdcard/Download/PDFTickets

# Копіюємо всі PDF файли
echo "Копіюємо PDF файли в емулятор..."
PDF_COUNT=0

for file in "$TICKETS_DIR"/*.pdf; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        echo "Копіюємо: $filename"
        adb push "$file" "/sdcard/Download/PDFTickets/"

        # Також копіюємо в основну папку Download для легшого доступу
        adb push "$file" "/sdcard/Download/"

        PDF_COUNT=$((PDF_COUNT + 1))
    fi
done

# Копіюємо інші типи файлів (якщо є)
for file in "$TICKETS_DIR"/*.{png,jpg,jpeg,txt}; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        echo "Копіюємо додатковий файл: $filename"
        adb push "$file" "/sdcard/Download/PDFTickets/"
    fi
done

echo "✓ Скопійовано $PDF_COUNT PDF файлів"

# Перевіряємо що файли скопійовано
echo "Перевіряємо файли на емуляторі..."
adb shell ls -la /sdcard/Download/

echo ""
echo "🎉 Готово! Тепер ви можете:"
echo "1. Відкрити додаток PDF2Pass"
echo "2. Натиснути 'Додати квиток з PDF'"
echo "3. Обрати файл з папки Download"
echo "4. Протестувати обробку різних PDF файлів"
