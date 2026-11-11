import { defineConfig } from "@playwright/test";
export default defineConfig({
    testDir: "./tests",
    timeout: 30000,
    use: {
        baseURL: "http://127.0.0.1:4178",
        headless: true,
        launchOptions: {
            executablePath:
                process.env.CHROME_PATH ??
                (process.platform === "win32"
                    ? "C:/Program Files/Google/Chrome/Application/chrome.exe"
                    : undefined),
            args: ["--autoplay-policy=user-gesture-required"],
        },
    },
    webServer: {
        command: "npm run dev -- --port 4178 --strictPort",
        url: "http://127.0.0.1:4178",
        reuseExistingServer: true,
    },
    reporter: [["list"], ["html", { open: "never" }]],
});
