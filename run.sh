#!/bin/bash

# --- 1. ПУТИ ---
VENV_ACTIVATE_PATH="/Users/vladimirrapoport/Copy-projects/attempt4/attempt4/CardPuzzleAppCopy/source_files/.venv/bin/activate"
SCRIPT_PATH="/Users/vladimirrapoport/Debugging/generate_levels/generate_levels.py"

# --- НОВЫЙ ПУТЬ ---
# Путь к папке, где лежит ваш Android-проект (где находится файл 'gradlew')
ANDROID_PROJECT_PATH="/Users/vladimirrapoport/Debugging"


# --- 2. Активируем Python venv ---
echo "▶️  Активирую Python venv..."
source "$VENV_ACTIVATE_PATH"

# --- 3. Запускаем Python скрипт ---
echo "▶️  Запускаю скрипт $SCRIPT_PATH..."
python3 "$SCRIPT_PATH"

# --- 4. Проверяем, что Python отработал ---
if [ $? -ne 0 ]; then
    echo "❌ ОШИБКА: Скрипт Python завершился с ошибкой. Сборка Android отменена."
    deactivate
    exit 1
fi

echo "✅  Скрипт Python успешно завершен."

# --- 5. НОВЫЙ ШАГ: Собираем и устанавливаем Android-проект ---
echo "▶️  Перехожу в папку Android-проекта: $ANDROID_PROJECT_PATH"
cd "$ANDROID_PROJECT_PATH"

echo "▶️  Запускаю Gradle-сборку и установку (./gradlew installDebug)..."
# (Это может занять 1-2 минуты)
./gradlew installDebug

# --- 6. Проверяем, что Android отработал ---
if [ $? -eq 0 ]; then
    echo "✅  Android-приложение успешно собрано и установлено!"
    # (Опционально) Открываем студию, если все равно хотим ее видеть
    open -a "Android Studio"
else
    echo "❌ ОШИБКА: Сборка Gradle завершилась с ошибкой."
fi

# --- 7. Деактивируем venv ---
deactivate