#!/bin/bash

echo "=== Копіювання файлів з папки /home/akomarovskyi/tickets ==="

# Перевіряємо підключення емулятора
if ! adb devices | grep -q "device$"; then
    echo "❌ Емулятор не підключено!"
    exit 1
fi

echo "✓ Емулятор підключено"

# Очищуємо емулятор від всіх PDF файлів
echo "Очищуємо емулятор від попередніх PDF файлів..."
adb shell "rm -f /sdcard/Download/*.pdf" 2>/dev/null
adb shell "rm -rf /sdcard/Download/Tickets" 2>/dev/null
adb shell "rm -rf /sdcard/Download/RealTickets" 2>/dev/null

# Створюємо папку для квитків
adb shell "mkdir -p /sdcard/Download/Tickets"

TICKETS_DIR="/home/akomarovskyi/tickets"

# Перевіряємо чи існує папка
if [ ! -d "$TICKETS_DIR" ]; then
    echo "❌ Папка $TICKETS_DIR не існує!"
    exit 1
fi

echo "Папка знайдена: $TICKETS_DIR"
echo "Вміст папки:"
ls -la "$TICKETS_DIR"

PDF_COUNT=0

# Копіюємо всі файли з папки tickets
echo ""
echo "Копіюємо файли..."
for file in "$TICKETS_DIR"/*; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        echo "Копіюємо: $filename"
        adb push "$file" "/sdcard/Download/"
        adb push "$file" "/sdcard/Download/Tickets/"
        PDF_COUNT=$((PDF_COUNT + 1))
    fi
done

echo ""
echo "✓ Скопійовано $PDF_COUNT файлів"

# Показуємо що скопійовано
echo "Файли на емуляторі:"
adb shell "ls -la /sdcard/Download/Tickets/"

echo ""
echo "🎉 Готово! Всі файли з папки tickets скопійовано в емулятор!"
