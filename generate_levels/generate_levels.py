
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
CACHE_DIR = os.path.join(BASE_PROJECT_PATH, "source_files/_audio_cache")
TEMP_DIR = os.path.join(BASE_PROJECT_PATH, "source_files/_temp_audio")

# Пауза по умолчанию
DEFAULT_SAFETY_PAUSE_MS = 500

# --- 2. Голоса ---
VOICE_MAP = {
    "female_a": "he-IL-Wavenet-A",
    "male_b": "he-IL-Wavenet-B",
    "female_c": "he-IL-Wavenet-C",
    "male_d": "he-IL-Wavenet-D",
}

# --- 3. Google API ---


def call_google_tts(text_to_speak, voice_name, output_filename):
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

        print(f"      ☁️ API Request: '{text_to_speak}'")
        response = client.synthesize_speech(
            input=synthesis_input, voice=voice, audio_config=audio_config
        )

        with open(output_filename, "wb") as out:
            out.write(response.audio_content)
        return True

    except Exception as e:
        print(f"      !!! API ERROR: '{text_to_speak}'. {e}")
        return False


def get_audio_segment(text, voice_key):
    google_voice_name = VOICE_MAP.get(voice_key)
    if not google_voice_name:
        print(f"      !!! Voice not found: {voice_key}")
        return None

    unique_string = f"{text}_{google_voice_name}"
    file_hash = hashlib.md5(unique_string.encode('utf-8')).hexdigest()
    cached_filename = os.path.join(CACHE_DIR, f"{file_hash}.mp3")

    if os.path.exists(cached_filename):
        try:
            return AudioSegment.from_mp3(cached_filename)
        except Exception as e:
            print(f"      !!! Corrupted cache file: {cached_filename}. Deleting.")
            os.remove(cached_filename)

    if call_google_tts(text, google_voice_name, cached_filename):
        return AudioSegment.from_mp3(cached_filename)

    return None


# --- 4. Парсер ---
def parse_card_block(block_text):
    data = {
        "HEBREW": [], "HEBREW_PROMPT": [], "HEBREW_CORRECT": [],
        "HEBREW_DISTRACTORS": [], "RUSSIAN_CORRECT": [], "RUSSIAN": [],
        "VOICES": [], "PAIRS": []
    }
    current_key = None

    for line in block_text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"): continue

        is_tag = False
        if line.startswith("TASK:"):
            data['taskType'] = line.split(":", 1)[1].strip();
            current_key = None;
            is_tag = True
        elif line.startswith("HEBREW_PROMPT:"):
            current_key = "HEBREW_PROMPT";
            is_tag = True
        elif line.startswith("HEBREW_CORRECT:"):
            current_key = "HEBREW_CORRECT";
            is_tag = True
        elif line.startswith("RUSSIAN_CORRECT:"):
            current_key = "RUSSIAN_CORRECT";
            is_tag = True
        elif line.startswith("HEBREW_DISTRACTORS:"):
            current_key = "HEBREW_DISTRACTORS";
            is_tag = True
        elif line.startswith("HEBREW:"):
            current_key = "HEBREW";
            is_tag = True
        elif line.startswith("RUSSIAN:"):
            current_key = "RUSSIAN";
            is_tag = True
        elif line.startswith("VOICES:"):
            current_key = "VOICES";
            is_tag = True
        elif line.startswith("PAIRS:"):
            current_key = "PAIRS";
            is_tag = True

        if is_tag:
            content = line.split(":", 1)[1].strip()
            if content and current_key: data[current_key].append(content)
            continue
        if current_key: data[current_key].append(line)

    data['hebrew_display_text'] = "\n".join(data["HEBREW"])
    data['hebrew_prompt_text'] = "\n".join(data["HEBREW_PROMPT"])
    data['russian_translation_text'] = "\n".join(data["RUSSIAN"])

    voice_info_list = []
    for v_line in data["VOICES"]:
        parts = [p.strip() for p in v_line.split(',')]
        if not parts[0]: continue
        pause = int(parts[1]) if len(parts) > 1 else 0
        voice_info_list.append({"key": parts[0], "pause_ms": pause})
    data['voice_info_list'] = voice_info_list
    return data


# --- 5. Обработка уровня ---
def process_level_file(txt_filepath, assets_path):
    print(f"--- Processing: {os.path.basename(txt_filepath)} ---")
    base_name = os.path.basename(txt_filepath)
    level_id = base_name.replace("level_", "").replace(".txt", "")
    cards_list = []

    audio_output_dir = os.path.join(assets_path, "audio")
    if not os.path.exists(audio_output_dir): os.makedirs(audio_output_dir)
    if not os.path.exists(CACHE_DIR): os.makedirs(CACHE_DIR)

    with open(txt_filepath, 'r', encoding='utf-8') as f:
        full_content = f.read()
    entry_blocks = full_content.split('===')

    for i, block in enumerate(entry_blocks):
        clean_block = "\n".join([l for l in block.splitlines() if not l.strip().startswith("#")])
        if not clean_block.strip(): continue

        print(f"  Card {i}...", end=" ")
        data = parse_card_block(clean_block)
        task_type = data.get('taskType')
        if not task_type: continue

        card_json = {
            "taskType": task_type,
            "audioFilename": None,
            "segments": []
        }

        hebrew_regex = r'[\u0590-\u05FF\']+'
        audio_lines = []
        full_text_hash_source = ""
        voices = data['voice_info_list']

        try:
            if task_type in ['ASSEMBLE_TRANSLATION', 'AUDITION']:
                card_json['uiDisplayTitle'] = data['hebrew_display_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']
                card_json['taskTargetCards'] = re.findall(hebrew_regex, data['hebrew_display_text'])
                audio_lines = data['HEBREW']
                full_text_hash_source = data['hebrew_display_text']

            elif task_type == 'FILL_IN_BLANK':
                card_json['uiDisplayTitle'] = data['hebrew_prompt_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                card_json['correctOptions'] = data['HEBREW_CORRECT']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']
                audio_lines = data['HEBREW']
                full_text_hash_source = data['hebrew_display_text']

            elif task_type == 'QUIZ':
                card_json['uiDisplayTitle'] = data['hebrew_prompt_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                card_json['correctOptions'] = data['HEBREW_CORRECT']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']
                card_json['taskTargetCards'] = re.findall(hebrew_regex, " ".join(data['HEBREW_CORRECT']))

            elif task_type == 'MATCHING_PAIRS':
                card_json['uiDisplayTitle'] = data['russian_translation_text']
                card_json['taskPairs'] = [list(p) for p in zip(data['HEBREW_CORRECT'], data['RUSSIAN_CORRECT'])]

            elif task_type == 'CONJUGATION':
                card_json['uiDisplayTitle'] = data['hebrew_prompt_text']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']
                raw_pairs = []
                targets = []
                for line in data['PAIRS']:
                    parts = line.split(',')
                    if len(parts) >= 2:
                        q = parts[0].strip()
                        a = parts[1].strip()
                        raw_pairs.append([q, a])
                        ans_words = re.findall(hebrew_regex, a)
                        targets.extend(ans_words)
                card_json['taskPairs'] = raw_pairs
                card_json['taskTargetCards'] = targets

            # --- ТИП: MAKE_QUESTION ---
            elif task_type == 'MAKE_QUESTION':
                # Для Журнала и Аудио берем ПОЛНЫЙ текст (HEBREW block)
                card_json['uiDisplayTitle'] = data['hebrew_display_text']
                # Для Игры берем только промпт-ответ (HEBREW_PROMPT)
                card_json['gamePrompt'] = data['hebrew_prompt_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                card_json['correctOptions'] = data['HEBREW_CORRECT']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']
                full_question = " ".join(data['HEBREW_CORRECT'])
                card_json['taskTargetCards'] = re.findall(hebrew_regex, full_question)
                audio_lines = data['HEBREW']
                full_text_hash_source = data['hebrew_display_text']

            # --- НОВЫЙ ТИП: MAKE_ANSWER ---
            elif task_type == 'MAKE_ANSWER':
                # Полный текст для журнала
                card_json['uiDisplayTitle'] = data['hebrew_display_text']
                # Для игры берем Вопрос (Prompt)
                card_json['gamePrompt'] = data['hebrew_prompt_text']
                card_json['translationPrompt'] = data['russian_translation_text']
                # Собираем Ответ (Correct)
                card_json['correctOptions'] = data['HEBREW_CORRECT']
                card_json['distractorOptions'] = data['HEBREW_DISTRACTORS']

                full_answer = " ".join(data['HEBREW_CORRECT'])
                card_json['taskTargetCards'] = re.findall(hebrew_regex, full_answer)

                audio_lines = data['HEBREW']
                full_text_hash_source = data['hebrew_display_text']
            # --------------------------------

        except Exception as e:
            print(f"Error: {e}")
            continue

        # --- Аудио ---
        if full_text_hash_source and voices:
            base_hash = hashlib.md5(full_text_hash_source.strip().encode('utf-8')).hexdigest()
            final_filename = f"{base_hash}.mp3"
            final_path = os.path.join(audio_output_dir, final_filename)

            card_json['audioFilename'] = final_filename

            if len(audio_lines) != len(voices):
                print("Mismatch lines/voices")
                card_json['audioFilename'] = None
            else:
                combined = AudioSegment.empty()
                segments_meta = []
                curr_ms = 0
                success = True

                for line_idx, (line, v_info) in enumerate(zip(audio_lines, voices)):
                    segment_audio = get_audio_segment(line.strip(), v_info["key"])
                    if not segment_audio:
                        success = False;
                        break

                    duration = len(segment_audio)
                    segments_meta.append({
                        "text": line.strip(),
                        "start_ms": curr_ms,
                        "end_ms": curr_ms + duration
                    })

                    manual_pause = v_info["pause_ms"]
                    pause = manual_pause if manual_pause > 0 else DEFAULT_SAFETY_PAUSE_MS

                    combined += segment_audio
                    combined += AudioSegment.silent(duration=pause)
                    curr_ms += duration + pause

                if success:
                    combined.export(final_path, format="mp3")
                    card_json['segments'] = segments_meta
                    print(f"✅ Audio OK ({len(segments_meta)})")
                else:
                    card_json['audioFilename'] = None

        cards_list.append(card_json)
        if task_type == 'AUDITION':
            twin = card_json.copy()
            twin['taskType'] = 'ASSEMBLE_TRANSLATION'
            cards_list.append(twin)
        print("")

    final_data = {"levelId": level_id, "cards": cards_list}
    json_path = os.path.join(assets_path, f"level_{level_id}.json")
    if os.path.exists(json_path): os.remove(json_path)

    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(final_data, f, ensure_ascii=False, indent=2)
    print(f"💾 JSON Saved: {json_path}\n")


def main():
    if not os.path.exists(ASSETS_DIR): os.makedirs(ASSETS_DIR)

    audio_dest = os.path.join(ASSETS_DIR, "audio")
    if os.path.exists(audio_dest):
        for f in os.listdir(audio_dest): os.remove(os.path.join(audio_dest, f))

    for f in os.listdir(SOURCE_DIR):
        if f.startswith("level_") and f.endswith(".txt"):
            process_level_file(os.path.join(SOURCE_DIR, f), ASSETS_DIR)


if __name__ == "__main__":
    main()
