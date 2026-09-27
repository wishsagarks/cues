import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const sourceRoot = '/Users/wish/Documents/DailyCodes/iqoo+';
const hyperDeckRoot = '/tmp/hyperdeck.QT2rvw';
const privateRoot = '/tmp/cues-hyperdeck-project';
const outputRoot = path.join(sourceRoot, 'docs');

const { inspectProject } = await import(`${hyperDeckRoot}/src/project-analysis.mjs`);
const { composeDeck } = await import(`${hyperDeckRoot}/src/storyboard.mjs`);
const { scaffoldDeck } = await import(`${hyperDeckRoot}/src/scaffold.mjs`);
const { buildPptx, buildPdf } = await import(`${hyperDeckRoot}/src/export.mjs`);
const artifactTool = await import(pathToFileURL('/Users/wish/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs').href);

const inspected = await inspectProject(sourceRoot);
const analysis = {
  ...inspected,
  projectName: 'Cues',
  theme: {
    ...inspected.theme,
    // Kinetic Obsidian tokens from docs/design/stitch/DESIGN_SYSTEM.md.
    primary: '#FFE600',
    secondary: '#FF5E00',
    tokens: [
      { name: 'surface-void', value: '#0B0C10' },
      { name: 'surface', value: '#11131B' },
      { name: 'surface-container', value: '#1D1F28' },
      { name: 'surface-container-high', value: '#282A32' },
      { name: 'on-surface', value: '#E1E1ED' },
      { name: 'on-surface-variant', value: '#CDC7AA' },
      { name: 'electric-yellow', value: '#FFE600' },
      { name: 'monster-orange', value: '#FF5E00' },
      { name: 'origin-ice-teal', value: '#00E5FF' },
      { name: 'verified-green', value: '#00FF88' },
      { name: 'telemetry-slate', value: '#7C8BA1' },
      { name: 'headline-font', value: 'Space Grotesk' },
      { name: 'body-font', value: 'Plus Jakarta Sans' },
      { name: 'mono-font', value: 'JetBrains Mono' },
    ],
  },
  sourceFiles: [
    'app/src/main/kotlin/com/cues/app/ui/ask/AskScreen.kt',
    'app/src/main/kotlin/com/cues/app/drafting/OnDeviceLlmDrafter.kt',
    'app/src/main/kotlin/com/cues/app/drafting/SarvamChatDrafter.kt',
    'app/src/main/kotlin/com/cues/app/net/SarvamClient.kt',
    'core/src/main/kotlin/com/cues/core/drafting/GrammarParser.kt',
    'app/src/main/kotlin/com/cues/app/runtime/PowerReceiver.kt',
    'app/src/main/kotlin/com/cues/app/runtime/TimeTriggerReceiver.kt',
    'app/src/main/kotlin/com/cues/app/ui/insights/InsightsScreen.kt',
  ],
};

const story = {
  category: 'pitch',
  narrative: 'Cues: the trustworthy AI shortcut layer for Android',
  audience: 'Someone who wants a shortcut they can inspect afterward — not a power user willing to hand-wire automation, not someone content to trust a black box. On this build: a person coordinating daily life across languages and places, who needs "when I get there, do this" to actually be provable, not just plausible.',
  duration: 8,
  outcome: 'Ship a dependable intent-to-action loop, then expand the trigger surface',
  features: [],
  sources: 'Cues Android codebase, on-device Gemma runtime, Sarvam translation, parser, receipts, and Insights',
  constraints: 'AI can propose. Only parser-approved routines run. Users review and arm before execution. Receipts make every outcome inspectable.',
  tempo: 'measured',
  background: 'Cues began with a practical gap: phone automation is powerful, but authoring it takes setup and the result is hard to trust after it runs.',
  problem: 'People can ask an assistant for an answer, yet reliable automation still requires device-specific setup, permissions, and guesswork. When a routine fires, the user often cannot tell what happened or why.',
  solution: 'Cues turns spoken or typed intent into a reviewable shortcut. Sarvam translates multilingual input, Gemma normalizes the request on-device, the grammar parser validates it, and the user approves before the runtime executes it. Every capability a cue requires is derived from what it actually does, never asserted by the drafter that proposed it — and once armed, nothing downstream of that approval ever consults a model again.',
  traction: '',
  competitors: 'System automation tools serve power users. General assistants serve conversation. Cues focuses on context-aware shortcuts that remain inspectable after they run, with a deterministic safety boundary between suggestion and action.',
  fundingAsk: '',
  productEmbed: { mode: 'none', src: '' },
  aiCapabilities: [],
  aiProvider: 'none',
};

const manifest = composeDeck(analysis, story, null);
manifest.composition.slideCount = 8;

const slideCopy = [
  {
    title: 'A shortcut is a promise we can prove',
    purpose: 'Cues makes the path from a human request to a phone action visible. The user sees what the system understood, what the parser accepted, and what the runtime actually did.',
  },
  {
    title: 'Voice is the shortest path to intent',
    purpose: 'People already describe a desired outcome in natural language. Cues accepts speech or text in the user\'s language, then creates one canonical English representation for the parser without making language a new setup task.',
  },
  {
    title: 'Automation is powerful, but not trustworthy by default',
    purpose: 'Opaque automation creates three failure modes: the request is misunderstood, the device context is wrong, or the user cannot reconstruct what happened. Cues treats each failure as a state to surface, not a reason to hide the result. A signal that cannot be read stays unknown through evaluation and into the receipt — unknown never quietly reads as "no", and it never grants permission to act either.',
  },
  {
    title: 'Cues separates language from authority',
    purpose: 'Sarvam handles translation only. Gemma handles intent normalization and repair. The deterministic grammar parser remains the authority for executable syntax. A user must still review, approve, and arm the routine — explained before it acts, granted the least access it needs, and never running further than the user put it.',
  },
  {
    title: 'From spoken intent to an armed routine',
    purpose: 'The loop is deliberately staged: capture intent, translate to English, normalize with Gemma, parse into a cue, resolve device and time context, present a review, then arm and execute. Receipts record the outcome for Insights. Worked example: "When I arrive at Office, text Mum I\'m home" becomes a place-arrival trigger and a drafted message — reviewed and handed to the user\'s own messaging app, never sent on the app\'s own authority.',
    files: analysis.sourceFiles,
  },
  {
    title: 'Why Cues wins the trust layer',
    purpose: 'Local-first: inference and decisions run on-device by default. Explain-before-acting: every routine renders as WHEN / IF / DO / UNTIL / RESTORE before it can arm. Least privilege: each permission request is derived from what the routine does, asked for once it creates visible value. Human control: nothing executes until the user approves and arms it. Provenance: every drafted routine names the drafter that produced it. Fail safely: a blocked action is a PARTIAL session, never a quiet success, and cleanup only ever releases what Cues itself owns. That combination turns a clever suggestion into a shortcut a user can safely keep.',
  },
  {
    title: 'Ship the loop, then expand the surface',
    purpose: 'The next milestone is a reliable daily loop across time, charger, and device context. After that foundation is stable, Cues can add richer sensors and more capable actions without weakening the safety boundary.',
  },
];

manifest.slides = manifest.slides.map((slide, index) => ({
  ...slide,
  ...(slideCopy[index] || {}),
}));

await fs.rm(privateRoot, { recursive: true, force: true });
await fs.mkdir(outputRoot, { recursive: true });
await scaffoldDeck(privateRoot, manifest, { aiProvider: null });
await fs.writeFile(path.join(privateRoot, '.deck', 'deck.json'), `${JSON.stringify(manifest, null, 2)}\n`);

const pptxPath = path.join(outputRoot, 'Cues_HyperDeck_8_Page.pptx');
const pdfPath = path.join(outputRoot, 'Cues_HyperDeck_8_Page.pdf');
const rawPptxPath = path.join(privateRoot, 'deck-raw.pptx');
await fs.writeFile(rawPptxPath, await buildPptx(manifest));
const importedPresentation = await artifactTool.PresentationFile.importPptx(await artifactTool.FileBlob.load(rawPptxPath));
await fs.rm(pptxPath, { force: true });
await (await artifactTool.PresentationFile.exportPptx(importedPresentation)).save(pptxPath);
await fs.rm(`${pptxPath}.inspect.ndjson`, { force: true });
await fs.writeFile(pdfPath, await buildPdf(manifest));

console.log(JSON.stringify({
  pptxPath,
  pdfPath,
  manifestSlides: manifest.slides.length,
  exportedPages: manifest.slides.length + 1,
  theme: manifest.analysis.theme,
}, null, 2));
