import {readFile} from 'node:fs/promises';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';

const run = promisify(execFile);

export const API = 'https://api.sarvam.ai/text-to-speech';

export const loadScript = async () =>
  JSON.parse(await readFile(new URL('../script.json', import.meta.url), 'utf8'));

export const requireKey = () => {
  const key = process.env.SARVAM_API_KEY?.trim();
  if (!key) {
    console.error(
      'SARVAM_API_KEY is not set.\n' +
        'Add this line to iqoo+/.env.local (gitignored, never committed):\n\n' +
        '  SARVAM_API_KEY=your-key-here\n\n' +
        'Get a key at https://dashboard.sarvam.ai, then re-run this command.'
    );
    process.exit(1);
  }
  return key;
};

// One TTS request. Returns a Buffer of WAV audio. Never logs the key.
export const synthesize = async ({key, text, speaker, pace, sampleRate = 44100}) => {
  for (let attempt = 1; attempt <= 4; attempt++) {
    const res = await fetch(API, {
      method: 'POST',
      headers: {'api-subscription-key': key, 'Content-Type': 'application/json'},
      body: JSON.stringify({
        text,
        language_code: 'en-IN',
        model: 'bulbul:v3',
        speaker,
        pace,
        speech_sample_rate: sampleRate,
        output_audio_codec: 'wav',
      }),
    });
    if (res.ok) {
      const json = await res.json();
      if (!json.audios?.[0]) throw new Error('Sarvam returned no audio: ' + JSON.stringify(json).slice(0, 300));
      return Buffer.from(json.audios[0], 'base64');
    }
    const body = (await res.text()).slice(0, 400);
    if ((res.status === 429 || res.status >= 500) && attempt < 4) {
      const wait = 1500 * attempt;
      console.warn(`  HTTP ${res.status}, retrying in ${wait}ms`);
      await new Promise((r) => setTimeout(r, wait));
      continue;
    }
    throw new Error(`Sarvam TTS failed: HTTP ${res.status} ${body}`);
  }
};

export const probeSeconds = async (file) => {
  const {stdout} = await run('ffprobe', [
    '-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', file,
  ]);
  return parseFloat(stdout.trim());
};

export const silence = async (file, seconds) =>
  run('ffmpeg', ['-y', '-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=mono', '-t', String(seconds), file]);
