// AnimationController.js
// Manages the 8-phase animation state machine with smooth easing.

export const PHASE = {
  IDLE_FACE: 0,
  DISSOLVE: 1,
  STRAND_FLOW: 2,
  CORE_FORM: 3,
  SCULPTURE: 4,
  EXPLODE: 5,
  REFORM: 6,
};

const PHASE_DURATIONS = {
  [PHASE.IDLE_FACE]:   Infinity,
  [PHASE.DISSOLVE]:    3000,
  [PHASE.STRAND_FLOW]: 2500,
  [PHASE.CORE_FORM]:   2000,
  [PHASE.SCULPTURE]:   Infinity,
  [PHASE.EXPLODE]:     1500,
  [PHASE.REFORM]:      4000,
};

const PHASE_SEQUENCE_FORWARD = [
  PHASE.IDLE_FACE, PHASE.DISSOLVE, PHASE.STRAND_FLOW, PHASE.CORE_FORM, PHASE.SCULPTURE,
];
const PHASE_SEQUENCE_RETURN = [
  PHASE.SCULPTURE, PHASE.EXPLODE, PHASE.REFORM, PHASE.IDLE_FACE,
];

// Smooth cubic easing
export const easeInOutCubic = t => t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2;
export const easeOutExpo = t => t === 1 ? 1 : 1 - Math.pow(2, -10 * t);
export const easeInExpo = t => t === 0 ? 0 : Math.pow(2, 10 * t - 10);

export class AnimationController {
  constructor() {
    this.phase = PHASE.IDLE_FACE;
    this.phaseT = 0;      // 0→1 within current phase
    this.phaseStart = 0;
    this.direction = 'forward'; // 'forward' | 'return'
    this.listeners = {};
  }

  on(event, fn) {
    this.listeners[event] = fn;
  }

  trigger(now) {
    if (this.phase === PHASE.IDLE_FACE) {
      this._startPhase(PHASE.DISSOLVE, now);
      this.direction = 'forward';
    } else if (this.phase === PHASE.SCULPTURE) {
      this._startPhase(PHASE.EXPLODE, now);
      this.direction = 'return';
    }
  }

  _startPhase(phase, now) {
    this.phase = phase;
    this.phaseStart = now;
    this.phaseT = 0;
    if (this.listeners.phaseChange) this.listeners.phaseChange(phase);
  }

  update(now) {
    const dur = PHASE_DURATIONS[this.phase];
    if (dur === Infinity) {
      this.phaseT = 1;
      return;
    }
    const elapsed = now - this.phaseStart;
    this.phaseT = Math.min(elapsed / dur, 1);

    if (this.phaseT >= 1) {
      // Advance to next phase
      const seq = this.direction === 'forward' ? PHASE_SEQUENCE_FORWARD : PHASE_SEQUENCE_RETURN;
      const idx = seq.indexOf(this.phase);
      if (idx >= 0 && idx < seq.length - 1) {
        this._startPhase(seq[idx + 1], now);
      }
    }
  }

  // Returns a single 0→N float usable in shaders:
  // 0 = IDLE_FACE, 1 = dissolving, 2 = strands, 3 = core, 4 = sculpture,
  // 5 = exploding, 6 = reforming
  getShaderPhase() {
    const t = easeInOutCubic(this.phaseT);
    return this.phase + t;
  }
}
