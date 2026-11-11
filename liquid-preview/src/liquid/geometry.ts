export type Theme = "light" | "dark";
export interface LiquidFrame {
    progress: number;
    impact: boolean;
    filling: boolean;
    finished: boolean;
}
export const IMPACT = 0.22,
    FILL = 0.28;
export const frameAt = (progress: number): LiquidFrame => {
    const p = Math.max(0, Math.min(1, progress));
    return {
        progress: p,
        impact: p >= IMPACT,
        filling: p >= FILL && p < 1,
        finished: p >= 1,
    };
};
const oval = (x: number, y: number, rx: number, ry: number) =>
    `M${x - rx},${y}a${rx},${ry} 0 1 1 ${rx * 2},0a${rx},${ry} 0 1 1 ${-rx * 2},0Z`;
/** Clockwise union subpaths under nonzero winding; no opaque liquid-colour overlay. */
export function liquidPath(
    w: number,
    h: number,
    x: number,
    y: number,
    progress: number,
): string {
    const p = frameAt(progress).progress;
    if (p >= 1) return `M0,0H${w}V${h}H0Z`;
    if (p <= 0 || w <= 0 || h <= 0) return "";
    x = Math.max(0, Math.min(w, x));
    y = Math.max(0, Math.min(h, y));
    const s = Math.min(w, h) / 400,
        fill = Math.max(0, (p - FILL) / (1 - FILL));
    const level = h + 20 * s - (h + 55 * s) * (fill * fill * (3 - 2 * fill));
    const amp =
        (5 + 10 * Math.sin(fill * Math.PI)) * s * Math.min(1, fill * 12);
    const wave = (xx: number) =>
        level +
        amp *
            (Math.sin((xx / w) * 4 * Math.PI + p * 24) +
                0.42 * Math.sin((xx / w) * 9 * Math.PI - p * 19));
    let d = "";
    if (p >= IMPACT) {
        d = `M0,${wave(0)}`;
        for (let i = 1; i <= 64; i++) {
            const xx = (w * i) / 64;
            d += `L${xx},${wave(xx)}`;
        }
        d += `L${w},${h + 40 * s}H0Z`;
    }
    const end = Math.min(
        y + (h - y) * Math.min(1, p / IMPACT),
        Math.max(y, level + amp),
    );
    const width = (5 + 2 * Math.sin(p * 31)) * s * (1 - fill * 0.65);
    if (level > y) {
        for (let i = 0; i <= 36; i++) {
            const t = i / 36,
                yy = y + (end - y) * t,
                xx = x + Math.sin(t * 9 - p * 26) * 7 * s * t,
                half = width * (0.65 + 0.3 * Math.sin(t * 19 + p * 42));
            d += `${i === 0 ? "M" : "L"}${xx + half},${yy}`;
        }
        for (let i = 36; i >= 0; i--) {
            const t = i / 36,
                yy = y + (end - y) * t,
                xx = x + Math.sin(t * 9 - p * 26) * 7 * s * t,
                half = width * (0.65 + 0.3 * Math.sin(t * 19 + p * 42));
            d += `L${xx - half},${yy}`;
        }
        d += "Z";
        for (let i = 0; i < 4; i++) {
            const t = (p * 2.9 + i * 0.23) % 1,
                yy = y + (h - y) * t;
            if (yy < level - 9 * s)
                d += oval(
                    x + Math.sin(p * 23 + i) * (14 + i * 5) * s,
                    yy,
                    2.5 * s,
                    4.5 * s,
                );
        }
    }
    const splash = Math.max(0, Math.min(1, (p - IMPACT) / 0.22));
    if (p >= IMPACT && splash < 1) {
        for (let i = 0; i < 10; i++) {
            const a = Math.PI * (0.1 + (i * 0.8) / 9),
                r = (35 + (i % 3) * 18) * s * splash;
            d += oval(
                x + Math.cos(a) * r * 2.5,
                h - Math.sin(a) * 190 * s * splash + 170 * s * splash * splash,
                (2 + (i % 3)) * s * (1 - splash),
                (3 + (i % 2)) * s * (1 - splash),
            );
        }
        if (splash > 0)
            d += oval(
                x,
                Math.min(h - 2 * s, level),
                70 * s * splash,
                5 * s * (1 - splash),
            );
    }
    return d;
}
