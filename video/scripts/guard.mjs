// Runs automatically before `npm run render` (npm "prerender" hook).
import {readFile} from 'node:fs/promises';

try {
  const m = JSON.parse(await readFile(new URL('../public/audio/manifest.json', import.meta.url), 'utf8'));
  if (m.voice === 'placeholder') {
    console.error('Refusing to render: the audio is silent placeholder audio.\nRun `npm run tts` with SARVAM_API_KEY set in ../.env.local first.');
    process.exit(1);
  }
  console.log(`Rendering with voice "${m.voice}" at pace ${m.pace}.`);
} catch {
  console.error('No narration found. Run `npm run tts` first.');
  process.exit(1);
}
