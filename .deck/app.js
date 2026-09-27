import { mountDeck } from '/deck/deck-engine.js';

mountDeck(document.querySelector('#root'));

// Local presenter controls: no remote state or telemetry.
let touchStartX = null;

document.addEventListener('keydown', (event) => {
  if (event.target.matches('textarea, input, select') || event.key.toLowerCase() !== 'f') return;
  event.preventDefault();
  if (document.fullscreenElement) document.exitFullscreen?.();
  else document.documentElement.requestFullscreen?.().catch(() => {});
});

document.addEventListener('touchstart', (event) => {
  touchStartX = event.changedTouches[0]?.clientX ?? null;
}, { passive: true });

document.addEventListener('touchend', (event) => {
  const endX = event.changedTouches[0]?.clientX;
  if (touchStartX === null || endX === undefined) return;
  const distance = endX - touchStartX;
  touchStartX = null;
  if (Math.abs(distance) < 56) return;
  document.dispatchEvent(new KeyboardEvent('keydown', {
    key: distance < 0 ? 'ArrowRight' : 'ArrowLeft',
    bubbles: true
  }));
}, { passive: true });
