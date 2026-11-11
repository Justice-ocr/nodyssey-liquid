import { useCallback, useEffect, useRef } from "react";
import { flushSync } from "react-dom";
import { frameAt, liquidPath, type Theme } from "./geometry";
import type { LiquidAudioAdapter } from "./audio";

export interface LiquidRevealOptions {
    theme: Theme;
    onThemeChange: (theme: Theme) => void;
    durationMs?: number;
    soundEnabled?: boolean;
    volume?: number;
    audio?: LiquidAudioAdapter;
    reducedMotion?: boolean;
}
interface Active {
    id: number;
    frame: number;
    finish: (completed: boolean) => void;
}
let sequence = 0;
const safe = (fn: () => void) => {
    try {
        fn();
    } catch {}
};

/** Snapshot only DOM/CSS/SVG. Unsupported live surfaces intentionally trigger a direct switch. */
export function canSnapshot(root: HTMLElement): boolean {
    if (root.querySelector("canvas,video,iframe,object,embed")) return false;
    return ![root, ...root.querySelectorAll("*")].some(
        (el) => el.shadowRoot !== null,
    );
}

function snapshot(
    root: HTMLElement,
    theme: Theme,
    w: number,
    h: number,
): HTMLElement {
    const clone = root.cloneNode(true) as HTMLElement;
    clone.dataset.theme = theme;
    clone.setAttribute("aria-hidden", "true");
    clone.inert = true;
    const originals = [root, ...root.querySelectorAll<HTMLElement>("*")];
    const copies = [clone, ...clone.querySelectorAll<HTMLElement>("*")];
    // Re-key SVG references and labels; no duplicate IDs even while the snapshot is attached.
    const ids = new Map<string, string>();
    for (const el of copies)
        if (el.id) {
            const id = `liquid-copy-${sequence}-${ids.size}`;
            ids.set(el.id, id);
            el.id = id;
        }
    for (let i = 0; i < copies.length; i++) {
        const src = originals[i],
            dst = copies[i];
        dst.removeAttribute("autofocus");
        dst.removeAttribute("name");
        for (const attr of [...dst.attributes]) {
            if (attr.name.startsWith("on")) dst.removeAttribute(attr.name);
            else {
                let value = attr.value;
                for (const [old, id] of ids) {
                    value = value.replaceAll(`url(#${old})`, `url(#${id})`);
                    if (
                        ["href", "xlink:href"].includes(attr.name) &&
                        value === `#${old}`
                    )
                        value = `#${id}`;
                    if (
                        ["for", "aria-labelledby", "aria-describedby"].includes(
                            attr.name,
                        )
                    )
                        value = value
                            .split(" ")
                            .map((v) => ids.get(v) ?? v)
                            .join(" ");
                }
                if (value !== attr.value) dst.setAttribute(attr.name, value);
            }
        }
        if (
            src instanceof HTMLInputElement &&
            dst instanceof HTMLInputElement
        ) {
            dst.value = src.value;
            dst.checked = src.checked;
        }
        if (
            src instanceof HTMLTextAreaElement &&
            dst instanceof HTMLTextAreaElement
        )
            dst.value = src.value;
        if (
            src instanceof HTMLSelectElement &&
            dst instanceof HTMLSelectElement
        )
            dst.value = src.value;
        dst.scrollTop = src.scrollTop;
        dst.scrollLeft = src.scrollLeft;
    }
    const stage = document.createElement("div");
    stage.dataset.liquidOverlay = "";
    stage.setAttribute("aria-hidden", "true");
    stage.inert = true;
    Object.assign(stage.style, {
        position: "fixed",
        inset: "0",
        width: `${w}px`,
        height: `${h}px`,
        zIndex: "2147483000",
        pointerEvents: "none",
        overflow: "hidden",
        isolation: "isolate",
    });
    const rect = root.getBoundingClientRect();
    Object.assign(clone.style, {
        position: "absolute",
        margin: "0",
        left: `${rect.left}px`,
        top: `${rect.top}px`,
        width: `${rect.width}px`,
        height: `${root.scrollHeight}px`,
        maxWidth: "none",
    });
    stage.append(clone);
    // Freeze sticky/fixed boxes at their observed viewport bounds before attachment. Moving these
    // painted clones to the stage avoids their original transformed/scrolling containing blocks.
    for (let i = 1; i < originals.length; i++) {
        const src = originals[i],
            dst = copies[i],
            style = getComputedStyle(src);
        if (style.position === "sticky" || style.position === "fixed") {
            const r = src.getBoundingClientRect();
            const holder = document.createElement("div");
            holder.dataset.theme = theme;
            holder.className = "liquid-fixed-snapshot";
            Object.assign(holder.style, {
                position: "absolute",
                left: `${r.left}px`,
                top: `${r.top}px`,
                width: `${r.width}px`,
                height: `${r.height}px`,
            });
            Object.assign(dst.style, {
                position: "static",
                margin: "0",
                width: "100%",
                height: "100%",
                transform: "none",
                inset: "auto",
            });
            holder.append(dst);
            stage.append(holder);
        }
    }
    return stage;
}

/** One RAF loop, one path write per frame. React only commits once after completion/interruption. */
export function useLiquidThemeReveal(options: LiquidRevealOptions) {
    const rootRef = useRef<HTMLDivElement>(null);
    const optionsRef = useRef(options);
    optionsRef.current = options;
    const active = useRef<Active | null>(null);
    const alive = useRef(true);
    const cancel = useCallback(() => active.current?.finish(false), []);
    useEffect(() => {
        const current = optionsRef.current;
        if (!current.soundEnabled || !current.volume)
            safe(() => current.audio?.stop(false));
    }, [options.soundEnabled, options.volume]);
    useEffect(() => {
        if (options.reducedMotion) cancel();
    }, [options.reducedMotion, cancel]);
    useEffect(() => {
        alive.current = true;
        const visibility = () => {
            if (document.hidden) {
                cancel();
                safe(() => optionsRef.current.audio?.stop(false));
            }
        };
        const resize = () => cancel();
        const motion = matchMedia("(prefers-reduced-motion: reduce)");
        const onMotion = () => {
            if (motion.matches) cancel();
        };
        document.addEventListener("visibilitychange", visibility);
        window.addEventListener("resize", resize);
        window.addEventListener("scroll", resize, { passive: true });
        window.visualViewport?.addEventListener("resize", resize);
        motion.addEventListener("change", onMotion);
        return () => {
            alive.current = false;
            // Unmount is not a completed theme request: do not update a caller that has gone away.
            const run = active.current;
            active.current = null;
            run?.finish(false);
            safe(() => optionsRef.current.audio?.stop(false));
            document.removeEventListener("visibilitychange", visibility);
            window.removeEventListener("resize", resize);
            window.removeEventListener("scroll", resize);
            window.visualViewport?.removeEventListener("resize", resize);
            motion.removeEventListener("change", onMotion);
        };
    }, [cancel]);
    const toggle = useCallback((button: HTMLElement) => {
        if (active.current) return;
        const o = optionsRef.current,
            root = rootRef.current;
        if (!root) return;
        const target: Theme = o.theme === "dark" ? "light" : "dark";
        const audio = o.audio,
            volume = Math.max(0, Math.min(1, o.volume ?? 0.25));
        if (o.soundEnabled && volume > 0)
            safe(() => {
                void audio?.unlock().catch(() => {});
            });
        const reduced =
            o.reducedMotion ??
            matchMedia("(prefers-reduced-motion: reduce)").matches;
        const supported =
            CSS.supports("clip-path", 'url("#liquid-probe")') &&
            canSnapshot(root);
        if (reduced || !supported) {
            o.onThemeChange(target);
            if (o.soundEnabled && volume > 0)
                safe(() => {
                    void audio
                        ?.unlock()
                        .then(() => {
                            if (
                                alive.current &&
                                !document.hidden &&
                                optionsRef.current.soundEnabled &&
                                (optionsRef.current.volume ?? 0) > 0
                            )
                                audio.feedback(target, volume);
                        })
                        .catch(() => {});
                });
            return;
        }
        const id = ++sequence,
            w = innerWidth,
            h = innerHeight,
            r = button.getBoundingClientRect(),
            x = r.left + r.width / 2,
            y = r.bottom;
        const stage = snapshot(root, target, w, h);
        const ns = "http://www.w3.org/2000/svg",
            svg = document.createElementNS(ns, "svg"),
            defs = document.createElementNS(ns, "defs");
        const clip = document.createElementNS(ns, "clipPath"),
            path = document.createElementNS(ns, "path");
        svg.setAttribute("width", "0");
        svg.setAttribute("height", "0");
        svg.setAttribute("aria-hidden", "true");
        clip.id = `liquid-clip-${id}`;
        clip.setAttribute("clipPathUnits", "userSpaceOnUse");
        path.setAttribute("clip-rule", "nonzero");
        clip.append(path);
        defs.append(clip);
        svg.append(defs);
        stage.style.clipPath = `url("#${clip.id}")`;
        document.body.append(svg, stage);
        const priorInert = root.inert;
        const focused = document.activeElement as HTMLElement | null;
        root.inert = true;
        const stopScroll = (event: Event) => event.preventDefault();
        window.addEventListener("wheel", stopScroll, { passive: false });
        window.addEventListener("touchmove", stopScroll, { passive: false });
        let closed = false;
        const run: Active = {
            id,
            frame: 0,
            finish: (completed) => {
                if (closed) return;
                closed = true;
                const mounted = active.current === run;
                cancelAnimationFrame(run.frame);
                safe(() => audio?.stop(completed));
                if (mounted) {
                    active.current = null;
                    flushSync(() => optionsRef.current.onThemeChange(target));
                }
                stage.remove();
                svg.remove();
                root.inert = priorInert;
                window.removeEventListener("wheel", stopScroll);
                window.removeEventListener("touchmove", stopScroll);
                if (mounted && focused?.isConnected)
                    focused.focus({ preventScroll: true });
            },
        };
        active.current = run;
        if (o.soundEnabled && volume > 0)
            safe(() => {
                void audio
                    ?.unlock()
                    .then(() => {
                        if (
                            active.current === run &&
                            !document.hidden &&
                            optionsRef.current.soundEnabled &&
                            (optionsRef.current.volume ?? 0) > 0
                        )
                            audio.start(target, volume);
                    })
                    .catch(() => {});
            });
        const started = performance.now(),
            duration = Math.max(800, Math.min(8000, o.durationMs ?? 2600));
        const tick = (now: number) => {
            if (active.current !== run) return;
            const frame = frameAt((now - started) / duration);
            path.setAttribute("d", liquidPath(w, h, x, y, frame.progress));
            safe(() => audio?.update(frame));
            if (frame.finished) run.finish(true);
            else run.frame = requestAnimationFrame(tick);
        };
        run.frame = requestAnimationFrame(tick);
    }, []);
    const previewSound = useCallback(async () => {
        if (active.current) return;
        const o = optionsRef.current;
        if (!o.soundEnabled || !o.volume || !o.audio) return;
        try {
            await o.audio.unlock();
            if (
                alive.current &&
                !document.hidden &&
                optionsRef.current.soundEnabled &&
                (optionsRef.current.volume ?? 0) > 0
            )
                o.audio.feedback(
                    o.theme === "dark" ? "light" : "dark",
                    o.volume,
                );
        } catch {}
    }, []);
    return {
        rootRef,
        toggle,
        cancel,
        previewSound,
        isAnimating: () => !!active.current,
    };
}
