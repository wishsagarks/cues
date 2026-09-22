import React from 'react';
import {AbsoluteFill, Audio, CalculateMetadataFunction, Composition, Sequence, Series, staticFile} from 'remotion';
import {LEAD, SCENES, TAIL} from './scenes';
import {C} from './theme';

export const FPS = 30;

type SceneEntry = {id: string; file: string; durationSec: number; frames: number};
export type CuesProps = {scenes: SceneEntry[]; voice: string};

const Cues: React.FC<CuesProps> = ({scenes}) => (
  <AbsoluteFill style={{background: C.dark}}>
    <Series>
      {scenes.map((s) => {
        const Visual = SCENES[s.id];
        return (
          <Series.Sequence key={s.id} durationInFrames={s.frames}>
            <Sequence from={LEAD}>
              <Audio src={staticFile(`audio/${s.file}`)} />
            </Sequence>
            <Visual frames={s.frames} />
          </Series.Sequence>
        );
      })}
    </Series>
  </AbsoluteFill>
);

// The film's length comes from the narration itself, so any voice or pace fits.
const calculateMetadata: CalculateMetadataFunction<CuesProps> = async () => {
  const res = await fetch(staticFile('audio/manifest.json'));
  if (!res.ok) {
    throw new Error('public/audio/manifest.json is missing. Run `npm run tts` (or `npm run tts:placeholder`) first.');
  }
  const manifest = (await res.json()) as {voice: string; scenes: Omit<SceneEntry, 'frames'>[]};
  const scenes = manifest.scenes.map((s) => ({
    ...s,
    frames: Math.round(s.durationSec * FPS) + LEAD + TAIL,
  }));
  return {
    durationInFrames: scenes.reduce((t, s) => t + s.frames, 0),
    props: {scenes, voice: manifest.voice},
  };
};

export const Root: React.FC = () => (
  <Composition
    id="Cues"
    component={Cues}
    durationInFrames={FPS * 120}
    fps={FPS}
    width={1920}
    height={1080}
    defaultProps={{scenes: [], voice: ''} as CuesProps}
    calculateMetadata={calculateMetadata}
  />
);
