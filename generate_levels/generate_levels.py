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


# --- 3. Google API (без изменений) ---
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


# --- 4. Парсер (ОБНОВЛЕН) ---
def parse_entry_block(block_text):
    """Парсит один блок (одну карточку) из .txt файла V9.0"""
    data = {}
    current_key = None
    lines_map = {
        "HEBREW": [],
        "RUSSIAN": [],
        "VOICES": []
    }

    for line in block_text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue

        if line.startswith("HEBREW:"):
            current_key = "HEBREW"
            line_content = line.split(":", 1)[1].strip()
            if line_content:
                lines_map[current_key].append(line_content)

        elif line.startswith("RUSSIAN:"):
            current_key = "RUSSIAN"
            line_content = line.split(":", 1)[1].strip()
            if line_content:
                lines_map[current_key].append(line_content)

        # --- ИЗМЕНЕНИЕ: Добавляем TASK ---
        elif line.startswith("TASK:"):
            data['taskType'] = line.split(":", 1)[1].strip()
            current_key = None

        # --- ИЗМЕНЕНИЕ: Удаляем IMAGE ---
        elif line.startswith("IMAGE:"):
            # data['imageName'] = line.split(":", 1)[1].strip() <-- УДАЛЕНО
            current_key = None

        elif line.startswith("AUDIO:"):
            current_key = None

        elif line.startswith("VOICES:"):
            current_key = "VOICES"
            line_content = line.split(":", 1)[1].strip()
            if line_content:
                lines_map[current_key].append(line_content)

        elif current_key:
            lines_map[current_key].append(line)

    data['hebrew_display'] = "\n".join(lines_map["HEBREW"])
    data['hebrew_lines'] = lines_map["HEBREW"]
    data['russian_translation'] = "\n".join(lines_map["RUSSIAN"])
    data['voice_keys'] = lines_map["VOICES"]

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

        # 1. Готовим hebrew_level_X.json
        hebrew_text_for_json = data.get('hebrew_display', '')
        hebrew_list.append(hebrew_text_for_json)

        # --- Генерация имени файла (без изменений) ---
        text_to_hash = hebrew_text_for_json.strip()
        hash_object = hashlib.md5(text_to_hash.encode('utf-8'))
        file_hash = hash_object.hexdigest()
        final_audio_filename = f"{file_hash}.mp3"
        # ---------------------------------------

        # --- ИЗМЕНЕНИЕ: 2. Готовим level_X.json ---
        entry = {
            "hebrew_index": hebrew_index_counter,
            "russian_translation": data.get('russian_translation', ''),
            "english_translation": None,
            "french_translation": None,
            "spanish_translation": None,
            "audioFilename": final_audio_filename,
            # "imageName": data.get('imageName', None), <-- УДАЛЕНО

            # --- ДОБАВЛЕНО ---
            # Устанавливаем тип задания. Если в .txt не указан TASK,
            # по умолчанию ставим 'ASSEMBLE_TRANSLATION'.
            "taskType": data.get('taskType', 'ASSEMBLE_TRANSLATION'),

            "voice": None  # (Это поле было в оригинале, оставляем как None)
        }
        level_entry_list.append(entry)

        # --- 6. Логика склейки MP3 (без изменений) ---
        final_mp3_path = os.path.join(audio_output_dir, final_audio_filename)

        if not os.path.exists(final_mp3_path):
            print(f"  🎵 Создаю диалог: {final_audio_filename}")

            hebrew_lines = data.get('hebrew_lines', [])
            voice_keys = data.get('voice_keys', [])

            if len(hebrew_lines) != len(voice_keys):
                print(
                    f"    !!! ОШИБКА: Карточка {i}! Количество строк HEBREW ({len(hebrew_lines)}) не совпадает с количеством VOICES ({len(voice_keys)}).")
                continue

            temp_files = []

            for line_idx, (line, voice_key) in enumerate(zip(hebrew_lines, voice_keys)):
                google_voice_name = VOICE_MAP.get(voice_key.strip())
                if not google_voice_name:
                    print(f"    !!! ОШИБКА: Голос '{voice_key}' не найден в VOICE_MAP.")
                    continue

                temp_filename = os.path.join(TEMP_DIR, f"_temp_{line_idx}.mp3")

                success = synthesize_speech(line.strip(), google_voice_name, temp_filename)

                if success:
                    temp_files.append(temp_filename)

            if temp_files:
                try:
                    combined_audio = AudioSegment.from_mp3(temp_files[0])

                    for temp_file in temp_files[1:]:
                        combined_audio += AudioSegment.from_mp3(temp_file)

                    combined_audio.export(final_mp3_path, format="mp3")
                    print(f"    ✅ Диалог СКЛЕЕН: {final_mp3_path}")

                except Exception as e:
                    print(f"    !!! ОШИБКА Pydub (склейки): {e}")
                    print(f"    !!! Убедитесь, что у вас установлен 'ffmpeg' (brew install ffmpeg)")

            for f in temp_files:
                os.remove(f)

        else:
            print(f"  ⏩ MP3 уже существует, пропуск: {final_audio_filename}")

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