const KEY = 'cuso_sound';
let ctx = null;

export function isSoundOn() {
  return localStorage.getItem(KEY) !== 'off';
}

export function setSoundOn(on) {
  localStorage.setItem(KEY, on ? 'on' : 'off');
}

function getCtx() {
  if (!ctx) {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return null;
    ctx = new Ctx();
  }
  return ctx;
}

// 三种同类 8-bit 点击音，每次随机选一个
const VARIANTS = [
  { steps: [{ f: 880, at: 0 }, { f: 520, at: 0.05 }], dur: 0.09, gain: 0.055 },
  { steps: [{ f: 988, at: 0 }, { f: 1319, at: 0.045 }], dur: 0.13, gain: 0.05 },
  { steps: [{ f: 660, at: 0 }, { f: 392, at: 0.04 }], dur: 0.08, gain: 0.06 },
];

export function playClick() {
  if (!isSoundOn()) return;
  try {
    const audio = getCtx();
    if (!audio) return;
    if (audio.state === 'suspended') audio.resume();

    const t = audio.currentTime;
    const variant = VARIANTS[Math.floor(Math.random() * VARIANTS.length)];

    const osc = audio.createOscillator();
    const gain = audio.createGain();
    osc.type = 'square';

    for (const step of variant.steps) {
      osc.frequency.setValueAtTime(step.f, t + step.at);
    }

    gain.gain.setValueAtTime(variant.gain, t);
    gain.gain.exponentialRampToValueAtTime(0.0008, t + variant.dur);

    osc.connect(gain);
    gain.connect(audio.destination);
    osc.start(t);
    osc.stop(t + variant.dur + 0.01);
  } catch (e) {
    // 忽略音频错误（如浏览器未授权）
  }
}
