import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { BrandWordmark } from '@/components/brand-mark';
import { releasesUrl, repoUrl } from './shared';

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: <BrandWordmark />,
      url: '/',
    },
    links: [
      {
        text: 'Docs',
        url: '/docs',
        active: 'nested-url',
      },
      {
        text: 'Architecture',
        url: '/docs/technical',
        active: 'url',
      },
      {
        text: 'Releases',
        url: releasesUrl,
        external: true,
      },
    ],
    githubUrl: repoUrl,
  };
}
