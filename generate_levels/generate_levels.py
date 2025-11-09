import os
import json
import hashlib
from google.cloud import texttospeech
from pydub import AudioSegment
import glob

# --- 1. Настройки ---
BASE_PROJECT_PATH = "/Users/vladimirrapoport/Copy-projects/attempt4/attempt4/CardPuzzleAppCopy"
SOURCE_DIR = os.path.join(BASE_PROJECT_PATH, "source_files/_source_files")
ASSETS_DIR = os.path.join(BASE_PROJECT_PATH, "app/src/main/assets")
TEMP_DIR = os.path.join(BASE_PROJECT_PATH, "source_files/_temp_audio")

# --- 2. Голоса ---
VOICE_MAP = {
    "female_a": "he-IL-Wavenet-A",
    "male_b": "he-IL-Wavenet-B",
    "female_c": "he-IL-Wavenet-C",
    "male_d": "he-IL-Wavenet-D",
}


# --- 3. Google API ---
def synthesize_speech(text_to_speak, voice_name, output_filename):
    """Вызывает Google TTS API и сохраняет .mp3 файл."""
    try:
        client = texttospeech.TextToSpeechClient()
        synthesis_input = texttospeech.SynthesisInput(text=text_to_speak)
        voice = texttospeech.VoiceSelectionParams(
            language_code="he-IL",
            name=voice_name
        )
        audio_config = texttospeech.AudioConfig(
            audio_encoding=texttospeech.AudioEncoding.MP3
        )

        print(f"    🔊 Запрос к Google API для: '{text_to_speak}' (Голос: {voice_name})")
        response = client.synthesize_speech(
            input=synthesis_input, voice=voice, audio_config=audio_config
        )

        with open(output_filename, "wb") as out:
            out.write(response.audio_content)
        return True  # Успех

    except Exception as e:
        print(f"    !!! ОШИБКА API: Не удалось сгенерировать '{text_to_speak}'. {e}")
        return False  # Провал


# --- 4. Парсер (ИСПРАВЛЕНО) ---
def parse_entry_block(block_text):
    """Парсит один блок (одну карточку) из .txt файла."""
    data = {}
    current_key = None
    lines_map = {
        "HEBREW": [],
        "HEBREW_PROMPT": [],
        "HEBREW_CORRECT": [],
        "HEBREW_DISTRACTORS": [],
        "RUSSIAN": [],
        "VOICES": []
    }

    for line in block_text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue

        if line.startswith("TASK:"):
            data['taskType'] = line.split(":", 1)[1].strip()
            current_key = None
            continue

        # --- ИСПРАВЛЕННЫЙ БЛОК ПАРСИНГА СТРОК ---
        if line.startswith("HEBREW_PROMPT:"):
            current_key = "HEBREW_PROMPT"
            line = line.split(":", 1)[1].strip()

        elif line.startswith("HEBREW_CORRECT:"):
            current_key = "HEBREW_CORRECT"
            line = line.split(":", 1)[1].strip()

        elif line.startswith("HEBREW_DISTRACTORS:"):
            current_key = "HEBREW_DISTRACTORS"
            line = line.split(":", 1)[1].strip()

        elif line.startswith("HEBREW:"):
            current_key = "HEBREW"
            line = line.split(":", 1)[1].strip()

        elif line.startswith("RUSSIAN:"):
            current_key = "RUSSIAN"
            line = line.split(":", 1)[1].strip()

        elif line.startswith("VOICES:"):
            current_key = "VOICES"
            line = line.split(":", 1)[1].strip()

        elif line.startswith("IMAGE:") or line.startswith("AUDIO:"):
            current_key = None
            continue

        # Если есть текущий ключ и строка не пуста, добавляем ее
        if current_key and line:
            lines_map[current_key].append(line)
        # ------------------------------------------

    data['hebrew_display'] = "\n".join(lines_map["HEBREW"])
    data['hebrew_lines'] = lines_map["HEBREW"]
    data['hebrew_prompt'] = "\n".join(lines_map["HEBREW_PROMPT"])
    data['task_correct_cards'] = lines_map["HEBREW_CORRECT"]
    data['task_distractor_cards'] = lines_map["HEBREW_DISTRACTORS"]
    data['russian_translation'] = "\n".join(lines_map["RUSSIAN"])

    voice_info_list = []
    for v_line in lines_map["VOICES"]:
        parts = [p.strip() for p in v_line.split(',')]
        key = parts[0]
        pause = int(parts[1]) if len(parts) > 1 else 0
        voice_info_list.append({"key": key, "pause_ms": pause})

    data['voice_info_list'] = voice_info_list

    if 'taskType' not in data:
        data['taskType'] = 'ASSEMBLE_TRANSLATION'

    return data


# --- 5. Основная функция (ОБНОВЛЕНА) ---
def process_level_file(txt_filepath, assets_path):
    """Читает .txt и генерирует два .json файла + склеенное аудио"""

    print(f"--- Обрабатываю: {txt_filepath} ---")

    base_name = os.path.basename(txt_filepath)
    level_id = base_name.replace("level_", "").replace(".txt", "")

    hebrew_list = []
    level_entry_list = []
    hebrew_index_counter = 0

    audio_output_dir = os.path.join(assets_path, "audio")
    if not os.path.exists(audio_output_dir):
        os.makedirs(audio_output_dir)
        print(f"Создана папка: {audio_output_dir}")

    if not os.path.exists(TEMP_DIR):
        os.makedirs(TEMP_DIR)
    for f in glob.glob(os.path.join(TEMP_DIR, "*.mp3")):
        os.remove(f)

    with open(txt_filepath, 'r', encoding='utf-8') as f:
        full_content = f.read()

    entry_blocks = full_content.split('===')

    for i, block in enumerate(entry_blocks):
        clean_block = "\n".join([line for line in block.splitlines() if not line.strip().startswith("#")])
        if not clean_block.strip():
            continue

        data = parse_entry_block(clean_block)
        task_type = data.get('taskType')

        # --- ИЗМЕНЕНИЕ 1: Общая логика для entry ---
        entry = {
            "hebrew_index": hebrew_index_counter,
            "russian_translation": data.get('russian_translation', ''),
            "english_translation": None,
            "french_translation": None,
            "spanish_translation": None,
            "audioFilename": None,  # (По умолчанию None)
            "taskType": task_type,
            "voice": None
        }

        # --- ИЗМЕНЕНИЕ 2: Разная логика для разных taskType ---

        if task_type == 'FILL_IN_BLANK' or task_type == 'ASSEMBLE_TRANSLATION':
            hebrew_full_text = data.get('hebrew_display', '')
            if not hebrew_full_text:
                print(f"    !!! ОШИБКА: Карточка {i}! Тег HEBREW: (для аудио) не найден.")
                continue

            text_to_hash = hebrew_full_text.strip()
            hash_object = hashlib.md5(text_to_hash.encode('utf-8'))
            file_hash = hash_object.hexdigest()
            final_audio_filename = f"{file_hash}.mp3"

            entry['audioFilename'] = final_audio_filename

            if task_type == 'FILL_IN_BLANK':
                hebrew_list.append(data.get('hebrew_prompt', ''))
                entry['task_correct_cards'] = data.get('task_correct_cards', [])
                entry['task_distractor_cards'] = data.get('task_distractor_cards', [])

            elif task_type == 'ASSEMBLE_TRANSLATION':
                hebrew_list.append(hebrew_full_text)

        elif task_type == 'MATCHING_PAIRS':
            # Для "Найди Пару" hebrew_index не нужен,
            # но мы должны что-то вставить, чтобы списки hebrew_list
            # и level_entry_list были одинаковой длины.
            # Мы вставим LTR-подсказку (напр. "Найди одинаковые пары.")
            hebrew_list.append(data.get('russian_translation', ''))

            list_A = data.get('task_correct_cards', [])
            list_B = data.get('task_distractor_cards', [])

            if len(list_A) != len(list_B) or not list_A:
                print(
                    f"    !!! ОШИБКА: Карточка {i} (MATCHING_PAIRS)! Кол-во HEBREW_CORRECT ({len(list_A)}) не совпадает с HEBREW_DISTRACTORS ({len(list_B)}) ИЛИ СПИСОК ПУСТ.")
                continue

            # Собираем пары
            entry['task_pairs'] = [list(pair) for pair in zip(list_A, list_B)]

            # Аудио для этого типа пока не генерируем

        else:
            print(f"    !!! ОШИБКА: Неизвестный taskType '{task_type}' в карточке {i}.")
            continue

        level_entry_list.append(entry)

        # --- ИЗМЕНЕНИЕ 3: Аудио генерируется только если оно нужно ---
        if entry['audioFilename']:
            final_mp3_path = os.path.join(audio_output_dir, entry['audioFilename'])

            if not os.path.exists(final_mp3_path):
                print(f"  🎵 Создаю диалог: {entry['audioFilename']}")

                hebrew_lines = data.get('hebrew_lines', [])
                voice_info_list = data.get('voice_info_list', [])

                if len(hebrew_lines) != len(voice_info_list):
                    print(
                        f"    !!! ОШИБКА: Карточка {i}! Количество строк HEBREW ({len(hebrew_lines)}) не совпадает с количеством VOICES ({len(voice_info_list)}).")
                    continue

                temp_files_info = []

                for line_idx, (line, voice_info) in enumerate(zip(hebrew_lines, voice_info_list)):

                    voice_key = voice_info["key"]
                    pause_ms = voice_info["pause_ms"]

                    google_voice_name = VOICE_MAP.get(voice_key)
                    if not google_voice_name:
                        print(f"    !!! ОШИБКА: Голос '{voice_key}' не найден в VOICE_MAP.")
                        continue

                    temp_filename = os.path.join(TEMP_DIR, f"_temp_{line_idx}.mp3")
                    success = synthesize_speech(line.strip(), google_voice_name, temp_filename)

                    if success:
                        temp_files_info.append((temp_filename, pause_ms))

                if temp_files_info:
                    try:
                        combined_audio = AudioSegment.empty()
                        for temp_filename, pause_ms in temp_files_info:
                            combined_audio += AudioSegment.from_mp3(temp_filename)
                            if pause_ms > 0:
                                combined_audio += AudioSegment.silent(duration=pause_ms)
                        combined_audio.export(final_mp3_path, format="mp3")
                        print(f"    ✅ Диалог СКЛЕЕН (с паузами): {final_mp3_path}")
                    except Exception as e:
                        print(f"    !!! ОШИБКА Pydub (склейки): {e}")
                        print(f"    !!! Убедитесь, что у вас установлен 'ffmpeg' (brew install ffmpeg)")

                for f, _ in temp_files_info:
                    os.remove(f)

            else:
                print(f"  ⏩ MP3 уже существует, пропуск: {entry['audioFilename']}")

        hebrew_index_counter += 1

    # --- Запись JSON файлов (без изменений) ---
    hebrew_file_path = os.path.join(assets_path, f"hebrew_level_{level_id}.json")
    with open(hebrew_file_path, 'w', encoding='utf-8') as f:
        json.dump(hebrew_list, f, ensure_ascii=False, indent=2)
    print(f"✅ JSON (Иврит) создан: {hebrew_file_path}")

    level_file_path = os.path.join(assets_path, f"level_{level_id}.json")
    with open(level_file_path, 'w', encoding='utf-8') as f:
        json.dump(level_entry_list, f, ensure_ascii=False, indent=2)
    print(f"✅ JSON (Уровень) создан: {level_file_path}")


# --- Точка входа (без изменений) ---
def main():
    if not os.path.exists(ASSETS_DIR):
        print(f"!!! ОШИБКА: Папка ASSETS_DIR не найдена по пути: {ASSETS_DIR}")
        return

    if not os.path.exists(SOURCE_DIR):
        print(f"!!! ОШИБКА: Папка SOURCE_DIR не найдена по пути: {SOURCE_DIR}")
        return

    print(f"Источник: {os.path.abspath(SOURCE_DIR)}")
    print(f"Назначение: {os.path.abspath(ASSETS_DIR)}")

    for filename in os.listdir(SOURCE_DIR):
        if filename.startswith("level_") and filename.endswith(".txt"):
            filepath = os.path.join(SOURCE_DIR, filename)
            process_level_file(filepath, ASSETS_DIR)

    print("\n--- Готово! ---")


if __name__ == "__main__":
    main()