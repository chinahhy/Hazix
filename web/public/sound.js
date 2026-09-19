// Remote-control feedback sounds for the browser preview.
//
// The TV app plays Android's key-click sound on every focus move and confirm;
// the browser has no such hook, so the same feedback is synthesised with the
// Web Audio API (a short sine blip) and gated by a user setting that persists
// in localStorage. Keep the two in step: move, confirm and back all click, and
// holding an arrow key down does not machine-gun the sound.

const STORAGE_KEY = 'hdao.web.sound.v1';
const MOVE = { frequency: 660, duration: 0.028, gain: 0.055 };
const CONFIRM = { frequency: 880, duration: 0.055, gain: 0.08 };
const REPEAT_GAP_MS = 70;
const IGNORED_KEYS = new Set(['Shift', 'Control', 'Alt', 'Meta', 'CapsLock', 'Tab', 'F5', 'F11', 'F12']);

let enabled = readEnabled();
let audioContext = null;
let lastPlayedAt = 0;

function readEnabled() {
  try { return localStorage.getItem(STORAGE_KEY) !== 'off'; } catch { return true; }
}

export function soundEnabled() {
  return enabled;
}

export function setSoundEnabled(value) {
  enabled = Boolean(value);
  try { localStorage.setItem(STORAGE_KEY, enabled ? 'on' : 'off'); } catch { /* private mode */ }
  if (enabled) play('confirm');
}

function context() {
  const Ctor = globalThis.AudioContext || globalThis.webkitAudioContext;
  if (!Ctor) return null;
  audioContext ||= new Ctor();
  // Browsers start the context suspended until the first user gesture. A remote
  // keypress counts as one, so resume and let the next press be heard.
  if (audioContext.state === 'suspended') audioContext.resume().catch(() => {});
  return audioContext;
}

function play(kind) {
  if (!enabled) return;
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

/** Re-reads nothing; exposed so tests and the app can force a state. */
export function resetSoundForTest() {
  enabled = readEnabled();
  lastPlayedAt = 0;
}
