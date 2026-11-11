import { expect, test } from "@playwright/test";
for (const viewport of [
    { width: 1440, height: 1000 },
    { width: 390, height: 844 },
    { width: 820, height: 1180 },
]) {
    test(`bidirectional reveal and preservation ${viewport.width}`, async ({
        page,
    }) => {
        await page.setViewportSize(viewport);
        await page.goto("/");
        await page.getByLabel("回复草稿").fill("form state survives 123");
        await page.getByRole("button", { name: "技术", exact: true }).click();
        await page.evaluate(() => scrollTo(0, 450));
        const y = await page.evaluate(() => scrollY);
        const button = page.getByRole("button", { name: "切换明暗主题" });
        await button.click();
        await expect(page.locator("[data-liquid-overlay]")).toHaveCount(1);
        await page.waitForTimeout(1050);
        const alignment = await page.evaluate(() => {
            const real = document
                .querySelector(".app > .topbar")!
                .getBoundingClientRect();
            const cloned = document
                .querySelector("[data-liquid-overlay] .topbar")!
                .getBoundingClientRect();
            const realFixed = document
                .querySelector(".app > .fixed-status")!
                .getBoundingClientRect();
            const copyFixed = document
                .querySelector("[data-liquid-overlay] .fixed-status")!
                .getBoundingClientRect();
            const ids = [...document.querySelectorAll("[id]")].map((x) => x.id);
            return {
                dx: real.x - cloned.x,
                dy: real.y - cloned.y,
                dw: real.width - cloned.width,
                df: realFixed.y - copyFixed.y,
                dfx: realFixed.x - copyFixed.x,
                dfw: realFixed.width - copyFixed.width,
                unique: new Set(ids).size === ids.length,
                aria: document
                    .querySelector("[data-liquid-overlay]")!
                    .getAttribute("aria-hidden"),
                path: document
                    .querySelector("clipPath path")!
                    .getAttribute("d"),
            };
        });
        expect(Math.abs(alignment.dx)).toBeLessThan(1);
        expect(Math.abs(alignment.dy)).toBeLessThan(1);
        expect(Math.abs(alignment.dw)).toBeLessThan(1);
        expect(Math.abs(alignment.df)).toBeLessThan(1);
        expect(Math.abs(alignment.dfx)).toBeLessThan(1);
        expect(Math.abs(alignment.dfw)).toBeLessThan(1);
        expect(alignment.unique).toBe(true);
        expect(alignment.aria).toBe("true");
        expect(alignment.path).toContain("a");
        await page.screenshot({
            path: `test-results/reveal-${viewport.width}.png`,
        });
        await expect(page.locator("[data-liquid-overlay]")).toHaveCount(0, {
            timeout: 6000,
        });
        await expect(page.locator(".app").first()).toHaveAttribute(
            "data-theme",
            "dark",
        );
        await expect(page.getByLabel("回复草稿")).toHaveValue(
            "form state survives 123",
        );
        await expect(
            page.getByRole("button", { name: "技术", exact: true }),
        ).toHaveAttribute("aria-pressed", "true");
        expect(await page.evaluate(() => scrollY)).toBe(y);
        await page.reload();
        await expect(page.locator("html")).toHaveAttribute(
            "data-theme",
            "dark",
        );
        await button.click();
        await expect(page.locator("[data-liquid-overlay]")).toHaveCount(0, {
            timeout: 6000,
        });
        await expect(page.locator(".app").first()).toHaveAttribute(
            "data-theme",
            "light",
        );
    });
}
test("repeated click, resize and background interruption settle consistently", async ({
    page,
}) => {
    await page.goto("/");
    await page.getByRole("button", { name: "切换明暗主题" }).click();
    await page.evaluate(() =>
        document.querySelector<HTMLButtonElement>(".theme-toggle")!.click(),
    );
    await expect(page.locator("[data-liquid-overlay]")).toHaveCount(1);
    await page.setViewportSize({ width: 800, height: 900 });
    await expect(page.locator("[data-liquid-overlay]")).toHaveCount(0);
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await page.getByRole("button", { name: "切换明暗主题" }).click();
    await page.evaluate(() => {
        Object.defineProperty(document, "hidden", {
            configurable: true,
            value: true,
        });
        document.dispatchEvent(new Event("visibilitychange"));
    });
    await expect(page.locator("[data-liquid-overlay]")).toHaveCount(0);
    await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
    expect(
        await page.locator(".app").evaluate((el) => (el as HTMLElement).inert),
    ).toBe(false);
});
test("reduced motion and unsupported live surfaces switch without overlay", async ({
    page,
}) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/");
    await page.getByRole("button", { name: "切换明暗主题" }).click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect(page.locator("[data-liquid-overlay]")).toHaveCount(0);
    await page.emulateMedia({ reducedMotion: "no-preference" });
    await page.evaluate(() =>
        document
            .querySelector(".app")!
            .append(document.createElement("canvas")),
    );
    await page.getByRole("button", { name: "切换明暗主题" }).click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
    await expect(page.locator("[data-liquid-overlay]")).toHaveCount(0);
});
test("sound defaults off, remembers volume and failures do not block theme", async ({
    page,
}) => {
    await page.goto("/");
    await expect(
        page.getByRole("checkbox", { name: "液体音效", exact: true }),
    ).not.toBeChecked();
    await page.getByRole("checkbox", { name: "液体音效", exact: true }).check();
    await page.getByLabel("音效音量").fill("0.42");
    await page.getByRole("button", { name: "简短试听" }).click();
    await page.reload();
    await expect(
        page.getByRole("checkbox", { name: "液体音效", exact: true }),
    ).toBeChecked();
    await expect(page.getByLabel("音效音量")).toHaveValue("0.42");
    await page.evaluate(() => {
        window.AudioContext = class {
            constructor() {
                throw Error("no output device");
            }
        } as unknown as typeof AudioContext;
    });
    await page.getByRole("button", { name: "切换明暗主题" }).click();
    await expect(page.locator("[data-liquid-overlay]")).toHaveCount(0, {
        timeout: 6000,
    });
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
});
