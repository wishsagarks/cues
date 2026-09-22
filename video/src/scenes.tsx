import React from 'react';
import {AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, display, body} from './theme';

// Frames of quiet before the narration starts, and after it ends.
export const LEAD = 15;
export const TAIL = 18;

export type SceneProps = {frames: number};

// Every scene paces itself against its own narration: at(0.5) is the frame
// where half of the spoken line has played, whatever the voice's actual speed.
const useClock = (frames: number) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const voice = Math.max(1, frames - LEAD - TAIL);
  const at = (fraction: number) => LEAD + fraction * voice;
  const pop = (start: number, damping = 200) =>
    spring({frame: frame - start, fps, config: {damping}});
  return {frame, fps, voice, at, pop};
};

const rise = (p: number, dy = 34): React.CSSProperties => ({
  opacity: p,
  transform: `translateY(${(1 - p) * dy}px)`,
});

const Base: React.FC<{bg: string; frames: number; children: React.ReactNode}> = ({bg, frames, children}) => {
  const frame = useCurrentFrame();
  const opacity = interpolate(frame, [0, 9, frames - 9, frames], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  return (
    <AbsoluteFill style={{background: bg, opacity, fontFamily: body, color: bg === C.dark ? C.light : C.dark}}>
      {children}
    </AbsoluteFill>
  );
};

const Heading: React.FC<{children: React.ReactNode; color?: string; style?: React.CSSProperties}> = ({
  children,
  color = C.dark,
  style,
}) => (
  <div
    style={{
      fontFamily: display,
      fontSize: 104,
      lineHeight: 1.04,
      textTransform: 'uppercase',
      color,
      ...style,
    }}
  >
    {children}
  </div>
);

const Eyebrow: React.FC<{children: React.ReactNode; color?: string; keepCase?: boolean}> = ({
  children,
  color = C.deep,
  keepCase = false,
}) => (
  <div style={{fontSize: 26, fontWeight: 700, letterSpacing: 5, textTransform: keepCase ? 'none' : 'uppercase', color}}>
    {children}
  </div>
);

const PAD = 120;

// ---------------------------------------------------------------- 1 COVER
export const Cover: React.FC<SceneProps> = ({frames}) => {
  const {frame, pop, at} = useClock(frames);
  return (
    <Base bg={C.dark} frames={frames}>
      <div style={{position: 'absolute', right: 190, top: 250, width: 560, height: 560}}>
        {[0, 1, 2, 3].map((r) => {
          const cycle = ((frame + r * 45) % 180) / 180;
          return (
            <div
              key={r}
              style={{
                position: 'absolute',
                inset: 0,
                borderRadius: '50%',
                border: `5px solid ${C.amber}`,
                opacity: (1 - cycle) * 0.55 * pop(20),
                transform: `scale(${0.18 + cycle * 0.82})`,
              }}
            />
          );
        })}
        <div
          style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            width: 92,
            height: 92,
            marginLeft: -46,
            marginTop: -46,
            borderRadius: '50%',
            background: C.amber,
            transform: `scale(${pop(14, 120)})`,
          }}
        />
      </div>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', height: '100%', justifyContent: 'space-between'}}>
        <div style={rise(pop(4))}>
          <Eyebrow color={C.amber} keepCase>iQOO HACKATHON 2026 · OPEN INNOVATION</Eyebrow>
        </div>
        <div style={{display: 'flex', gap: 14}}>
          {'CUES'.split('').map((letter, i) => (
            <div
              key={i}
              style={{
                fontFamily: display,
                fontSize: 470,
                lineHeight: 0.95,
                color: C.light,
                ...rise(pop(8 + i * 5, 140), 90),
              }}
            >
              {letter}
            </div>
          ))}
        </div>
        <div style={{display: 'flex', flexDirection: 'column', gap: 26}}>
          <div style={{fontSize: 58, fontWeight: 700, lineHeight: 1.2, ...rise(pop(at(0.28)))}}>
            Context you declare.
          </div>
          <div style={{fontSize: 58, fontWeight: 700, lineHeight: 1.2, color: C.amber, ...rise(pop(at(0.62)))}}>
            Behaviour that ends.
          </div>
          <div style={{fontSize: 26, color: C.greyD, marginTop: 10, ...rise(pop(at(0.85)))}}>
            A proposed Android prototype for iQOO. Not an automation builder. Not a system that learns about you.
          </div>
        </div>
      </div>
    </Base>
  );
};

// ---------------------------------------------------------------- 2 PROBLEM
export const Problem: React.FC<SceneProps> = ({frames}) => {
  const {pop, at} = useClock(frames);
  const cards = [
    {e: 'Context', h: 'The same earbuds. Different intentions.', p: 'Earbuds can mean a workout, a call or a commute. You choose which signals matter.', bg: C.white, amber: false, at: 0.18},
    {e: 'Afterwards', h: 'A routine needs an ending.', p: 'The workout ends early. Quiet mode should release its own changes, without overriding another mode.', bg: C.white, amber: false, at: 0.46},
    {e: 'Our focus', h: 'A routine you can check and trust.', p: 'See when it starts, when it stops and why it ran. Rehearse it before you activate it.', bg: C.amber, amber: true, at: 0.72},
  ];
  return (
    <Base bg={C.light} frames={frames}>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', gap: 70}}>
        <div style={rise(pop(4))}>
          <Heading>The hard part is trusting the routine</Heading>
        </div>
        <div style={{display: 'flex', gap: 40}}>
          {cards.map((c) => (
            <div
              key={c.e}
              style={{
                flex: 1,
                background: c.bg,
                border: c.amber ? 'none' : `2px solid ${C.border}`,
                borderRadius: 32,
                padding: 60,
                display: 'flex',
                flexDirection: 'column',
                gap: 24,
                minHeight: 470,
                ...rise(pop(at(c.at))),
              }}
            >
              <Eyebrow color={c.amber ? C.dark : C.deep}>{c.e}</Eyebrow>
              <div style={{fontSize: 50, fontWeight: 700, lineHeight: 1.15}}>{c.h}</div>
              <div style={{fontSize: 32, lineHeight: 1.45, color: c.amber ? C.dark : C.greyL}}>{c.p}</div>
            </div>
          ))}
        </div>
      </div>
    </Base>
  );
};

// ---------------------------------------------------------------- 3 SPEAK
const QUOTE =
  'When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer and quiet notifications. End it if I disconnect.';

export const Speak: React.FC<SceneProps> = ({frames}) => {
  const {frame, pop, at, voice} = useClock(frames);
  const words = QUOTE.split(' ');
  // Words appear across the spoken sentence, starting once "you just say it" is done.
  const start = at(0.16);
  const span = voice * 0.78;
  const bars = 26;
  const speaking = frame > LEAD && frame < LEAD + voice;
  return (
    <Base bg={C.light} frames={frames}>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', gap: 56}}>
        <div style={rise(pop(4))}>
          <Eyebrow>Spoken on the phone</Eyebrow>
          <Heading style={{marginTop: 14}}>Just say it</Heading>
        </div>
        <div style={{display: 'flex', gap: 60, alignItems: 'stretch'}}>
          <div
            style={{
              width: 420,
              background: C.dark,
              borderRadius: 32,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexDirection: 'column',
              gap: 46,
              ...rise(pop(10)),
            }}
          >
            <div style={{position: 'relative', width: 170, height: 170}}>
              {[0, 1, 2].map((r) => {
                const cycle = ((frame + r * 20) % 60) / 60;
                return (
                  <div
                    key={r}
                    style={{
                      position: 'absolute',
                      inset: 0,
                      borderRadius: '50%',
                      border: `4px solid ${C.amber}`,
                      opacity: speaking ? (1 - cycle) * 0.7 : 0,
                      transform: `scale(${1 + cycle * 0.9})`,
                    }}
                  />
                );
              })}
              <div
                style={{
                  position: 'absolute',
                  inset: 0,
                  borderRadius: '50%',
                  background: C.amber,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <svg width="76" height="76" viewBox="0 0 24 24" fill="none" stroke={C.dark} strokeWidth="2" strokeLinecap="round">
                  <rect x="9" y="3" width="6" height="12" rx="3" fill={C.dark} />
                  <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
                </svg>
              </div>
            </div>
            <div style={{display: 'flex', gap: 6, alignItems: 'center', height: 90}}>
              {Array.from({length: bars}).map((_, i) => {
                const h = speaking ? 10 + 74 * Math.abs(Math.sin(frame / 5 + i * 0.6) * Math.cos(frame / 11 + i * 0.35)) : 8;
                return <div key={i} style={{width: 6, height: h, borderRadius: 3, background: C.amber}} />;
              })}
            </div>
          </div>
          <div
            style={{
              flex: 1,
              background: C.amber,
              borderRadius: 32,
              padding: '64px 72px',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              gap: 30,
              ...rise(pop(14)),
            }}
          >
            <div style={{fontSize: 62, fontWeight: 700, lineHeight: 1.28, color: C.dark}}>
              “
              {words.map((w, i) => {
                const p = pop(start + (i / words.length) * span, 200);
                return (
                  <span key={i} style={{opacity: p, display: 'inline-block', marginRight: i === words.length - 1 ? 0 : 16, transform: `translateY(${(1 - p) * 12}px)`}}>
                    {w}
                  </span>
                );
              })}
              <span style={{opacity: pop(start + span, 200)}}>”</span>
            </div>
            <div style={{fontSize: 30, color: C.dark, ...rise(pop(at(0.9)))}}>
              Cues resolves the device and shows every default.
            </div>
          </div>
        </div>
      </div>
    </Base>
  );
};

// ---------------------------------------------------------------- 4 REVIEW
export const Review: React.FC<SceneProps> = ({frames}) => {
  const {pop, at} = useClock(frames);
  const rows = [
    ['WHEN', 'Selected earbuds connect', 0.14],
    ['IF', 'Weekdays, at or after 18:00', 0.28],
    ['DO', 'Focus timer + DND rule', 0.4],
    ['UNTIL', '45 minutes or disconnect', 0.52],
    ['RESTORE', 'End timer, release our DND', 0.64],
  ] as const;
  return (
    <Base bg={C.light} frames={frames}>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', gap: 56}}>
        <div style={rise(pop(4))}>
          <Eyebrow>Review before activating</Eyebrow>
          <Heading style={{marginTop: 14}}>The whole story, up front</Heading>
        </div>
        <div style={{display: 'flex', gap: 60}}>
          <div style={{flex: 1, background: C.dark, borderRadius: 32, padding: '58px 68px', display: 'flex', flexDirection: 'column', gap: 30}}>
            {rows.map(([label, text, f]) => (
              <div key={label} style={{display: 'flex', alignItems: 'baseline', gap: 34, ...rise(pop(at(f)), 24)}}>
                <div style={{width: 220, fontSize: 40, fontWeight: 700, letterSpacing: 3, color: C.amber}}>{label}</div>
                <div style={{fontSize: 46, color: C.light}}>{text}</div>
              </div>
            ))}
          </div>
          <div style={{width: 540, display: 'flex', flexDirection: 'column', gap: 26, ...rise(pop(at(0.72)))}}>
            <div style={{background: C.amber, borderRadius: 32, padding: 50, fontSize: 40, fontWeight: 700, lineHeight: 1.3}}>
              You approve this exact version.
            </div>
            <div style={{fontSize: 27, lineHeight: 1.5, color: C.greyL, padding: '0 8px'}}>
              Repeat: once per connection. Access: Bluetooth, notifications, DND policy and timer scheduling.
            </div>
          </div>
        </div>
      </div>
    </Base>
  );
};

// ---------------------------------------------------------------- 5 ARCHITECTURE
export const Architecture: React.FC<SceneProps> = ({frames}) => {
  const {pop, at} = useClock(frames);
  const top = ['Speak or type', 'Speech to text', 'Model proposes', 'Validate, approve'];
  const topSub = ['Your own words, as a transcript.', 'On device. No audio leaves the phone.', 'Typed data, closed vocabulary. Not code.', 'Bounds and access checked. You approve.'];
  const bottom = ['Event adapters', 'Evaluator', 'Session and actions', 'Exit and receipt'];
  const bottomSub = ['Bluetooth and charging, public APIs.', 'Unknown never permits an action.', 'One session per connection.', 'Release owned effects, then explain.'];
  const grow = pop(at(0.42), 180);
  return (
    <Base bg={C.light} frames={frames}>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', gap: 38}}>
        <div style={rise(pop(4))}>
          <Heading style={{fontSize: 92}}>The model drafts. It never runs anything.</Heading>
        </div>
        <div>
          <div style={{...rise(pop(at(0.06))), marginBottom: 18}}>
            <Eyebrow>Authoring · the model participates here</Eyebrow>
          </div>
          <div style={{display: 'flex', gap: 30}}>
            {top.map((t, i) => (
              <div key={t} style={{flex: 1, background: C.white, border: `2px solid ${C.border}`, borderRadius: 26, padding: '36px 34px', minHeight: 210, ...rise(pop(at(0.08 + i * 0.07)), 26)}}>
                <div style={{fontSize: 36, fontWeight: 700}}>{t}</div>
                <div style={{fontSize: 27, color: C.greyL, marginTop: 14, lineHeight: 1.4}}>{topSub[i]}</div>
              </div>
            ))}
          </div>
        </div>
        <div style={{background: C.amber, borderRadius: 24, height: 96, display: 'flex', alignItems: 'center', padding: '0 46px', fontSize: 34, fontWeight: 700, letterSpacing: 1, transformOrigin: 'left center', transform: `scaleX(${grow})`, opacity: grow, overflow: 'hidden', whiteSpace: 'nowrap'}}>
          TRUST BOUNDARY · Only approved, validated data crosses. No model below this line, ever.
        </div>
        <div>
          <div style={{...rise(pop(at(0.5))), marginBottom: 18}}>
            <Eyebrow>Runtime · deterministic Android code only</Eyebrow>
          </div>
          <div style={{display: 'flex', gap: 30}}>
            {bottom.map((t, i) => (
              <div key={t} style={{flex: 1, background: '#141414', borderRadius: 26, padding: '36px 34px', minHeight: 210, ...rise(pop(at(0.56 + i * 0.09)), 26)}}>
                <div style={{fontSize: 36, fontWeight: 700, color: C.light}}>{t}</div>
                <div style={{fontSize: 27, color: C.greyD, marginTop: 14, lineHeight: 1.4}}>{bottomSub[i]}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </Base>
  );
};

// ---------------------------------------------------------------- 6 ENDING
export const Ending: React.FC<SceneProps> = ({frames}) => {
  const {frame, pop, at} = useClock(frames);
  const x0 = 260;
  const x1 = 1660;
  const startX = x0 + 140;
  const endX = x1 - 140;
  const barP = interpolate(frame, [at(0.08), at(0.3)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  const chips = [
    ['Timer ended', 0.5],
    ['Our DND released', 0.6],
    ['Other modes left untouched', 0.72],
  ] as const;
  return (
    <Base bg={C.dark} frames={frames}>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', gap: 50}}>
        <div style={rise(pop(4))}>
          <Eyebrow color={C.amber}>The part most tools skip</Eyebrow>
          <Heading color={C.light} style={{marginTop: 14}}>The routine ends. You stay in control.</Heading>
        </div>
        <div style={{position: 'relative', height: 250}}>
          <div style={{position: 'absolute', left: x0 - PAD, right: x0 - PAD - 0, top: 120, height: 8, background: C.borderD, borderRadius: 4}} />
          <div
            style={{
              position: 'absolute',
              left: startX - PAD,
              top: 108,
              height: 32,
              width: (endX - startX) * barP,
              background: C.amber,
              borderRadius: 16,
            }}
          />
          <div style={{position: 'absolute', left: startX - PAD - 20, top: 100, width: 48, height: 48, borderRadius: '50%', background: C.amber, border: `6px solid ${C.dark}`, ...rise(pop(at(0.05)), 0)}} />
          <div style={{position: 'absolute', left: startX - PAD - 130, top: 10, width: 300, textAlign: 'center', ...rise(pop(at(0.06)), 16)}}>
            <div style={{fontSize: 40, fontWeight: 700}}>18:12</div>
            <div style={{fontSize: 27, color: C.greyD, marginTop: 6}}>Earbuds connect</div>
          </div>
          <div style={{position: 'absolute', left: endX - PAD - 20, top: 100, width: 48, height: 48, borderRadius: '50%', background: C.light, border: `6px solid ${C.dark}`, ...rise(pop(at(0.32)), 0)}} />
          <div style={{position: 'absolute', left: endX - PAD - 130, top: 10, width: 300, textAlign: 'center', ...rise(pop(at(0.32)), 16)}}>
            <div style={{fontSize: 40, fontWeight: 700}}>18:26</div>
            <div style={{fontSize: 27, color: C.greyD, marginTop: 6}}>You disconnect</div>
          </div>
          <div style={{position: 'absolute', left: (startX + endX) / 2 - PAD - 200, top: 178, width: 400, textAlign: 'center', fontSize: 27, color: C.amber, fontWeight: 600, ...rise(pop(at(0.3)), 10)}}>
            One session
          </div>
        </div>
        <div style={{display: 'flex', gap: 28}}>
          {chips.map(([t, f]) => (
            <div key={t} style={{flex: 1, background: C.cardD, border: `2px solid ${C.borderD}`, borderRadius: 24, padding: '34px 38px', fontSize: 34, fontWeight: 600, ...rise(pop(at(f)), 26)}}>
              {t}
            </div>
          ))}
        </div>
        <div style={{background: C.amber, borderRadius: 24, padding: '34px 44px', fontSize: 34, fontWeight: 600, color: C.dark, ...rise(pop(at(0.86)), 26)}}>
          Receipt · “Started: your earbuds connected at 18:12. Ended: you disconnected at 18:26.”
        </div>
      </div>
    </Base>
  );
};

// ---------------------------------------------------------------- 7 REHEARSE
export const Rehearse: React.FC<SceneProps> = ({frames}) => {
  const {pop, at} = useClock(frames);
  const days = [
    ['MON', '18:24', true],
    ['TUE', '17:30', false],
    ['WED', '18:51', true],
    ['THU', '19:02', true],
    ['FRI', '18:36', true],
    ['SAT', '18:20', false],
    ['SUN', '18:40', false],
  ] as const;
  return (
    <Base bg={C.light} frames={frames}>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', gap: 60}}>
        <div style={rise(pop(4))}>
          <Eyebrow>Before activation</Eyebrow>
          <Heading style={{marginTop: 14}}>Rehearse when it would run</Heading>
        </div>
        <div style={{fontSize: 36, lineHeight: 1.5, color: C.greyL, width: 1500, ...rise(pop(at(0.06)))}}>
          Try the cue on clearly labelled sample events. See which would start, which would be skipped, and why.
        </div>
        <div style={{display: 'flex', gap: 24}}>
          {days.map(([d, t, on], i) => (
            <div
              key={d}
              style={{
                flex: 1,
                borderRadius: 26,
                padding: '38px 30px',
                background: on ? C.amber : C.white,
                border: on ? 'none' : `2px solid ${C.border}`,
                color: on ? C.dark : C.muted,
                minHeight: 250,
                ...rise(pop(at(0.28 + i * 0.075)), 26),
              }}
            >
              <div style={{fontSize: 34, fontWeight: 700}}>{d}</div>
              <div style={{fontSize: 44, fontWeight: 700, marginTop: 18}}>{t}</div>
              <div style={{fontSize: 27, marginTop: 16, fontWeight: 600}}>{on ? 'starts' : 'skipped'}</div>
            </div>
          ))}
        </div>
        <div style={{fontSize: 30, color: C.greyL, ...rise(pop(at(0.86)))}}>
          Illustrative sample events: four starts, three skips. Tuesday is too early. The weekend is excluded. Tap a result to see the reason.
        </div>
      </div>
    </Base>
  );
};

// ---------------------------------------------------------------- 8 CONTEXTUAL
export const Contextual: React.FC<SceneProps> = ({frames}) => {
  const {pop, at} = useClock(frames);
  const inferred = ['Messages', 'Emails', 'Photos', 'What is on screen'];
  const declared = ['These earbuds', 'Weekdays', 'After 18:00', 'Charging'];
  return (
    <Base bg={C.dark} frames={frames}>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', gap: 54}}>
        <div style={rise(pop(4))}>
          <Heading color={C.light} style={{fontSize: 96}}>Contextual is not only about starting</Heading>
        </div>
        <div style={{display: 'flex', gap: 48}}>
          <div style={{flex: 1, background: C.cardD, border: `2px solid ${C.borderD}`, borderRadius: 32, padding: 60, minHeight: 610, display: 'flex', flexDirection: 'column', gap: 26, ...rise(pop(at(0.16)))}}>
            <Eyebrow color={C.greyD}>Inferred · Siri AI in iOS 27</Eyebrow>
            <div style={{fontSize: 44, fontWeight: 700, lineHeight: 1.2}}>Reads a broad personal index</div>
            <div style={{display: 'flex', flexWrap: 'wrap', gap: 16}}>
              {inferred.map((t, i) => (
                <div key={t} style={{fontSize: 30, padding: '14px 26px', borderRadius: 40, border: `2px solid ${C.borderD}`, color: C.greyD, ...rise(pop(at(0.2 + i * 0.05)), 14)}}>
                  {t}
                </div>
              ))}
            </div>
            <div style={{fontSize: 30, lineHeight: 1.45, color: C.greyD, marginTop: 'auto', ...rise(pop(at(0.42)))}}>
              Powerful. We do not claim to beat it.
            </div>
          </div>
          <div style={{flex: 1, background: C.amber, borderRadius: 32, padding: 60, minHeight: 610, display: 'flex', flexDirection: 'column', gap: 26, color: C.dark, ...rise(pop(at(0.5)))}}>
            <Eyebrow color={C.dark}>Declared · Cues</Eyebrow>
            <div style={{fontSize: 44, fontWeight: 700, lineHeight: 1.2}}>You choose the signals</div>
            <div style={{display: 'flex', flexWrap: 'wrap', gap: 16}}>
              {declared.map((t, i) => (
                <div key={t} style={{fontSize: 30, fontWeight: 600, padding: '14px 26px', borderRadius: 40, background: C.dark, color: C.amber, ...rise(pop(at(0.56 + i * 0.06)), 14)}}>
                  {t}
                </div>
              ))}
            </div>
            <div style={{fontSize: 30, lineHeight: 1.45, marginTop: 'auto', fontWeight: 600, ...rise(pop(at(0.85)))}}>
              Everything else stays unknown.
            </div>
          </div>
        </div>
      </div>
    </Base>
  );
};

// ---------------------------------------------------------------- 9 CLOSE
export const Close: React.FC<SceneProps> = ({frames}) => {
  const {pop, at} = useClock(frames);
  const build = ['Bluetooth + charging', 'Focus timer + owned DND', 'Exit and reason receipts'];
  return (
    <Base bg={C.dark} frames={frames}>
      <div style={{padding: PAD, display: 'flex', flexDirection: 'column', height: '100%', justifyContent: 'center', gap: 56}}>
        <div style={{display: 'flex', gap: 14}}>
          {'CUES'.split('').map((letter, i) => (
            <div key={i} style={{fontFamily: display, fontSize: 340, lineHeight: 0.95, color: C.light, ...rise(pop(6 + i * 5, 140), 80)}}>
              {letter}
            </div>
          ))}
        </div>
        <div style={{display: 'flex', gap: 22}}>
          {build.map((t, i) => (
            <div key={t} style={{fontSize: 32, fontWeight: 600, padding: '18px 34px', borderRadius: 50, border: `2px solid ${C.borderD}`, color: C.light, ...rise(pop(at(0.2 + i * 0.12)), 18)}}>
              {t}
            </div>
          ))}
        </div>
        <div>
          <div style={{fontSize: 64, fontWeight: 700, ...rise(pop(at(0.62)))}}>Context you declare.</div>
          <div style={{fontSize: 64, fontWeight: 700, color: C.amber, ...rise(pop(at(0.82)))}}>Behaviour that ends.</div>
        </div>
        <div style={{fontSize: 26, color: C.greyD, ...rise(pop(at(0.9)))}}>Proposed prototype · iQOO Hackathon 2026 · Open Innovation</div>
      </div>
    </Base>
  );
};

export const SCENES: Record<string, React.FC<SceneProps>> = {
  cover: Cover,
  problem: Problem,
  speak: Speak,
  review: Review,
  architecture: Architecture,
  ending: Ending,
  rehearse: Rehearse,
  contextual: Contextual,
  close: Close,
};
