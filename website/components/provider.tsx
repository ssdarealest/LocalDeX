'use client';

import type { ReactNode } from 'react';
import { RootProvider } from 'fumadocs-ui/provider/next';
import { LocalSearchDialog } from './search';

export function Provider({ children }: { children: ReactNode }) {
  return (
    <RootProvider
      theme={{
        defaultTheme: 'dark',
        attribute: 'class',
      }}
      search={{
        enabled: true,
        SearchDialog: LocalSearchDialog,
      }}
    >
      {children}
    </RootProvider>
  );
}
