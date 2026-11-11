import { afterEach, describe, expect, it, vi } from "vitest";
import { WaterAudio } from "./audio";
import { frameAt } from "./geometry";
class Param {
    value = 0;
    setValueAtTime = vi.fn();
    exponentialRampToValueAtTime = vi.fn();
    setTargetAtTime = vi.fn();
}
class Node {
    gain = new Param();
    frequency = new Param();
    type = "";
    buffer: unknown;
    loop = false;
    onended: (() => void) | null = null;
    connect = vi.fn(() => this);
    disconnect = vi.fn();
    start = vi.fn();
    stop = vi.fn();
}
class Context {
    static instance: Context;
    state = "suspended";
    currentTime = 0;
    sampleRate = 24000;
    destination = new Node();
    oscillators: Node[] = [];
    sources: Node[] = [];
    constructor() {
        Context.instance = this;
    }
    resume = vi.fn(async () => {
        this.state = "running";
    });
    close = vi.fn(async () => {
        this.state = "closed";
    });
    createOscillator() {
        const n = new Node();
        this.oscillators.push(n);
        return n;
    }
    createBufferSource() {
        const n = new Node();
        this.sources.push(n);
        return n;
    }
    createGain() {
        return new Node();
    }
    createBiquadFilter() {
        return new Node();
    }
    createBuffer(_channels: number, length: number) {
        return { getChannelData: () => new Float32Array(length) };
    }
}
afterEach(() => vi.unstubAllGlobals());
describe("audio follows actual reveal progress", () => {
    it("opens no context on construction and emits each phase only once", async () => {
        vi.stubGlobal("AudioContext", Context);
        const a = new WaterAudio();
        expect(Context.instance).toBeUndefined();
        await a.unlock();
        const c = Context.instance;
        a.start("dark", 0.4);
        expect(c.oscillators).toHaveLength(1);
        a.update(frameAt(0.21));
        expect(c.sources).toHaveLength(0);
        a.update(frameAt(0.22));
        a.update(frameAt(0.24));
        expect(c.sources).toHaveLength(1);
        a.update(frameAt(0.3));
        a.update(frameAt(0.8));
        expect(c.sources).toHaveLength(2);
        expect(c.sources[1].loop).toBe(true);
        a.stop(true);
        expect(c.oscillators).toHaveLength(2);
        expect(
            c.sources.every((s) => s.disconnect.mock.calls.length === 1),
        ).toBe(true);
        a.dispose();
        expect(c.close).toHaveBeenCalledOnce();
    });
    it("interrupt or mute stops the flow and never produces a completion cue", async () => {
        vi.stubGlobal("AudioContext", Context);
        const a = new WaterAudio();
        await a.unlock();
        a.start("light", 0.3);
        a.update(frameAt(0.5));
        const c = Context.instance;
        a.stop(false);
        a.update(frameAt(0.99));
        a.stop(true);
        expect(c.oscillators).toHaveLength(1);
        expect(c.sources).toHaveLength(2);
        expect(
            c.sources.every(
                (s) =>
                    s.stop.mock.lastCall?.length === 0 &&
                    s.disconnect.mock.calls.length === 1,
            ),
        ).toBe(true);
        a.dispose();
    });
    it("zero volume creates no sources even after unlock", async () => {
        vi.stubGlobal("AudioContext", Context);
        const a = new WaterAudio();
        await a.unlock();
        a.start("dark", 0);
        a.update(frameAt(0.5));
        a.stop(true);
        expect(Context.instance.sources).toHaveLength(0);
        expect(Context.instance.oscillators).toHaveLength(0);
        a.dispose();
    });
});
