import Link from 'next/link';
import { ArrowUpRight, ImageIcon, Terminal } from 'lucide-react';
import { releasesUrl, repoUrl } from '@/lib/shared';

const FOOTNOTES = [
  {
    id: '1',
    label: 'Loopback ADB over TLS 1.3 (127.0.0.1) via NsdManager mDNS discovery.',
  },
  {
    id: '2',
    label: 'Self-healing WRITE_SECURE_SETTINGS keeps Wireless Debugging active after reboots.',
  },
  {
    id: '3',
    label: 'Bundled scrcpy-server v3.1 (SHA-256 verified) spawns --new-display for One UI.',
  },
  {
    id: '4',
    label: 'Zero-copy MediaCodec (KEY_LOW_LATENCY = 1) + Trackpad, Touch, S-Pen, Mouse & Gamepad.',
  },
];

const MECHANICS = [
  {
    num: '01',
    eyebrow: 'LOOPBACK TLS 1.3',
    title: 'Zero-PC Wireless Pairing',
    body: 'Discovers _adb-tls-pairing._tcp and _adb-tls-connect._tcp on localhost. Submit your 6-digit PIN from an inline notification reply without leaving Developer Options.',
  },
  {
    num: '02',
    eyebrow: 'SELF-HEALING ADB',
    title: 'Survives Reboots & Wi-Fi Switches',
    body: 'Automatically grants pm grant org.localdex android.permission.WRITE_SECURE_SETTINGS on pairing so LocalDeX silently restores adb_wifi_enabled = 1 on launch.',
  },
  {
    num: '03',
    eyebrow: 'VIRTUAL DISPLAY',
    title: 'Native One UI DeX + Freeform',
    body: 'Deploys bundled scrcpy-server v3.1 to /data/local/tmp/ and launches --new-display=WxH/DPI, triggering Samsung DeX with movable, edge-resizable windows.',
  },
  {
    num: '04',
    eyebrow: 'VIDEO & AUDIO',
    title: 'Hardware Low-Latency Pipeline',
    body: 'Decodes H.264 / H.265 straight onto a SurfaceView with KEY_LOW_LATENCY = 1. Speakers play DeX audio natively by default, with optional OPUS/AAC/RAW loopback.',
  },
];

const INPUT_MATRIX = [
  {
    mode: 'Virtual Trackpad',
    actions: '1-finger glide (1.35×), 1-finger tap left-click, 2-finger vertical scroll, 2-finger tap right-click',
    notes: 'Default mode inside DexViewerActivity',
  },
  {
    mode: 'Direct Touch',
    actions: 'Scaled multi-touch coordinates preserving pointer IDs and touch pressure',
    notes: 'Toggle instantly via Floating Pill',
  },
  {
    mode: 'S-Pen & Stylus',
    actions: 'Direct precision tip click, 4096-level hardware pressure, air hover cursor, side button / eraser right-click',
    notes: 'Auto-detected regardless of pill mode',
  },
  {
    mode: 'Hardware Mouse',
    actions: 'Left / right / middle clicks, H/V scroll wheel, plus Android Pointer Capture lock (AXIS_RELATIVE_X/Y)',
    notes: 'Back releases Pointer Capture first',
  },
  {
    mode: 'Gamepad & Keyboard',
    actions: 'Left stick cursor gliding, right stick scroll, D-pad hat arrows, Button A click, full KeyEvent forwarding',
    notes: 'System Back forwards to DeX Display #N',
  },
];

const DISPLAY_PRESETS = [
  { name: '1080p Standard (16:9)', spec: '1920 × 1080 / 240 DPI', viewport: '1280 × 720 dp', bitrate: '16 Mbps' },
  { name: 'Native Aspect', spec: '2176 × 1812 / 360 DPI', viewport: '967 × 805 dp', bitrate: '20 Mbps' },
  { name: 'Galaxy Z Fold (Inner)', spec: '2176 × 1812 / 360 DPI', viewport: '967 × 805 dp', bitrate: '24 Mbps' },
  { name: 'Galaxy Z Fold (Cover)', spec: '2316 × 904 / 380 DPI', viewport: '975 × 380 dp', bitrate: '18 Mbps' },
  { name: '4:3 Productivity', spec: '1920 × 1440 / 240 DPI', viewport: '1280 × 960 dp', bitrate: '18 Mbps' },
  { name: 'Ultrawide (21:9)', spec: '2560 × 1080 / 280 DPI', viewport: '1462 × 617 dp', bitrate: '24 Mbps' },
];

const DEVICE_FAMILIES = [
  {
    family: 'Galaxy S Series',
    models: 'S25, S24, S23, S22, S21, S20, S10 families (Ultra, Plus, FE)',
    support: 'Full Samsung DeX',
  },
  {
    family: 'Galaxy Z Fold Series',
    models: 'Z Fold 6, Special Edition, Z Fold 5 / 4 / 3 / 2',
    support: 'Full DeX + Flex Posture',
  },
  {
    family: 'Galaxy Note Series',
    models: 'Note 20 / Ultra, Note 10 / + / Lite, Note 9, Note 8',
    support: 'Full DeX + S-Pen Hover',
  },
  {
    family: 'Galaxy Tab S Series',
    models: 'Tab S10+ / Ultra, S9 / S8 / S7 / S6, FE, Active 4 Pro / 5',
    support: 'Full DeX + Pointer Lock',
  },
  {
    family: 'Z Flip, A/M/F & Non-Samsung',
    models: 'Z Flip 3–6, Galaxy A/M/F, Pixel, OnePlus, Xiaomi (Android 11+)',
    support: 'AOSP Freeform Fallback',
  },
];

export default function HomePage() {
  return (
    <main className="flex-1 w-full py-6 sm:py-8 px-4 sm:px-5">
      <div className="max-w-[1280px] mx-auto border border-fd-border bg-fd-background divide-y divide-fd-border">
        {/* Hero Split Section */}
        <section className="grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-fd-border">
          {/* Left Copy Column */}
          <div className="lg:col-span-6 p-6 sm:p-10 flex flex-col justify-between gap-8">
            <div>
              <span className="ld-eyebrow text-fd-primary block mb-4">
                LOCALDEX // ON-DEVICE VIRTUAL DISPLAY
              </span>
              <h1 className="text-[36px] sm:text-[46px] xl:text-[54px] font-[450] tracking-[-0.045em] leading-[1.04] text-fd-foreground text-balance">
                Run Samsung DeX on your Galaxy&apos;s own screen.
              </h1>
              <p className="mt-5 text-[16px] sm:text-[17.5px] leading-[1.58] tracking-[-0.01em] text-fd-muted-foreground max-w-[52ch]">
                No external monitor, no dock, no USB cable, no computer, and no root access.
                LocalDeX creates a trusted virtual display over localhost and streams your native
                DeX desktop fullscreen with hardware acceleration.
              </p>

              {/* CTA Row */}
              <div className="mt-7 flex flex-wrap items-center gap-2.5">
                <Link
                  href="/docs"
                  className="inline-flex items-center justify-center gap-2 h-9 px-4 bg-fd-foreground text-fd-background text-[13.5px] font-medium rounded-[2px] hover:opacity-85 transition-opacity"
                >
                  Read the Docs
                </Link>
                <Link
                  href="/docs/setup-pairing"
                  className="inline-flex items-center justify-center gap-2 h-9 px-4 border border-fd-border bg-fd-background text-fd-foreground text-[13.5px] font-medium rounded-[2px] hover:border-fd-foreground/40 transition-colors"
                >
                  4-Step Setup Guide
                </Link>
                <a
                  href={releasesUrl}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="inline-flex items-center gap-1 h-9 px-3 text-[13px] font-mono uppercase tracking-[0.06em] text-fd-muted-foreground hover:text-fd-foreground transition-colors"
                >
                  Download APK
                  <ArrowUpRight className="size-3.5" />
                </a>
              </div>
            </div>

            {/* Numbered Technical Footnotes */}
            <ol className="border-t border-fd-border pt-5 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
              {FOOTNOTES.map((note) => (
                <li key={note.id} className="grid grid-cols-[16px_1fr] gap-2 items-baseline">
                  <span className="font-mono text-[10.5px] text-fd-primary tabular-nums">
                    {note.id}
                  </span>
                  <span className="text-[12.5px] leading-[1.45] text-fd-muted-foreground">
                    {note.label}
                  </span>
                </li>
              ))}
            </ol>
          </div>

          {/* Right Hero Image Placeholder (full area edge-to-edge) */}
          <div className="lg:col-span-6 relative min-h-[360px] lg:min-h-full w-full bg-[var(--ld-plate)] flex flex-col items-center justify-center p-6 text-center overflow-hidden">
            <div className="ld-ticks" aria-hidden="true">
              <i />
              <i />
              <i />
              <i />
            </div>

            <div className="flex flex-col items-center gap-3 max-w-[320px]">
              <div className="size-10 border border-fd-border bg-fd-background flex items-center justify-center text-fd-muted-foreground">
                <ImageIcon className="size-5" />
              </div>
              <span className="ld-eyebrow text-fd-primary">
                HERO IMAGE PLACEHOLDER
              </span>
              <p className="font-mono text-[12px] text-fd-foreground">
                ~639 × 575 px (2×: 1280 × 1150 px)
              </p>
              <p className="text-[12.5px] text-fd-muted-foreground leading-relaxed">
                Fills full right hero column. Replace this area with{' '}
                <code className="font-mono text-fd-foreground">public/hero.png</code> using{' '}
                <code className="font-mono text-fd-foreground">object-cover</code>.
              </p>
            </div>
          </div>
        </section>

        {/* 4-Column Core Mechanics Grid */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 divide-y sm:divide-y-0 sm:divide-x divide-fd-border">
          {MECHANICS.map((item) => (
            <div
              key={item.num}
              className="group p-6 flex flex-col justify-between gap-4 relative hover:bg-fd-card/40 transition-colors"
            >
              <div>
                <div className="flex items-center justify-between gap-2 mb-3">
                  <span className="ld-eyebrow text-fd-muted-foreground">
                    <b className="text-fd-primary font-normal">{item.num}</b> / {item.eyebrow}
                  </span>
                </div>
                <h2 className="text-[16.5px] font-medium tracking-[-0.015em] text-fd-foreground">
                  {item.title}
                </h2>
                <p className="mt-2 text-[13.5px] leading-[1.55] text-fd-muted-foreground">
                  {item.body}
                </p>
              </div>
            </div>
          ))}
        </section>

        {/* Multi-Mode Input & Controls Matrix */}
        <section className="p-6 sm:p-10">
          <div className="flex flex-wrap items-end justify-between gap-4 mb-6">
            <div>
              <span className="ld-eyebrow text-fd-primary block mb-2">
                05 / INPUT ENGINE & FLOATING PILL
              </span>
              <h2 className="text-[24px] sm:text-[28px] font-medium tracking-[-0.03em] text-fd-foreground">
                Multi-mode input built for desktop workflows.
              </h2>
            </div>
            <Link
              href="/docs/viewer-and-controls"
              className="font-mono text-[11.5px] uppercase tracking-[0.06em] text-fd-muted-foreground hover:text-fd-foreground inline-flex items-center gap-1"
            >
              Explore controls <ArrowUpRight className="size-3.5" />
            </Link>
          </div>

          <div className="border border-fd-border divide-y divide-fd-border">
            {INPUT_MATRIX.map((row) => (
              <div
                key={row.mode}
                className="grid grid-cols-1 md:grid-cols-12 gap-2 md:gap-4 px-4 py-3.5 items-center hover:bg-fd-card/40 transition-colors"
              >
                <div className="md:col-span-3 font-medium text-[14px] text-fd-foreground">
                  {row.mode}
                </div>
                <div className="md:col-span-6 text-[13.5px] text-fd-muted-foreground">
                  {row.actions}
                </div>
                <div className="md:col-span-3 md:text-end font-mono text-[11px] text-fd-primary">
                  {row.notes}
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Presets & Supported Devices Split */}
        <section className="grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-fd-border">
          {/* Presets */}
          <div className="lg:col-span-6 p-6 sm:p-8">
            <div className="flex items-center justify-between gap-2 mb-5">
              <div>
                <span className="ld-eyebrow text-fd-primary block mb-1.5">
                  06 / DISPLAY PROFILES
                </span>
                <h2 className="text-[20px] font-medium tracking-[-0.025em]">
                  Built-in Presets & Custom Tuning
                </h2>
              </div>
              <Link
                href="/docs/display-profiles"
                className="font-mono text-[11px] uppercase tracking-[0.06em] text-fd-muted-foreground hover:text-fd-foreground"
              >
                Specs →
              </Link>
            </div>

            <div className="border border-fd-border divide-y divide-fd-border">
              {DISPLAY_PRESETS.map((preset) => (
                <div
                  key={preset.name}
                  className="px-3.5 py-2.5 flex flex-wrap items-center justify-between gap-2 text-[13px]"
                >
                  <div>
                    <div className="font-medium text-fd-foreground">{preset.name}</div>
                    <div className="font-mono text-[11px] text-fd-muted-foreground">
                      {preset.spec}
                    </div>
                  </div>
                  <div className="text-end font-mono text-[11px]">
                    <div className="text-fd-foreground">{preset.viewport}</div>
                    <div className="text-fd-muted-foreground">{preset.bitrate}</div>
                  </div>
                </div>
              ))}
            </div>
            <p className="mt-3 font-mono text-[11px] text-fd-muted-foreground">
              Custom editor: 120–480 DPI · 60 / 120 FPS · H.264 / H.265 · 8–32 Mbps · Optional OPUS/AAC/RAW
            </p>
          </div>

          {/* Supported Devices + PC Mirroring */}
          <div className="lg:col-span-6 p-6 sm:p-8 flex flex-col justify-between gap-6">
            <div>
              <div className="flex items-center justify-between gap-2 mb-5">
                <div>
                  <span className="ld-eyebrow text-fd-primary block mb-1.5">
                    07 / COMPATIBILITY MATRIX
                  </span>
                  <h2 className="text-[20px] font-medium tracking-[-0.025em]">
                    Supported Galaxy Devices
                  </h2>
                </div>
                <Link
                  href="/docs/installation"
                  className="font-mono text-[11px] uppercase tracking-[0.06em] text-fd-muted-foreground hover:text-fd-foreground"
                >
                  Details →
                </Link>
              </div>

              <div className="border border-fd-border divide-y divide-fd-border">
                {DEVICE_FAMILIES.map((item) => (
                  <div key={item.family} className="px-3.5 py-2.5 flex items-start justify-between gap-3">
                    <div>
                      <div className="text-[13.5px] font-medium text-fd-foreground">
                        {item.family}
                      </div>
                      <div className="text-[12px] text-fd-muted-foreground">{item.models}</div>
                    </div>
                    <span className="font-mono text-[10.5px] uppercase tracking-[0.05em] text-fd-primary border border-fd-border px-1.5 py-0.5 shrink-0">
                      {item.support}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* PC Mirroring Bar */}
            <div className="ld-plate p-4">
              <div className="flex items-center justify-between gap-2 mb-2">
                <span className="ld-eyebrow text-fd-muted-foreground flex items-center gap-1.5">
                  <Terminal className="size-3.5 text-fd-primary" />
                  SECONDARY DISPLAY MIRRORING FROM PC
                </span>
                <span className="font-mono text-[10.5px] text-fd-muted-foreground">
                  Display ID #N
                </span>
              </div>
              <code className="block font-mono text-[12.5px] text-fd-foreground">
                scrcpy --display-id=&lt;N&gt;
              </code>
            </div>
          </div>
        </section>

        {/* Technical Footer */}
        <footer className="px-6 sm:px-8 py-4 flex flex-wrap items-center justify-between gap-4 bg-fd-card/30">
          <div className="flex flex-wrap items-center gap-3 font-mono text-[11px] uppercase tracking-[0.07em] text-fd-muted-foreground">
            <span className="text-fd-foreground">LOCALDEX</span>
            <span>·</span>
            <span>GNU GPL-3.0</span>
            <span>·</span>
            <span>F-DROID & IZZYONDROID READY</span>
          </div>
          <nav className="flex flex-wrap items-center gap-5 font-mono text-[11px] uppercase tracking-[0.07em] text-fd-muted-foreground">
            <Link href="/docs" className="hover:text-fd-foreground transition-colors">
              Docs
            </Link>
            <Link href="/docs/technical" className="hover:text-fd-foreground transition-colors">
              Architecture
            </Link>
            <a href="/llms.txt" className="hover:text-fd-foreground transition-colors">
              llms.txt
            </a>
            <a href="/llms-full.txt" className="hover:text-fd-foreground transition-colors">
              llms-full.txt
            </a>
            <a
              href={repoUrl}
              target="_blank"
              rel="noreferrer noopener"
              className="hover:text-fd-foreground transition-colors"
            >
              GitHub
            </a>
          </nav>
        </footer>
      </div>
    </main>
  );
}
