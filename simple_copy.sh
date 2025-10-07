#!/bin/bash

echo "=== Пошук та копіювання PDF тікетів ==="

# Перезапускаємо ADB
adb kill-server
adb start-server

# Чекаємо підключення
adb wait-for-device

echo "✓ Емулятор підключено"

# Створюємо папки на емуляторі
adb shell "mkdir -p /sdcard/Download/Tickets"
adb shell "mkdir -p /sdcard/Downloads/Tickets"

# Шукаємо PDF файли в різних локаціях
SEARCH_PATHS=(
    "/home/akomarovskyi/tickets"
    "/home/akomarovskyi/Downloads"
    "/home/akomarovskyi/Documents"
    "/home/akomarovskyi/Desktop"
    "/home/akomarovskyi/Project/pdf2pass_app"
)

PDF_COUNT=0

for search_path in "${SEARCH_PATHS[@]}"; do
    if [ -d "$search_path" ]; then
        echo "Шукаємо PDF в: $search_path"

        # Знаходимо всі PDF файли
        find "$search_path" -name "*.pdf" -type f 2>/dev/null | while read pdf_file; do
            if [ -f "$pdf_file" ]; then
                filename=$(basename "$pdf_file")
                echo "Копіюємо: $filename"

                # Копіюємо файл в обидві папки для зручності
                adb push "$pdf_file" "/sdcard/Download/"
                adb push "$pdf_file" "/sdcard/Download/Tickets/"

                PDF_COUNT=$((PDF_COUNT + 1))
            fi
        done
    fi
done

echo "Перевіряємо файли на емуляторі..."
adb shell "ls -la /sdcard/Download/ | grep pdf"

echo ""
echo "🎉 PDF файли скопійовано в емулятор!"
echo "Тепер в додатку PDF2Pass:"
echo "1. Натисніть 'Додати квиток з PDF'"
echo "2. Оберіть файли з папки Download"
echo "3. Протестуйте обробку PDF"
