#!/bin/bash

echo "=== Пошук та копіювання ТІЛЬКИ реальних квитків ==="

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

# Створюємо папку для реальних квитків
adb shell "mkdir -p /sdcard/Download/RealTickets"

echo "Пошук папки tickets..."

# Можливі місця розташування папки tickets
POSSIBLE_PATHS=(
    "/home/akomarovskyi/tickets"
    "/home/akomarovskyi/Downloads/tickets"
    "/home/akomarovskyi/Documents/tickets"
    "/home/akomarovskyi/Desktop/tickets"
    "/home/akomarovskyi/Project/tickets"
)

TICKETS_DIR=""
PDF_COUNT=0

# Шукаємо папку tickets
for path in "${POSSIBLE_PATHS[@]}"; do
    if [ -d "$path" ]; then
        TICKETS_DIR="$path"
        echo "✓ Знайдено папку з квитками: $TICKETS_DIR"
        break
    fi
done

# Якщо не знайдено папку tickets, шукаємо файли з назвами квитків
if [ -z "$TICKETS_DIR" ]; then
    echo "Папка tickets не знайдена. Шукаємо PDF файли з назвами квитків..."

    # Шукаємо файли з цифровими назвами (типові для квитків)
    SEARCH_DIRS=("/home/akomarovskyi/Downloads" "/home/akomarovskyi/Documents" "/home/akomarovskyi/Desktop")

    for search_dir in "${SEARCH_DIRS[@]}"; do
        if [ -d "$search_dir" ]; then
            echo "Шукаємо квитки в: $search_dir"

            # Шукаємо PDF файли з цифровими назвами (ймовірно квитки)
            find "$search_dir" -maxdepth 2 -name "*.pdf" -type f 2>/dev/null | while read pdf_file; do
                filename=$(basename "$pdf_file")

                # Фільтруємо файли за патернами квитків
                if [[ "$filename" =~ ^[0-9]+_[0-9]+\.pdf$ ]] ||
                   [[ "$filename" =~ [Tt]icket ]] ||
                   [[ "$filename" =~ [Qq]uittung ]] ||
                   [[ "$filename" =~ [Bb]illet ]] ||
                   [[ "$filename" =~ [Кк]виток ]]; then

                    echo "Знайдено можливий квиток: $filename"
                    adb push "$pdf_file" "/sdcard/Download/"
                    adb push "$pdf_file" "/sdcard/Download/RealTickets/"
                    PDF_COUNT=$((PDF_COUNT + 1))
                fi
            done
        fi
    done
else
    # Копіюємо файли з знайденої папки tickets
    echo "Копіюємо файли з папки: $TICKETS_DIR"
    ls -la "$TICKETS_DIR"

    for pdf_file in "$TICKETS_DIR"/*.pdf; do
        if [ -f "$pdf_file" ]; then
            filename=$(basename "$pdf_file")
            echo "Копіюємо реальний квиток: $filename"
            adb push "$pdf_file" "/sdcard/Download/"
            adb push "$pdf_file" "/sdcard/Download/RealTickets/"
            PDF_COUNT=$((PDF_COUNT + 1))
        fi
    done
fi

echo ""
echo "✓ Скопійовано $PDF_COUNT реальних квитків"

# Показуємо що скопійовано
echo "Файли на емуляторі:"
adb shell "ls -la /sdcard/Download/ | grep -E '\\.pdf$'"

echo ""
echo "🎉 Тепер на емуляторі тільки реальні квитки!"
echo "Тестуйте додаток PDF2Pass з справжніми квитками!"
