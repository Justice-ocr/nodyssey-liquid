# Liquid Theme Reveal — interactive preview

This is a complete React/TypeScript/Vite example and reusable web implementation. It is **not** the Android app and never accesses a forum account or API. Android integration is documented in [../docs/liquid-theme-reveal.md](../docs/liquid-theme-reveal.md).

## Run

```sh
cd liquid-preview
npm ci
npm run dev -- --port 4178
# http://127.0.0.1:4178
npm run build
npm run test
npx playwright install chromium
npm run test:e2e
```

On Windows the Playwright config uses installed Chrome. Override `CHROME_PATH` to use another executable; other platforms use Playwright Chromium.

Use either theme button. At the bottom, enable sound, set volume, and press **简短试听** for a quiet completion cue. Then switch the theme to hear the full progress-synchronized pour/splash/flow sequence. Sound defaults off and is never initialized on mount. Light-to-dark is lower; dark-to-light is brighter. Settings and theme persist locally. The inline head script restores theme before first paint.

## Reuse

```tsx
import {useState, useMemo} from 'react';
import {useLiquidThemeReveal} from './src/liquid/useLiquidThemeReveal';
import {WaterAudio} from './src/liquid/audio';
import type {Theme} from './src/liquid/geometry';

function ExistingApp() {
  const [theme, setTheme] = useState<Theme>('light');
  const audio = useMemo(() => new WaterAudio(), []);
  const {rootRef, toggle, cancel, previewSound} = useLiquidThemeReveal({
    theme,
    onThemeChange: setTheme,
    durationMs: 2600,
    soundEnabled: false,
    volume: 0.25,
    audio, // optional; accepts any LiquidAudioAdapter
  });
  // Dispose an owned audio adapter when its owning app unmounts.
  return <div ref={rootRef} data-theme={theme}>
    <button onClick={event => toggle(event.currentTarget)}>Theme</button>
    {/* Original application content, rendered exactly once. */}
  </div>;
}
```

`LiquidRevealOptions`, `LiquidAudioAdapter`, `LiquidFrame`, `Theme` are exported TypeScript contracts. The adapter must isolate its own output errors; the hook also guards all calls. `cancel()` settles the requested theme without a finish sound. `previewSound()` only works with sound enabled and positive volume. The caller owns audio-adapter lifetime, theme persistence and CSS variables. Theme changes must preserve element dimensions and fonts.

## How it works

- The original React tree remains the old-theme base. A temporary inert DOM/CSS/SVG clone is scoped with `[data-theme=next]` and revealed using CSS `clip-path: url(#unique-id)` referencing one dynamic SVG `clipPath/path`.
- One `requestAnimationFrame` loop writes only the path's `d`; no per-frame React state updates, layout reads, or DOM rebuilds.
- Real control `.value`, checked state, selection option, nested scroll offsets are copied. IDs and SVG/label references are re-keyed. No clone has React event listeners; the clone is inert and `aria-hidden`.
- Sticky/fixed clone boxes are positioned at their observed viewport bounds. Original content becomes temporarily inert and wheel/touch scrolling is frozen, preventing divergence. Resize, browser viewport changes, scroll and hidden document settle immediately. Cleanup restores focus, inert state and listeners.
- Completion commits the controlled theme synchronously before removing the clone. Unmount removes temporary DOM without updating the unmounted caller. Repeated calls during a run are ignored.
- Geometry and audio share the actual frame progress, with one-shot impact and fill transitions. The adapter uses one lazy AudioContext, quiet locally synthesized sources, no remote assets, no fixed timeout sequence.

## Honest snapshot limits

Supported: ordinary DOM, CSS variables, SVG, forms, nested scrolling, sticky/fixed in this document. The demo is tested at 390×844, 820×1180 and 1440×1000, including after page scroll.

**Canvas/WebGL, video, iframe/object/embed and open Shadow DOM cause a direct theme switch**. Closed shadow roots cannot be detected: consumers must opt out of animation for those surfaces. A static canvas snapshot cannot magically redraw a second theme and is deliberately not claimed as supported. CSS animations, animated GIFs, custom elements, native popovers/dialogs, portals outside the root, theme-specific DOM structure and virtualized items appearing during animation need application-specific handling. Pause animation or call `cancel()` before changing business state externally during the short capture. Body/ancestor-dependent CSS selectors should be converted to explicit `[data-theme]` scopes. The demo has no portals or closed shadow roots.

Reduced motion and unsupported SVG URL clipping use a direct switch; sound may give only a short feedback cue. Audio refusal/device loss never blocks theme changes.
