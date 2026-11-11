import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { useLiquidThemeReveal } from "./liquid/useLiquidThemeReveal";
import { WaterAudio } from "./liquid/audio";
import type { Theme } from "./liquid/geometry";
import { Moon, Sun, ArrowDown, Heart, Music2 } from "lucide-react";
import "./style.css";
const load = <T,>(key: string, fallback: T): T => {
    try {
        return JSON.parse(localStorage.getItem(key) ?? "null") ?? fallback;
    } catch {
        return fallback;
    }
};
const save = (key: string, value: unknown) => {
    try {
        localStorage.setItem(key, JSON.stringify(value));
    } catch {}
};
const topics = [
    {
        tag: "技术",
        title: "把复杂留给实现，把顺手留给日常",
        text: "一个原生客户端，一点流动的灵感。保留阅读位置，也保留每一次认真写下的回复。",
        author: "Nodyssey",
        color: "orange",
        count: 24,
    },
    {
        tag: "分享",
        title: "今天的网络，依然值得探索",
        text: "线路、延迟与一份新的测评报告。交换经验，连接彼此。",
        author: "weishiair",
        color: "blue",
        count: 16,
    },
    {
        tag: "日常",
        title: "换一个主题，继续刚才的讨论",
        text: "不是一层颜色盖上来，而是另一套真实主题沿着水流慢慢出现。",
        author: "Liquid Lab",
        color: "teal",
        count: 8,
    },
];
function App() {
    const [theme, setTheme] = useState<Theme>(() =>
        document.documentElement.dataset.theme === "dark" ? "dark" : "light",
    );
    const [sound, setSound] = useState(() => load("liquid-sound", false));
    const [volume, setVolume] = useState(() =>
        Math.max(0, Math.min(1, load("liquid-volume", 0.25))),
    );
    const [duration, setDuration] = useState(2600);
    const [reduced, setReduced] = useState(false);
    const [filter, setFilter] = useState("全部");
    const [draft, setDraft] = useState("这段草稿在切换后仍然保留。");
    const [liked, setLiked] = useState(false);
    const [fault, setFault] = useState(false);
    const water = useRef<WaterAudio | null>(null);
    if (!water.current) water.current = new WaterAudio();
    useEffect(() => () => water.current?.dispose(), []);
    useEffect(() => {
        save("liquid-sound", sound);
        if (!sound) water.current?.stop(false);
    }, [sound]);
    useEffect(() => {
        save("liquid-volume", volume);
        if (!volume) water.current?.stop(false);
    }, [volume]);
    const { rootRef, toggle, cancel, previewSound } = useLiquidThemeReveal({
        theme,
        onThemeChange: (next) => {
            setTheme(next);
            document.documentElement.dataset.theme = next;
            try {
                localStorage.setItem("liquid-theme", next);
            } catch {}
        },
        durationMs: duration,
        soundEnabled: sound,
        volume,
        audio: fault ? undefined : water.current,
        reducedMotion: reduced || undefined,
    });
    return (
        <div ref={rootRef} className="app" data-theme={theme}>
            <header className="topbar">
                <a className="brand" href="#home">
                    <span className="brandmark">
                        n<span>·</span>
                    </span>
                    <span>
                        Nodyssey <small>LIQUID LAB</small>
                    </span>
                </a>
                <nav>
                    <a href="#feed">社区预览</a>
                    <a href="#settings">实验设置</a>
                    <a href="https://github.com/weishiair/nodyssey">
                        上游项目 ↗
                    </a>
                </nav>
                <button
                    className="theme-toggle"
                    aria-label="切换明暗主题"
                    onClick={(e) => toggle(e.currentTarget)}
                >
                    <span>
                        {theme === "light" ? (
                            <Moon size={18} />
                        ) : (
                            <Sun size={18} />
                        )}
                    </span>
                    <span>{theme === "light" ? "流入夜色" : "唤醒晨光"}</span>
                </button>
            </header>
            <main id="home">
                <section className="hero">
                    <div className="eyebrow">
                        <i /> A LITTLE MOTION. A NEW PERSPECTIVE.
                    </div>
                    <h1>
                        让主题，
                        <br />
                        <em>自然流动。</em>
                    </h1>
                    <p>
                        从一滴，到整个世界。
                        <br />
                        液体经过的地方，悄然换上另一种光线。
                    </p>
                    <div className="hero-actions">
                        <button
                            className="primary"
                            aria-label="体验液体切换"
                            onClick={(e) => toggle(e.currentTarget)}
                        >
                            体验液体切换 <ArrowDown size={16} />
                        </button>
                        <a href="#settings">调整音效与节奏 ↗</a>
                    </div>
                    <div className="hero-note">
                        <span>01 — POUR</span>
                        <span>02 — SPLASH</span>
                        <span>03 — RISE</span>
                    </div>
                    <div className="liquid-art" aria-hidden="true">
                        <svg viewBox="0 0 340 340">
                            <path d="M169 20 C167 75 220 108 251 155 C303 234 252 312 173 316 C83 321 39 242 83 174 C119 119 165 78 169 20Z" />
                            <path
                                className="shine"
                                d="M128 171 Q94 220 113 251"
                            />
                            <circle cx="268" cy="105" r="15" />
                            <circle cx="83" cy="80" r="7" />
                        </svg>
                        <div className="art-label">
                            A new theme.
                            <br />
                            Not a new page.
                        </div>
                    </div>
                </section>
                <section id="feed" className="workspace">
                    <div className="section-title">
                        <div>
                            <span className="eyebrow">
                                YOUR COMMUNITY, IN A DIFFERENT LIGHT
                            </span>
                            <h2>继续，刚才的探索。</h2>
                        </div>
                        <span className="status">
                            <i /> 交互状态始终在线
                        </span>
                    </div>
                    <div className="columns">
                        <div>
                            <div className="filters">
                                {["全部", "技术", "分享", "日常"].map((x) => (
                                    <button
                                        aria-pressed={filter === x}
                                        className={
                                            filter === x ? "selected" : ""
                                        }
                                        key={x}
                                        onClick={() => setFilter(x)}
                                    >
                                        {x}
                                    </button>
                                ))}
                            </div>
                            <div className="topics">
                                {topics
                                    .filter(
                                        (x) =>
                                            filter === "全部" ||
                                            filter === x.tag,
                                    )
                                    .map((x, i) => (
                                        <article
                                            className="card topic"
                                            key={x.title}
                                        >
                                            <div className="topic-meta">
                                                <span
                                                    className={`avatar ${x.color}`}
                                                >
                                                    {x.author[0]}
                                                </span>
                                                <span>
                                                    {x.author} <b>Lv.{i + 2}</b>
                                                </span>
                                                <span className="tag">
                                                    {x.tag}
                                                </span>
                                            </div>
                                            <h3>{x.title}</h3>
                                            <p>{x.text}</p>
                                            <div className="topic-foot">
                                                <span>
                                                    12 分钟前 · {x.count} 条回复
                                                </span>
                                                <button
                                                    aria-label={`喜欢 ${x.title}`}
                                                    aria-pressed={liked}
                                                    onClick={() =>
                                                        setLiked(!liked)
                                                    }
                                                >
                                                    <Heart
                                                        size={14}
                                                        fill={
                                                            liked
                                                                ? "currentColor"
                                                                : "none"
                                                        }
                                                    />{" "}
                                                    {liked ? 19 : 18}
                                                </button>
                                            </div>
                                        </article>
                                    ))}
                            </div>
                            <article className="card composer">
                                <div className="card-heading">
                                    <h3>写下你的想法</h3>
                                    <span>草稿保留测试</span>
                                </div>
                                <textarea
                                    aria-label="回复草稿"
                                    value={draft}
                                    onChange={(e) => setDraft(e.target.value)}
                                />
                                <div className="composer-foot">
                                    <small>
                                        {draft.length} 字 · 仅本次会话
                                    </small>
                                    <button onClick={() => setDraft("")}>
                                        清空草稿
                                    </button>
                                </div>
                            </article>
                        </div>
                        <aside>
                            <article className="card chart">
                                <div className="card-heading">
                                    <h3>社区脉搏</h3>
                                    <span className="live">LIVE</span>
                                </div>
                                <div className="chart-value">
                                    2,486 <small>↑ 12.8%</small>
                                </div>
                                <svg
                                    viewBox="0 0 320 115"
                                    role="img"
                                    aria-label="社区活跃趋势"
                                >
                                    <path
                                        className="chart-grid"
                                        d="M0 25H320M0 65H320M0 105H320"
                                    />
                                    <path
                                        className="chart-fill"
                                        d="M0 95Q20 90 38 75T75 82T115 57T155 65T195 35T235 46T275 19T320 8V115H0Z"
                                    />
                                    <path
                                        className="chart-line"
                                        d="M0 95Q20 90 38 75T75 82T115 57T155 65T195 35T235 46T275 19T320 8"
                                    />
                                </svg>
                                <div className="chart-labels">
                                    <span>MON</span>
                                    <span>THU</span>
                                    <span>SUN</span>
                                </div>
                            </article>
                            <article className="card details">
                                <span className="eyebrow">
                                    UNDER THE SURFACE
                                </span>
                                <h3>
                                    不改变内容，
                                    <br />
                                    只改变光。
                                </h3>
                                <p>
                                    动态 SVG 联合轮廓
                                    <br />
                                    单一动画时钟
                                    <br />
                                    本地合成音效
                                    <br />
                                    尊重减少动态效果
                                </p>
                                <span className="pill">DOM / CSS / SVG</span>
                            </article>
                        </aside>
                    </div>
                </section>
                <section id="settings" className="settings card">
                    <div>
                        <span className="eyebrow">MAKE IT YOURS</span>
                        <h2>听见，流动的节奏。</h2>
                        <p>
                            默认静音。开启后，滴落、飞溅、流水与收尾跟随同一条时间线。
                        </p>
                    </div>
                    <div className="controls">
                        <label className="toggle-row">
                            <span>
                                液体音效 <small>在此设备上记忆</small>
                            </span>
                            <input
                                aria-label="液体音效"
                                type="checkbox"
                                checked={sound}
                                onChange={(e) => setSound(e.target.checked)}
                            />
                        </label>
                        <label className="range-row">
                            音量 <output>{Math.round(volume * 100)}%</output>
                            <input
                                aria-label="音效音量"
                                type="range"
                                min="0"
                                max="1"
                                step=".01"
                                value={volume}
                                onChange={(e) => setVolume(+e.target.value)}
                            />
                        </label>
                        <div className="control-buttons">
                            <button
                                onClick={() => void previewSound()}
                                disabled={!sound || !volume}
                            >
                                <Music2 size={14} /> 简短试听
                            </button>
                            <button onClick={cancel}>中断并完成切换</button>
                        </div>
                        <label className="range-row">
                            动画时长{" "}
                            <output>{(duration / 1000).toFixed(1)}s</output>
                            <input
                                aria-label="动画时长"
                                type="range"
                                min="1200"
                                max="5000"
                                step="100"
                                value={duration}
                                onChange={(e) => setDuration(+e.target.value)}
                            />
                        </label>
                        <label className="toggle-row">
                            <span>模拟减少动态效果</span>
                            <input
                                type="checkbox"
                                checked={reduced}
                                onChange={(e) => setReduced(e.target.checked)}
                            />
                        </label>
                        <label className="toggle-row">
                            <span>模拟音频不可用</span>
                            <input
                                type="checkbox"
                                checked={fault}
                                onChange={(e) => {
                                    water.current?.stop(false);
                                    setFault(e.target.checked);
                                }}
                            />
                        </label>
                    </div>
                </section>
                <footer>
                    <span>NODYSSEY · LIQUID THEME REVEAL</span>
                    <span>这是独立交互预览，不连接论坛账号或 API。</span>
                </footer>
            </main>
            <div className="fixed-status">
                <i />
                {theme === "light" ? "日间模式" : "夜间模式"}
                <span> · 状态不变，视角焕新</span>
            </div>
        </div>
    );
}
createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
);
