import { appDescription, appName } from '@/lib/shared';
import { renderLocalDeXOgImage } from './og/docs/[...slug]/route';

export const revalidate = false;
export const alt = `${appName} — Native On-Device Samsung DeX`;
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';

export default function OpenGraphImage() {
  return renderLocalDeXOgImage({
    title: 'Run Samsung DeX on your Galaxy screen.',
    description: appDescription,
    eyebrow: `${appName} v1.0.0`,
  });
}
