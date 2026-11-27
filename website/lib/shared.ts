import { createGetUrl } from 'fumadocs-core/source';

export const appName = 'LocalDeX';
export const appDescription =
  'Run Samsung DeX natively on your Galaxy device screen — no dock, no cable, no computer, no root.';
export const docsRoute = '/docs';
export const docsImageRoute = '/og/docs';

export const gitConfig = {
  user: 'ssdarealest',
  repo: 'LocalDeX',
  branch: 'main',
};

export const repoUrl = `https://github.com/${gitConfig.user}/${gitConfig.repo}`;
export const releasesUrl = `${repoUrl}/releases`;

const getImageUrl = createGetUrl(docsImageRoute);

export function getPageImageUrl(page: { slugs: string[]; locale?: string }) {
  const segments = [...page.slugs, 'image.png'];

  return { segments, url: getImageUrl(segments, page.locale) };
}
