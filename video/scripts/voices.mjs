// Optional: renders the same opening line in several voices so you can listen and choose.
//   npm run voices        -> out/voice-samples/<name>.wav
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {requireKey, synthesize} from './lib.mjs';

const CANDIDATES = ['ishita', 'priya', 'kavya', 'ritu', 'simran', 'shreya', 'neha', 'roopa'];
const LINE = 'Meet Cues. Context you declare. Behaviour that ends. Say it once, and your phone follows it, then stops.';

const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'out', 'voice-samples');
await mkdir(dir, {recursive: true});
const key = requireKey();

for (const speaker of CANDIDATES) {
  try {
    await writeFile(path.join(dir, `${speaker}.wav`), await synthesize({key, text: LINE, speaker, pace: 0.92}));
    console.log('wrote', speaker);
  } catch (e) {
    console.warn(`skipped ${speaker}: ${e.message}`);
  }
}
console.log(`\nSamples in ${dir}. Then: npm run tts -- --voice=<name>`);
