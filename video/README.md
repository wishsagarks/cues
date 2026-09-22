# Cues explainer video

A 4K, narrated explainer built with [Remotion](https://www.remotion.dev) and a Sarvam AI voice.
The narration lives in `script.json`; the film's length is measured from the generated audio, so any
voice or pace fits.

## Make the video

1. Add your key to `../.env.local` (gitignored, never committed):

   ```
   SARVAM_API_KEY=your-key-here
   ```

2. Optional: listen to candidate voices, then pick one.

   ```bash
   npm run voices                  # writes out/voice-samples/*.wav
   ```

3. Generate the narration and render in 4K (3840x2160).

   ```bash
   npm run make                    # defaults to voice "ishita", pace 0.92
   # or, with a different voice:
   npm run tts -- --voice=priya --pace=0.9 && npm run render
   ```

Output: `out/cues-4k.mp4`. `npm run tts` prints the total runtime and warns if it falls outside 1:30 to 2:30;
adjust `--pace` or trim `script.json`.

`npm run studio` opens Remotion Studio to preview. `npm run tts:placeholder` writes silent clips so the layout
can be checked without a key; `npm run render` refuses to render placeholder audio.
