import type { LiquidFrame, Theme } from "./geometry";
export interface LiquidAudioAdapter {
    unlock(): Promise<void>;
    start(theme: Theme, volume: number): void;
    update(frame: LiquidFrame): void;
    stop(completed: boolean): void;
    feedback(theme: Theme, volume: number): void;
    dispose(): void;
}
interface Voice {
    source: AudioScheduledSourceNode;
    gain: GainNode;
    filter?: BiquadFilterNode;
}
/** Original local synthesis. A single lazily unlocked AudioContext; no audio on mount. */
export class WaterAudio implements LiquidAudioAdapter {
    private context?: AudioContext;
    private voices = new Set<Voice>();
    private flow?: Voice;
    private active = false;
    private impacted = false;
    private theme: Theme = "light";
    private volume = 0;
    async unlock() {
        try {
            this.context ??= new AudioContext();
            if (this.context.state === "suspended") await this.context.resume();
        } catch {
            /* Theme switching is independent of permission/device availability. */
        }
    }
    private ready() {
        return this.context?.state === "running" && this.volume > 0;
    }
    start(theme: Theme, volume: number) {
        this.stop(false);
        this.theme = theme;
        this.volume = Math.max(0, Math.min(1, volume));
        this.active = !!this.ready();
        this.impacted = false;
        if (this.active) this.tone(false);
    }
    update(frame: LiquidFrame) {
        if (!this.active) return;
        try {
            if (frame.impact && !this.impacted) {
                this.impacted = true;
                this.noise(0.25, false, 0.16);
            }
            if (frame.filling && !this.flow)
                this.flow = this.noise(1, true, 0.065);
            if (this.flow && this.context)
                this.flow.gain.gain.setTargetAtTime(
                    this.volume * 0.065 * Math.min(1, (1 - frame.progress) * 9),
                    this.context.currentTime,
                    0.03,
                );
        } catch {
            this.stop(false);
        }
    }
    stop(completed: boolean) {
        const finish = completed && this.active;
        this.active = false;
        this.flow = undefined;
        for (const voice of [...this.voices]) this.release(voice, true);
        if (finish) this.tone(true);
    }
    feedback(theme: Theme, volume: number) {
        this.stop(false);
        this.theme = theme;
        this.volume = volume;
        if (this.ready()) this.tone(true);
    }
    dispose() {
        this.stop(false);
        void this.context?.close().catch(() => {});
        this.context = undefined;
    }
    private release(v: Voice, stop = false) {
        if (!this.voices.delete(v)) return;
        v.source.onended = null;
        if (stop)
            try {
                v.source.stop();
            } catch {}
        v.source.disconnect();
        v.gain.disconnect();
        v.filter?.disconnect();
    }
    private tone(finish: boolean) {
        const c = this.context;
        if (!c || !this.ready()) return;
        try {
            const source = c.createOscillator(),
                gain = c.createGain();
            const pitch = this.theme === "dark" ? 0.74 : 1.18,
                now = c.currentTime,
                duration = finish ? 0.16 : 0.18;
            source.type = "sine";
            source.frequency.setValueAtTime((finish ? 370 : 650) * pitch, now);
            source.frequency.exponentialRampToValueAtTime(
                (finish ? 260 : 210) * pitch,
                now + duration,
            );
            gain.gain.setValueAtTime(0.0001, now);
            gain.gain.exponentialRampToValueAtTime(
                Math.max(0.0001, this.volume * (finish ? 0.055 : 0.08)),
                now + 0.008,
            );
            gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
            source.connect(gain).connect(c.destination);
            const v = { source, gain };
            this.voices.add(v);
            source.onended = () => this.release(v);
            source.start(now);
            source.stop(now + duration + 0.01);
        } catch {
            this.stop(false);
        }
    }
    private noise(
        duration: number,
        loop: boolean,
        level: number,
    ): Voice | undefined {
        const c = this.context;
        if (!c || !this.ready()) return;
        const source = c.createBufferSource(),
            gain = c.createGain(),
            filter = c.createBiquadFilter();
        const b = c.createBuffer(
                1,
                Math.floor(c.sampleRate * duration),
                c.sampleRate,
            ),
            data = b.getChannelData(0);
        let seed = 1729;
        for (let i = 0; i < data.length; i++) {
            seed = (Math.imul(seed, 1664525) + 1013904223) | 0;
            data[i] = seed / 2147483648;
        }
        source.buffer = b;
        source.loop = loop;
        filter.type = "lowpass";
        filter.frequency.value = this.theme === "dark" ? 520 : 1050;
        const now = c.currentTime;
        gain.gain.setValueAtTime(0.0001, now);
        gain.gain.exponentialRampToValueAtTime(
            Math.max(0.0001, this.volume * level),
            now + 0.015,
        );
        if (!loop)
            gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
        source.connect(filter).connect(gain).connect(c.destination);
        const v = { source, gain, filter };
        this.voices.add(v);
        source.onended = () => this.release(v);
        source.start(now);
        if (!loop) source.stop(now + duration + 0.01);
        return v;
    }
}
