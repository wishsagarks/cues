#!/usr/bin/env python3
"""
Sarvam AI — one working call per API.
Run from project root:  python3 sarvam/test_sarvam.py
"""

import os, sys, base64
from pathlib import Path

# ── Load key from .env.local ───────────────────────────────────────────
env_local = Path(__file__).resolve().parent.parent / ".env.local"
if env_local.exists():
    for line in env_local.read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())

API_KEY = os.environ.get("SARVAM_API_KEY", "")
if not API_KEY:
    print("✗ SARVAM_API_KEY not set.  Add it to .env.local or export it.")
    sys.exit(1)

from sarvamai import SarvamAI

client = SarvamAI(api_subscription_key=API_KEY)

passed, failed = [], []

def test(name, fn):
    try:
        result = fn()
        print(f"  ✓ {name}")
        passed.append(name)
        return result
    except Exception as e:
        # Extract just the useful part of the error
        err = str(e)
        if "invalid_api_key_error" in err:
            err = "403 — invalid API key (regenerate at dashboard.sarvam.ai)"
        elif len(err) > 120:
            err = err[:120] + "..."
        print(f"  ✗ {name}: {err}")
        failed.append(name)
        return None


print("\n═══ Sarvam AI Integration Test ═══\n")

# ── 1. Text Translation ───────────────────────────────────────────────
def translate():
    r = client.text.translate(
        input="When my earbuds connect after 6 PM, start a focus timer.",
        source_language_code="en-IN",
        target_language_code="hi-IN",
        speaker_gender="Male",
    )
    print(f"      → {r.translated_text}")
    return r

test("Text Translation (en→hi)", translate)


# ── 2. Transliteration ────────────────────────────────────────────────
def transliterate():
    r = client.text.transliterate(
        input="mera earbuds connect hone par focus timer shuru karo",
        source_language_code="hi-Latn",
        target_language_code="hi-IN",
    )
    print(f"      → {r.transliterated_text}")
    return r

test("Transliteration (hi-Latn→hi)", transliterate)


# ── 3. Language Detection ─────────────────────────────────────────────
def detect_lang():
    r = client.text.identify_language(
        input="जब मेरे ईयरबड्स कनेक्ट हों तो फोकस टाइमर शुरू करो"
    )
    print(f"      → {r.language_code}")
    return r

test("Language Detection", detect_lang)


# ── 4. Chat Completion (Sarvam-105B) ──────────────────────────────────
def chat():
    r = client.chat.completions(
        model="sarvam-105b",
        messages=[
            {
                "role": "system",
                "content": "You are a helpful assistant for an app called Cues that creates context-based routines on Android phones. Reply concisely."
            },
            {
                "role": "user",
                "content": "Explain in Hindi what a 'cue' is in the Cues app — one sentence."
            },
        ],
    )
    reply = r.choices[0].message.content
    print(f"      → {reply[:120]}...")
    return r

test("Chat Completion (Sarvam-105B)", chat)


# ── 5. Text-to-Speech (Bulbul) ────────────────────────────────────────
def tts():
    r = client.text_to_speech.convert(
        text="नमस्ते, कृपया अपनी रूटीन की समीक्षा करें।",
        language_code="hi-IN",
        speaker="anushka",
        model="bulbul:v2",
    )
    # r.audios is a list of base64-encoded audio chunks
    if r.audios:
        audio_bytes = base64.b64decode(r.audios[0])
        out = Path(__file__).resolve().parent / "test_output.wav"
        out.write_bytes(audio_bytes)
        print(f"      → {len(audio_bytes):,} bytes → {out.name}")
    return r

test("Text-to-Speech (Bulbul v2)", tts)


# ── 6. Speech-to-Text (Saaras) ────────────────────────────────────────
# Uses the TTS output we just created as input (if it exists).
tts_output = Path(__file__).resolve().parent / "test_output.wav"
if tts_output.exists():
    def stt():
        with open(tts_output, "rb") as f:
            r = client.speech_to_text.transcribe(
                file=f,
                model="saaras:v4",
                mode="transcribe",
            )
        print(f"      → \"{r.transcript}\"")
        return r

    test("Speech-to-Text (Saaras v4)", stt)
else:
    print("  ⊘ Speech-to-Text: skipped (no audio file from TTS step)")


# ── Summary ───────────────────────────────────────────────────────────
print(f"\n{'─'*40}")
print(f"  {len(passed)} passed, {len(failed)} failed")
if failed:
    print(f"  Failed: {', '.join(failed)}")
    if any("invalid API key" in f for f in failed):
        print(f"\n  → Regenerate your key at https://dashboard.sarvam.ai/")
print()
