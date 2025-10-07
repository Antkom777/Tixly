#!/bin/bash

echo "Створюємо тестовий PDF файл..."

# Створюємо простий HTML файл
cat > /tmp/test_ticket.html << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>Test Ticket</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; }
        .header { text-align: center; font-size: 24px; font-weight: bold; }
        .info { margin: 10px 0; }
    </style>
</head>
<body>
    <div class="header">CONCERT TICKET</div>
    <div class="info"><strong>Event:</strong> Rock Concert 2024</div>
    <div class="info"><strong>Date:</strong> 2024-12-15 19:30</div>
    <div class="info"><strong>Location:</strong> Madison Square Garden</div>
    <div class="info"><strong>Ticket:</strong> TKT-789456</div>
    <div class="info"><strong>Seat:</strong> Section A, Row 5, Seat 12</div>
    <div class="info"><strong>Price:</strong> $85.00</div>
</body>
</html>
EOF

# Конвертуємо HTML в PDF (якщо є wkhtmltopdf)
if command -v wkhtmltopdf >/dev/null 2>&1; then
    wkhtmltopdf /tmp/test_ticket.html /home/akomarovskyi/Project/pdf2pass_app/test_concert_ticket.pdf
    echo "✓ Тестовий PDF створено: test_concert_ticket.pdf"
else
    echo "wkhtmltopdf не встановлений"
    echo "Можна встановити: sudo apt install wkhtmltopdf"
fi

echo "Тестування завершено"
