import { describe, it, expect } from "vitest";
import { frameAt, liquidPath } from "./geometry";
import { WaterAudio } from "./audio";
describe("shared liquid clock", () => {
    it("does not replay impact before the falling stream reaches the floor", () => {
        expect(frameAt(0.219).impact).toBe(false);
        expect(frameAt(0.22).impact).toBe(true);
        expect(frameAt(0.27).filling).toBe(false);
        expect(frameAt(0.28).filling).toBe(true);
        expect(frameAt(1.8)).toEqual({
            progress: 1,
            impact: true,
            filling: false,
            finished: true,
        });
    });
    it("never emits invalid coordinates and reaches full coverage on varied viewports", () => {
        for (const [w, h] of [
            [390, 844],
            [1440, 900],
            [820, 1180],
        ]) {
            for (let i = 0; i <= 100; i++)
                expect(liquidPath(w, h, w * 0.8, 90, i / 100)).not.toMatch(
                    /NaN|Infinity/,
                );
            expect(liquidPath(w, h, w / 2, 90, 1)).toBe(`M0,0H${w}V${h}H0Z`);
            const early = liquidPath(w, h, w / 2, 90, 0.1),
                impact = liquidPath(w, h, w / 2, 90, 0.3);
            expect(early).not.toBe(impact);
            expect(impact).toContain("a");
        }
    });
});
describe("audio failure isolation", () => {
    it("can be muted, updated and disposed without an available AudioContext", async () => {
        const audio = new WaterAudio();
        await expect(audio.unlock()).resolves.toBeUndefined();
        audio.start("dark", 0.25);
        audio.update(frameAt(0.4));
        audio.stop(false);
        audio.dispose();
    });
});
