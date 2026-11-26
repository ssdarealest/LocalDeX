import type { SVGProps } from 'react';

export function LocalDeXMark(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      className="size-5 shrink-0"
      {...props}
    >
      {/* Outer virtual display frame */}
      <rect
        x="2"
        y="3"
        width="20"
        height="15"
        stroke="currentColor"
        strokeWidth="1.75"
      />
      {/* Freeform window inside display */}
      <rect
        x="5.5"
        y="6.5"
        width="8.5"
        height="6"
        stroke="currentColor"
        strokeWidth="1.5"
      />
      {/* Loopback / active indicator accent */}
      <rect x="16" y="6.5" width="2.5" height="2.5" fill="var(--ld-accent, #5aa9ff)" />
      {/* Base rail */}
      <path d="M7 21h10" stroke="currentColor" strokeWidth="1.75" />
    </svg>
  );
}

export function BrandWordmark({ compact = false }: { compact?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5 tracking-[-0.025em] font-medium text-[15px]">
      <LocalDeXMark />
      {!compact && (
        <span className="inline-flex items-baseline gap-1.5">
          <span>LocalDeX</span>
          <span className="font-mono text-[10px] uppercase tracking-[0.08em] text-fd-muted-foreground border border-fd-border px-1 py-0.5 leading-none">
            v1.0.0
          </span>
        </span>
      )}
    </span>
  );
}
