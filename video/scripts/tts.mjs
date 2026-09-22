// Generates one narration clip per scene with Sarvam Bulbul v3 and writes a
// manifest of measured durations that the Remotion composition sizes itself from.
//
//   npm run tts                       real voice (needs SARVAM_API_KEY in ../.env.local)
//   npm run tts:placeholder           silent clips, to check layout without a key
//   npm run tts -- --voice=priya --pace=0.9
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {loadScript, requireKey, synthesize, probeSeconds, silence} from './lib.mjs';

const arg = (name, fallback) => {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  return hit ? hit.split('=')[1] : fallback;
};

const placeholder = process.argv.includes('--placeholder');
const voice = arg('voice', process.env.SARVAM_VOICE || 'ishita');
const pace = parseFloat(arg('pace', process.env.SARVAM_PACE || '0.92'));

const outDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public', 'audio');
await mkdir(outDir, {recursive: true});

const {scenes} = await loadScript();
const key = placeholder ? null : requireKey();
const results = [];

for (const scene of scenes) {
  const file = `${scene.id}.wav`;
  const target = path.join(outDir, file);
  if (placeholder) {
    const words = scene.text.split(/\s+/).length;
    await silence(target, Math.max(3, words / 2.3));
  } else {
    if (scene.text.length > 2500) throw new Error(`Scene "${scene.id}" exceeds the 2,500 character limit`);
    process.stdout.write(`Synthesizing ${scene.id} (${scene.text.length} chars) with ${voice}... `);
    await writeFile(target, await synthesize({key, text: scene.text, speaker: voice, pace}));
    console.log('done');
  }
  results.push({id: scene.id, file, durationSec: await probeSeconds(target)});
}

await writeFile(
  path.join(outDir, 'manifest.json'),
  JSON.stringify({voice: placeholder ? 'placeholder' : voice, pace, scenes: results}, null, 2)
);

const LEAD = 0.5, TAIL = 0.6;
const total = results.reduce((t, s) => t + s.durationSec + LEAD + TAIL, 0);
const mm = `${Math.floor(total / 60)}:${String(Math.round(total % 60)).padStart(2, '0')}`;
console.log(`\nTotal runtime: ${mm} (${total.toFixed(1)}s)  voice=${placeholder ? 'placeholder' : voice}`);
if (total < 90 || total > 150) {
  console.warn(`WARNING: outside the 1:30 to 2:30 target. ${total > 150 ? 'Raise --pace or trim script.json.' : 'Lower --pace or extend script.json.'}`);
}
if (placeholder) console.log('Placeholder audio is silent. Run `npm run tts` for the real voice before the final render.');
