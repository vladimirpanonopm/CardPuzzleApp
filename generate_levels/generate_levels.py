import os
import json
import hashlib
from google.cloud import texttospeech
from pydub import AudioSegment
import glob
import re

# --- 1. Настройки ---
BASE_PROJECT_PATH = "/Users/vladimirrapoport/Debugging"
SOURCE_DIR = os.path.join(BASE_PROJECT_PATH, "source_files/_source_files")
ASSETS_DIR = os.path.join(BASE_PROJECT_PATH, "app/src/main/assets")
TEMP_DIR = os.path.join(BASE_PROJECT_PATH, "source_files/_temp_audio")

# Пауза по умолчанию (если в файле не указано явно).
# 500 мс — хороший баланс между естественностью и техническим буфером.
DEFAULT_SAFETY_PAUSE_MS = 500

# --- 2. Голоса ---
VOICE_MAP = {
    "female_a": "he-IL-Wavenet-A",
    "male_b": "he-IL-Wavenet-B",
    "female_c": "he-IL-Wavenet-C",
    "male_d": "he-IL-Wavenet-D",
}


# --- 3. Google API ---
def synthesize_speech(text_to_speak, voice_name, output_filename):
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

        print(f"    🔊 Google API: '{text_to_speak}' ({voice_name})")
        response = client.synthesize_speech(
            input=synthesis_input, voice=voice, audio_config=audio_config
        )

        with open(output_filename, "wb") as out:
            out.write(response.audio_content)
        return True

    except Exception as e:
        print(f"    !!! API ERROR: '{text_to_speak}'. {e}")
        return False


# --- 4. Парсер ---
def parse_card_block(block_text):
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

        if is_tag:
            line_content = line.split(":", 1)[1].strip()
            if line_content and current_key:
                data[current_key].append(line_content)
            continue

        if current_key:
            data[current_key].append(line)

    data['hebrew_display_text'] = "\n".join(data["HEBREW"])
    data['hebrew_prompt_text'] = "\n".join(data["HEBREW_PROMPT"])
    data['russian_translation_text'] = "\n".join(data["RUSSIAN"])

    voice_info_list = []
    for v_line in data["VOICES"]:
        parts = [p.strip() for p in v_line.split(',')]
        if not parts[0]: continue
        key = parts[0]
        pause = int(parts[1]) if len(parts) > 1 else 0
        voice_info_list.append({"key": key, "pause_ms": pause})
    data['voice_info_list'] = voice_info_list

    return data


# --- 5. Обработка уровня ---
def process_level_file(txt_filepath, assets_path):
    print(f"--- Processing: {txt_filepath} ---")

    base_name = os.path.basename(txt_filepath)
    level_id = base_name.replace("level_", "").replace(".txt", "")
    cards_list = []

    audio_output_dir = os.path.join(assets_path, "audio")
    if not os.path.exists(audio_output_dir):
        os.makedirs(audio_output_dir)

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

        print(f"  Parsing card {i}...")
        data = parse_card_block(clean_block)
        task_type = data.get('taskType')

        if not task_type:
            continue

        card_json = {
            "taskType": task_type,
            "audioFilename": None,
            "segments": []
        }

        audio_hebrew_lines = []
        audio_text_to_hash = ""
        voice_info_list = data['voice_info_list']
        hebrew_word_regex = r'[\u0590-\u05FF\']+'

        try:
            if task_type == 'ASSEMBLE_TRANSLATION' or task_type == 'AUDITION':
                card_json['uiDisplayTitle'] = data['hebrew_display_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']

                text_to_parse = data['hebrew_display_text']
                card_json['taskTargetCards'] = re.findall(hebrew_word_regex, text_to_parse)

                audio_hebrew_lines = data['HEBREW']
                audio_text_to_hash = data['hebrew_display_text']

            elif task_type == 'FILL_IN_BLANK':
                card_json['uiDisplayTitle'] = data['hebrew_prompt_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                card_json['correctOptions'] = data['HEBREW_CORRECT']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']
                audio_hebrew_lines = data['HEBREW']
                audio_text_to_hash = data['hebrew_display_text']

            elif task_type == 'QUIZ':
                card_json['uiDisplayTitle'] = data['hebrew_prompt_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                card_json['correctOptions'] = data['HEBREW_CORRECT']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']
                full_correct_sentence = " ".join(data['HEBREW_CORRECT'])
                card_json['taskTargetCards'] = re.findall(hebrew_word_regex, full_correct_sentence)

            elif task_type == 'MATCHING_PAIRS':
                card_json['uiDisplayTitle'] = data['russian_translation_text']
                list_A = data['HEBREW_CORRECT']
                list_B = data['RUSSIAN_CORRECT']
                card_json['taskPairs'] = [list(pair) for pair in zip(list_A, list_B)]

            else:
                print(f"    !!! ERROR: Unknown taskType '{task_type}'")
                continue

        except KeyError as e:
            print(f"    !!! ERROR: Missing tag {e} in card {i}.")
            continue

        # --- Генерация Аудио с тайм-кодами ---
        if audio_text_to_hash and voice_info_list:
            text_to_hash = audio_text_to_hash.strip()
            hash_object = hashlib.md5(text_to_hash.encode('utf-8'))
            base_hash = hash_object.hexdigest()

            final_full_audio_filename = f"{base_hash}.mp3"
            final_full_mp3_path = os.path.join(audio_output_dir, final_full_audio_filename)
            card_json['audioFilename'] = final_full_audio_filename

            if len(audio_hebrew_lines) != len(voice_info_list):
                print(f"    !!! ERROR: Lines vs Voices mismatch.")
                card_json['audioFilename'] = None
            else:
                if not os.path.exists(final_full_mp3_path):
                    print(f"  🎵 Generating merged audio: {base_hash}")

                    combined_audio = AudioSegment.empty()
                    segments_metadata = []

                    current_position_ms = 0
                    generation_success = True

                    for line_idx, (line, voice_info) in enumerate(zip(audio_hebrew_lines, voice_info_list)):
                        voice_key = voice_info["key"]
                        manual_pause_ms = voice_info["pause_ms"]

                        # Применяем 500мс только если в файле не указано иное (>0)
                        pause_to_apply = manual_pause_ms if manual_pause_ms > 0 else DEFAULT_SAFETY_PAUSE_MS

                        google_voice = VOICE_MAP.get(voice_key)

                        temp_file = os.path.join(TEMP_DIR, f"temp_{line_idx}.mp3")
                        if not synthesize_speech(line.strip(), google_voice, temp_file):
                            generation_success = False
                            break

                        segment_audio = AudioSegment.from_mp3(temp_file)
                        duration_ms = len(segment_audio)

                        start_ms = current_position_ms
                        end_ms = start_ms + duration_ms

                        segments_metadata.append({
                            "text": line.strip(),
                            "start_ms": start_ms,
                            "end_ms": end_ms
                        })

                        combined_audio += segment_audio
                        combined_audio += AudioSegment.silent(duration=pause_to_apply)

                        current_position_ms += duration_ms + pause_to_apply

                    if generation_success:
                        combined_audio.export(final_full_mp3_path, format="mp3")
                        card_json['segments'] = segments_metadata
                        print(f"    ✅ Created with {len(segments_metadata)} segments.")
                    else:
                        card_json['audioFilename'] = None
                else:
                    print(f"  ⏩ Audio exists. (Ensure segments data is valid via fresh run).")
                    pass

        cards_list.append(card_json)

        if task_type == 'AUDITION':
            card_json_twin = card_json.copy()
            card_json_twin['taskType'] = 'ASSEMBLE_TRANSLATION'
            cards_list.append(card_json_twin)

    final_data = {"levelId": level_id, "cards": cards_list}

    old_hebrew = os.path.join(assets_path, f"hebrew_level_{level_id}.json")
    if os.path.exists(old_hebrew): os.remove(old_hebrew)

    level_file_path = os.path.join(assets_path, f"level_{level_id}.json")
    with open(level_file_path, 'w', encoding='utf-8') as f:
        json.dump(final_data, f, ensure_ascii=False, indent=2)
    print(f"✅ Saved JSON: {level_file_path}")


def main():
    if not os.path.exists(ASSETS_DIR) or not os.path.exists(SOURCE_DIR):
        print("Path Error")
        return

    print("⚠️  RECOMMENDATION: Delete 'app/src/main/assets/audio' folder before running.")

    for f in os.listdir(SOURCE_DIR):
        if f.startswith("level_") and f.endswith(".txt"):
            process_level_file(os.path.join(SOURCE_DIR, f), ASSETS_DIR)


if __name__ == "__main__":
    main()