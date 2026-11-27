'use client';

import { useFetchSearch } from 'fumadocs-core/search/client';
import {
  SearchDialog,
  SearchDialogClose,
  SearchDialogContent,
  SearchDialogFooter,
  SearchDialogHeader,
  SearchDialogIcon,
  SearchDialogInput,
  SearchDialogList,
  SearchDialogOverlay,
  type SharedProps,
} from 'fumadocs-ui/components/dialog/search';
import { useI18n } from 'fumadocs-ui/contexts/i18n';

const QUICK_ITEMS = [
  {
    id: 'quick-setup',
    type: 'page' as const,
    url: '/docs/setup-pairing',
    content: 'Setup & Wireless Pairing — 4-step wizard & inline PIN entry',
  },
  {
    id: 'quick-profiles',
    type: 'page' as const,
    url: '/docs/display-profiles',
    content: 'Display Profiles — Presets, DPI, FPS, H.264/H.265 & Audio',
  },
  {
    id: 'quick-controls',
    type: 'page' as const,
    url: '/docs/viewer-and-controls',
    content: 'Viewer & Controls — Floating Pill, Trackpad, S-Pen & Mouse Lock',
  },
  {
    id: 'quick-technical',
    type: 'page' as const,
    url: '/docs/technical',
    content: 'Technical Breakdown — Loopback ADB TLS 1.3 & scrcpy-server',
  },
];

export function LocalSearchDialog(props: SharedProps) {
  const { locale } = useI18n();
  const searchState = useFetchSearch({
    api: '/api/search',
    locale,
  });

  return (
    <SearchDialog {...searchState} {...props}>
      <SearchDialogOverlay />
      <SearchDialogContent className="rounded-[2px] border border-fd-border">
        <SearchDialogHeader>
          <SearchDialogIcon />
          <SearchDialogInput placeholder="Search LocalDeX docs, ADB flags, gestures..." />
          <SearchDialogClose />
        </SearchDialogHeader>
        <SearchDialogList defaultItems={QUICK_ITEMS} />
        <SearchDialogFooter className="flex items-center justify-between text-[11px] font-mono uppercase tracking-[0.06em] text-fd-muted-foreground">
          <span>Local Orama Index</span>
          <a href="/llms.txt" className="hover:text-fd-foreground transition-colors">
            llms.txt
          </a>
        </SearchDialogFooter>
      </SearchDialogContent>
    </SearchDialog>
  );
}
