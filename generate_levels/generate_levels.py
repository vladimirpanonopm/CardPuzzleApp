import os
import json
import hashlib
from google.cloud import texttospeech
from pydub import AudioSegment
import glob

# --- 1. Настройки (Без изменений) ---
BASE_PROJECT_PATH = "/Users/vladimirrapoport/Debugging"
SOURCE_DIR = os.path.join(BASE_PROJECT_PATH, "source_files/_source_files")
ASSETS_DIR = os.path.join(BASE_PROJECT_PATH, "app/src/main/assets")
TEMP_DIR = os.path.join(BASE_PROJECT_PATH, "source_files/_temp_audio")

# --- 2. Голоса (Без изменений) ---
VOICE_MAP = {
    "female_a": "he-IL-Wavenet-A",
    "male_b": "he-IL-Wavenet-B",
    "female_c": "he-IL-Wavenet-C",
    "male_d": "he-IL-Wavenet-D",
}


# --- 3. Google API (Без изменений) ---
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


# --- 4. НОВЫЙ Парсер ---
def parse_card_block(block_text):
    """
    Парсит один блок (одну карточку) из НОВОГО .txt файла (с тегами).
    """
    data = {
        "HEBREW": [],
        "HEBREW_PROMPT": [],
        "HEBREW_CORRECT": [],
        "HEBREW_DISTRACTORS": [],
        "RUSSIAN_CORRECT": [],
        "RUSSIAN": [],
        "VOICES": []
    }
    current_key = None

    for line in block_text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue

        # Проверяем, является ли строка тегом
        is_tag = False
        if line.startswith("TASK:"):
            data['taskType'] = line.split(":", 1)[1].strip()
            current_key = None
            is_tag = True
        elif line.startswith("HEBREW_PROMPT:"):
            current_key = "HEBREW_PROMPT"
            is_tag = True
        elif line.startswith("HEBREW_CORRECT:"):
            current_key = "HEBREW_CORRECT"
            is_tag = True
        elif line.startswith("RUSSIAN_CORRECT:"):
            current_key = "RUSSIAN_CORRECT"
            is_tag = True
        elif line.startswith("HEBREW_DISTRACTORS:"):
            current_key = "HEBREW_DISTRACTORS"
            is_tag = True
        elif line.startswith("HEBREW:"):
            current_key = "HEBREW"
            is_tag = True
        elif line.startswith("RUSSIAN:"):
            current_key = "RUSSIAN"
            is_tag = True
        elif line.startswith("VOICES:"):
            current_key = "VOICES"
            is_tag = True

        # Проверяем, есть ли контент на той же строке, что и тег
        # (Например: "RUSSIAN: Привет")
        if is_tag:
            line_content = line.split(":", 1)[1].strip()
            if line_content and current_key:
                data[current_key].append(line_content)
            continue

        # Если это не тег и не комментарий, добавляем к текущему ключу
        if current_key:
            data[current_key].append(line)

    # --- Пост-обработка (собираем текст и голоса) ---

    # 1. Собираем многострочные текстовые поля
    data['hebrew_display_text'] = "\n".join(data["HEBREW"])
    data['hebrew_prompt_text'] = "\n".join(data["HEBREW_PROMPT"])
    data['russian_translation_text'] = "\n".join(data["RUSSIAN"])

    # 2. Парсим информацию о голосах
    voice_info_list = []
    for v_line in data["VOICES"]:
        parts = [p.strip() for p in v_line.split(',')]
        if not parts[0]: continue  # Пропускаем пустые строки
        key = parts[0]
        pause = int(parts[1]) if len(parts) > 1 else 0
        voice_info_list.append({"key": key, "pause_ms": pause})
    data['voice_info_list'] = voice_info_list

    return data


# --- 5. ОБНОВЛЕННАЯ Основная функция ---
def process_level_file(txt_filepath, assets_path):
    """
    Читает .txt и генерирует ОДИН .json файл для уровня + склеенное аудио.
    """

    print(f"--- Обрабатываю: {txt_filepath} ---")

    base_name = os.path.basename(txt_filepath)
    level_id = base_name.replace("level_", "").replace(".txt", "")

    # Единый список карточек для этого уровня
    cards_list = []

    # --- Настройка папок (без изменений) ---
    audio_output_dir = os.path.join(assets_path, "audio")
    if not os.path.exists(audio_output_dir):
        os.makedirs(audio_output_dir)
        print(f"Создана папка: {audio_output_dir}")

    if not os.path.exists(TEMP_DIR):
        os.makedirs(TEMP_DIR)
    for f in glob.glob(os.path.join(TEMP_DIR, "*.mp3")):
        os.remove(f)  # Чистим временные файлы

    # --- Чтение и парсинг ---
    with open(txt_filepath, 'r', encoding='utf-8') as f:
        full_content = f.read()

    entry_blocks = full_content.split('===')

    for i, block in enumerate(entry_blocks):
        clean_block = "\n".join([line for line in block.splitlines() if not line.strip().startswith("#")])
        if not clean_block.strip():
            continue

        print(f"  Parsing card {i}...")
        data = parse_card_block(clean_block)
        task_type = data.get('taskType')

        if not task_type:
            print(f"    !!! ОШИБКА: 'TASK:' не найден в блоке {i}. Блок пропущен.")
            continue

        # --- Собираем JSON для карточки ---
        card_json = {
            "taskType": task_type,
            "audioFilename": None
        }

        # Переменные для аудио (если оно понадобится)
        audio_hebrew_lines = []
        audio_text_to_hash = ""
        voice_info_list = data['voice_info_list']

        try:
            # --- Логика для разных taskType ---
            if task_type == 'ASSEMBLE_TRANSLATION' or task_type == 'AUDITION':
                card_json['uiDisplayTitle'] = data['hebrew_display_text']
                card_json['translationPrompt'] = data['russian_translation_text']

                # --- ИЗМЕНЕНИЕ: Добавляем "неправильные" карточки ---
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']
                # --------------------------------------------------

                audio_hebrew_lines = data['HEBREW']
                audio_text_to_hash = data['hebrew_display_text']

            elif task_type == 'FILL_IN_BLANK':
                card_json['uiDisplayTitle'] = data['hebrew_prompt_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                card_json['correctOptions'] = data['HEBREW_CORRECT']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']

                # Для аудио используется ПОЛНАЯ фраза (тег HEBREW)
                audio_hebrew_lines = data['HEBREW']
                audio_text_to_hash = data['hebrew_display_text']

            elif task_type == 'MATCHING_PAIRS':
                card_json['uiDisplayTitle'] = data['russian_translation_text']
                list_A = data['HEBREW_CORRECT']
                list_B = data['RUSSIAN_CORRECT']

                if len(list_A) != len(list_B) or len(list_A) == 0:
                    print(
                        f"    !!! ОШИБКА: Карточка {i} (MATCHING_PAIRS)! Кол-во HEBREW_CORRECT ({len(list_A)}) не совпадает с HEBREW_DISTRACTORS ({len(list_B)}) или равно 0.")
                    continue

                card_json['taskPairs'] = [list(pair) for pair in zip(list_A, list_B)]
                # Аудио не генерируем, как и просили

            else:
                print(f"    !!! ОШИБКА: Неизвестный taskType '{task_type}' в карточке {i}.")
                continue

        except KeyError as e:
            print(f"    !!! ОШИБКА: Отсутствует обязательный тег (например, {e}) для {task_type} в карточке {i}.")
            continue

        # --- Генерация Аудио (если нужно) ---
        if audio_text_to_hash and voice_info_list:
            # 1. Генерируем имя файла
            text_to_hash = audio_text_to_hash.strip()
            hash_object = hashlib.md5(text_to_hash.encode('utf-8'))
            file_hash = hash_object.hexdigest()
            final_audio_filename = f"{file_hash}.mp3"

            card_json['audioFilename'] = final_audio_filename
            final_mp3_path = os.path.join(audio_output_dir, final_audio_filename)

            # 2. Проверяем, совпадает ли кол-во строк текста и голосов
            if len(audio_hebrew_lines) != len(voice_info_list):
                print(
                    f"    !!! ОШИБКА: Карточка {i}! Количество строк HEBREW ({len(audio_hebrew_lines)}) не совпадает с количеством VOICES ({len(voice_info_list)}).")
                # Не продолжаем, но карточку добавляем (будет без аудио)
                card_json['audioFilename'] = None

            # 3. Создаем MP3, если его нет
            elif not os.path.exists(final_mp3_path):
                print(f"  🎵 Создаю диалог: {final_audio_filename}")

                temp_files_info = []  # (путь, пауза_после)
                generation_success = True

                for line_idx, (line, voice_info) in enumerate(zip(audio_hebrew_lines, voice_info_list)):
                    voice_key = voice_info["key"]
                    pause_ms = voice_info["pause_ms"]

                    google_voice_name = VOICE_MAP.get(voice_key)
                    if not google_voice_name:
                        print(f"    !!! ОШИBКА: Голос '{voice_key}' не найден в VOICE_MAP.")
                        generation_success = False
                        break

                    temp_filename = os.path.join(TEMP_DIR, f"_temp_{line_idx}.mp3")
                    success = synthesize_speech(line.strip(), google_voice_name, temp_filename)

                    if not success:
                        generation_success = False
                        break

                    temp_files_info.append((temp_filename, pause_ms))

                # 4. Склеиваем
                if generation_success and temp_files_info:
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
                        card_json['audioFilename'] = None  # Ошибка -> нет аудио

                elif not generation_success:
                    card_json['audioFilename'] = None  # Ошибка -> нет аудио

                # 5. Чистим временные файлы
                for f, _ in temp_files_info:
                    if os.path.exists(f):
                        os.remove(f)

            else:
                print(f"  ⏩ MP3 уже существует, пропуск: {final_audio_filename}")

        # Добавляем готовую карточку в список
        cards_list.append(card_json)

    # --- Запись ЕДИНОГО JSON файла ---

    # 1. Создаем финальный объект уровня
    final_level_data = {
        "levelId": level_id,
        "cards": cards_list
    }

    # 2. Удаляем старый hebrew_level_...json, если он есть (чтобы не было путаницы)
    old_hebrew_file_path = os.path.join(assets_path, f"hebrew_level_{level_id}.json")
    if os.path.exists(old_hebrew_file_path):
        os.remove(old_hebrew_file_path)
        print(f"🧹 Удален старый файл: {old_hebrew_file_path}")

    # 3. Записываем новый единый файл
    level_file_path = os.path.join(assets_path, f"level_{level_id}.json")
    with open(level_file_path, 'w', encoding='utf-8') as f:
        json.dump(final_level_data, f, ensure_ascii=False, indent=2)
    print(f"✅ ЕДИНЫЙ JSON (Уровень) создан: {level_file_path}")


# --- Точка входа (Без изменений) ---
def main():
    if not os.path.exists(ASSETS_DIR):
        print(f"!!! ОШИБКА: Папка ASSETS_DIR не найдена по пути: {ASSETS_DIR}")
        return

    if not os.path.exists(SOURCE_DIR):
        print(f"!!! ОШИБКА: Папка SOURCE_DIR не найдена по пути: {SOURCE_DIR}")
        return

    print(f"Источник: {os.path.abspath(SOURCE_DIR)}")
    print(f"Назначение: {os.path.abspath(ASSETS_DIR)}")

    # Ищем все txt файлы уровней в папке-источнике
    found_files = False
    for filename in os.listdir(SOURCE_DIR):
        if filename.startswith("level_") and filename.endswith(".txt"):
            found_files = True
            filepath = os.path.join(SOURCE_DIR, filename)
            process_level_file(filepath, ASSETS_DIR)

    if not found_files:
        print(f"!!! ВНИМАНИЕ: Не найдено ни одного 'level_...txt' файла в {SOURCE_DIR}")

    print("\n--- Готово! ---")


if __name__ == "__main__":
    main()