#!/bin/bash

# Тестовий скрипт для перевірки PDF файлу
echo "=== Тестування PDF файлу ==="
echo "Файл: /home/akomarovskyi/Downloads/1035140_1505558266.pdf"

# Перевіряємо чи існує файл
if [ -f "/home/akomarovskyi/Downloads/1035140_1505558266.pdf" ]; then
    echo "✓ PDF файл знайдено"

    # Отримуємо інформацію про файл
    echo "Розмір файлу: $(du -h /home/akomarovskyi/Downloads/1035140_1505558266.pdf | cut -f1)"

    # Спробуємо витягти текст з PDF (якщо є pdftotext)
    if command -v pdftotext >/dev/null 2>&1; then
        echo "Витягуємо текст з PDF..."
        pdftotext /home/akomarovskyi/Downloads/1035140_1505558266.pdf /tmp/pdf_content.txt
        echo "Перші 10 рядків тексту з PDF:"
        head -10 /tmp/pdf_content.txt
    else
        echo "pdftotext не встановлений, встановлюємо poppler-utils..."
        if command -v apt >/dev/null 2>&1; then
            sudo apt update && sudo apt install -y poppler-utils
        elif command -v pkg >/dev/null 2>&1; then
            sudo pkg install poppler-utils
        fi
    fi

    # Копіюємо файл до проекту для тестування
    cp /home/akomarovskyi/Downloads/1035140_1505558266.pdf /home/akomarovskyi/Project/pdf2pass_app/test_ticket.pdf
    echo "✓ Файл скопійовано до проекту як test_ticket.pdf"

else
    echo "✗ PDF файл не знайдено"
fi

echo "=== Тест завершено ==="
