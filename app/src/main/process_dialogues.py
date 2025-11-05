import csv
import json
import os
import sys
import hashlib
from collections import defaultdict
from google.cloud import texttospeech

# -------------------------------------------------------------------------
# --- ГЛАВНЫЕ НАСТРОЙКИ ---
# -------------------------------------------------------------------------

# Путь к папке 'assets' вашего Android-проекта.
# Убедитесь, что этот путь правильный для вашего компьютера.
ASSETS_DIR = "/Users/vladimirrapoport/Copy-projects/attempt4/attempt4/CardPuzzleApp/app/src/main/assets"

# -------------------------------------------------------------------------

def process_level_from_csv(csv_file_path, level_number):
    """
    Читает CSV с диалогами, где указан точный голос, генерирует аудио
    и создает вложенный JSON-файл для уровня.
    """
    if not os.path.exists(csv_file_path):
        print(f"Ошибка: CSV-файл не найден: {csv_file_path}")
        return

    audio_output_dir = os.path.join(ASSETS_DIR, "audio")
    os.makedirs(audio_output_dir, exist_ok=True)
    print(f"Путь к Android assets: {ASSETS_DIR}")
    print(f"Аудиофайлы будут сохранены в: {audio_output_dir}")

    try:
        client = texttospeech.TextToSpeechClient()
    except Exception as e:
        print(f"Ошибка: Не удалось инициализировать Google Cloud Text-to-Speech клиент.")
        print(f"Убедитесь, что вы настроили аутентификацию: {e}")
        return

    audio_config = texttospeech.AudioConfig(audio_encoding=texttospeech.AudioEncoding.MP3)

    try:
        with open(csv_file_path, mode='r', encoding='utf-8-sig') as f:
            csv_reader = csv.DictReader(f)
            rows = [row for row in csv_reader if any(field.strip() for field in row.values())]
    except Exception as e:
        print(f"Ошибка при чтении CSV-файла '{csv_file_path}': {e}")
        return

    processed_turns = []
    print(f"\nНайдено {len(rows)} строк в файле {csv_file_path}. Начинаю обработку...")

    for i, row in enumerate(rows):
        hebrew_text = row.get("hebrew", "").strip()
        group_id = row.get("groupId", "").strip()

        # --- ИЗМЕНЕНИЕ: Берем голос напрямую из CSV ---
        # Если колонка voice пустая, используем голос по умолчанию.
        voice_name = row.get("voice", "").strip()
        if not voice_name:
            print(f"  !! Внимание: для строки '{hebrew_text[:20]}...' не указан голос. Пропускаю.")
            continue

        if not hebrew_text or not group_id:
            print(f"  - Пропускаю строку {i+2}, т.к. поля 'hebrew' или 'groupId' пустые.")
            continue

        voice_params = texttospeech.VoiceSelectionParams(language_code="he-IL", name=voice_name)
        hash_object = hashlib.md5(hebrew_text.encode('utf-8'))
        hashed_filename = f"{hash_object.hexdigest()}.mp3"
        audio_output_path = os.path.join(audio_output_dir, hashed_filename)

        print(f"  -> Обработка groupId '{group_id}': '{hebrew_text[:30]}...' (голос: {voice_name})")

        if not os.path.exists(audio_output_path):
            try:
                print(f"    - Генерирую аудио '{hashed_filename}'...")
                synthesis_input = texttospeech.SynthesisInput(text=hebrew_text)
                response = client.synthesize_speech(input=synthesis_input, voice=voice_params, audio_config=audio_config)

                with open(audio_output_path, "wb") as out:
                    out.write(response.audio_content)
            except Exception as e:
                print(f"    !! Ошибка при генерации аудио: {e}")
                continue
        else:
            print(f"    - Аудиофайл уже существует (кэш), пропускаю генерацию.")

        processed_turns.append({
            "groupId": group_id,
            "hebrew": hebrew_text,
            "russian_translation": row.get("russian_translation", ""),
            "english_translation": row.get("english_translation", ""),
            "spanish_translation": row.get("spanish_translation", ""),
            "french_translation": row.get("french_translation", ""),
            "audioFilename": hashed_filename
        })

    print("\nГруппирую предложения в диалоги...")
    grouped_rounds = defaultdict(list)
    for turn in processed_turns:
        group_id = turn.pop("groupId")
        grouped_rounds[group_id].append(turn)

    final_json_data = []
    # Сортируем по groupId как числам, чтобы '2' шел после '1', а не '10'.
    for round_id in sorted(grouped_rounds.keys(), key=int):
        final_json_data.append({
            "roundId": round_id,
            "turns": grouped_rounds[round_id]
        })

    final_json_filename = f"level_{level_number}.json"
    final_json_output_path = os.path.join(ASSETS_DIR, final_json_filename)

    with open(final_json_output_path, 'w', encoding='utf-8') as f:
        json.dump(final_json_data, f, ensure_ascii=False, indent=2)

    print(f"\nГотово! Создано {len(final_json_data)} раундов.")
    print(f"Финальный JSON-файл сохранен: '{final_json_output_path}'")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Ошибка: неверное количество аргументов.")
        print("Пример использования: python process_dialogues.py <путь_к_csv> <номер_уровня>")
        print("Например: python process_dialogues.py level_1.csv 1")
    else:
        csv_path = sys.argv[1]
        level_num = sys.argv[2]
        process_level_from_csv(csv_path, level_num)