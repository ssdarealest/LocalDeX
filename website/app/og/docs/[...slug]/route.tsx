import { ImageResponse } from 'next/og';
import { notFound } from 'next/navigation';
import { source } from '@/lib/source';
import { appName, getPageImageUrl } from '@/lib/shared';

export const revalidate = false;

export function renderLocalDeXOgImage({
  title,
  description,
  eyebrow,
}: {
  title: string;
  description?: string;
  eyebrow: string;
}) {
  return new ImageResponse(
    (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          width: '100%',
          height: '100%',
          backgroundColor: '#050505',
          color: '#f4f4f5',
          padding: '48px',
        }}
      >
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            width: '100%',
            height: '100%',
            border: '1px solid rgba(255, 255, 255, 0.18)',
            backgroundColor: '#0b0b0b',
            padding: '48px',
          }}
        >
          {/* Top Eyebrow Row */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              width: '100%',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div
                style={{
                  width: '10px',
                  height: '10px',
                  backgroundColor: '#5aa9ff',
                }}
              />
              <span
                style={{
                  fontSize: '18px',
                  letterSpacing: '0.14em',
                  color: '#a1a1aa',
                  textTransform: 'uppercase',
                }}
              >
                {eyebrow}
              </span>
            </div>
            <span
              style={{
                fontSize: '18px',
                color: '#5aa9ff',
                letterSpacing: '0.08em',
              }}
            >
              127.0.0.1 // FOSS GPL-3.0
            </span>
          </div>

          {/* Main Title & Description */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div
              style={{
                fontSize: title.length > 32 ? '56px' : '68px',
                fontWeight: 600,
                lineHeight: 1.08,
                letterSpacing: '-0.03em',
                color: '#ffffff',
              }}
            >
              {title}
            </div>
            {description && (
              <div
                style={{
                  fontSize: '28px',
                  lineHeight: 1.4,
                  color: '#a1a1aa',
                  maxWidth: '940px',
                }}
              >
                {description}
              </div>
            )}
          </div>

          {/* Bottom Bar */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderTop: '1px solid rgba(255, 255, 255, 0.14)',
              paddingTop: '24px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <svg
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
              >
                <rect x="2" y="3" width="20" height="15" stroke="#ffffff" strokeWidth="1.75" />
                <rect x="5.5" y="6.5" width="8.5" height="6" stroke="#ffffff" strokeWidth="1.5" />
                <rect x="16" y="6.5" width="2.5" height="2.5" fill="#5aa9ff" />
                <path d="M7 21h10" stroke="#ffffff" strokeWidth="1.75" />
              </svg>
              <span style={{ fontSize: '24px', fontWeight: 600, color: '#ffffff' }}>
                {appName}
              </span>
            </div>
            <span style={{ fontSize: '18px', color: '#71717a' }}>
              Native Samsung DeX on Galaxy Screens
            </span>
          </div>
        </div>
      </div>
    ),
    {
      width: 1200,
      height: 630,
    },
  );
}

export async function GET(_req: Request, { params }: RouteContext<'/og/docs/[...slug]'>) {
  const { slug } = await params;
  const page = source.getPage(slug.slice(0, -1));
  if (!page) notFound();

  return renderLocalDeXOgImage({
    title: page.data.title,
    description: page.data.description,
    eyebrow: `${appName} Documentation`,
  });
}

export function generateStaticParams() {
  return source.getPages().map((page) => ({
    lang: page.locale,
    slug: getPageImageUrl(page).segments,
  }));
}
