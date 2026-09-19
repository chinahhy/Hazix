// Remote-control feedback sounds for the browser preview.
//
// The TV app plays Android's key-click sound on every focus move and confirm;
// the browser has no such hook, so the same feedback is synthesised with the
// Web Audio API (a short sine blip). There is deliberately no in-app toggle:
// sound is always on and the user silences it where they expect to, in the
// system settings (Android's key-click setting / the device volume). Keep the
// two sides in step: move, confirm and back all click, and holding an arrow
// key down does not machine-gun the sound.

const MOVE = { frequency: 660, duration: 0.028, gain: 0.055 };
const CONFIRM = { frequency: 880, duration: 0.055, gain: 0.08 };
const REPEAT_GAP_MS = 70;

let audioContext = null;
let lastPlayedAt = 0;

function context() {
  const Ctor = globalThis.AudioContext || globalThis.webkitAudioContext;
  if (!Ctor) return null;
  if (!audioContext) {
    try { audioContext = new Ctor(); } catch { return null; }
  }
  // Browsers start the context suspended until the first user gesture. A remote
  // keypress counts as one, so resume and let the next press be heard.
  if (audioContext.state === 'suspended') audioContext.resume().catch(() => {});
  return audioContext;
}

function play(kind) {
  const ctx = context();
  if (!ctx || ctx.state !== 'running') return;
  const now = ctx.currentTime;
  if (kind !== 'confirm' && now * 1000 - lastPlayedAt < REPEAT_GAP_MS) return;
  lastPlayedAt = now * 1000;
  const spec = kind === 'confirm' ? CONFIRM : MOVE;
  const oscillator = ctx.createOscillator();
  const gain = ctx.createGain();
  oscillator.type = 'sine';
  oscillator.frequency.setValueAtTime(spec.frequency, now);
  oscillator.frequency.exponentialRampToValueAtTime(spec.frequency * 0.72, now + spec.duration);
  gain.gain.setValueAtTime(spec.gain, now);
  gain.gain.exponentialRampToValueAtTime(0.0001, now + spec.duration);
  oscillator.connect(gain).connect(ctx.destination);
  oscillator.start(now);
  oscillator.stop(now + spec.duration + 0.01);
}

export function playMoveSound() { play('move'); }
export function playConfirmSound() { play('confirm'); }
export function playBackSound() { play('move'); }
