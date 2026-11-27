import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import { Provider } from '@/components/provider';
import { appDescription, appName } from '@/lib/shared';
import './global.css';

const geist = Geist({
  subsets: ['latin'],
  variable: '--font-geist-sans',
});

const geistMono = Geist_Mono({
  subsets: ['latin'],
  variable: '--font-geist-mono',
});

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'https://localdex.org'),
  title: {
    default: `${appName} — Run Samsung DeX natively on your Galaxy screen`,
    template: `%s | ${appName}`,
  },
  description: appDescription,
  openGraph: {
    title: `${appName} — Native On-Device Samsung DeX`,
    description: appDescription,
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: `${appName} — Native On-Device Samsung DeX`,
    description: appDescription,
  },
};

export default function Layout({ children }: LayoutProps<'/'>) {
  return (
    <html
      lang="en"
      className={`${geist.variable} ${geistMono.variable} ${geist.className}`}
      suppressHydrationWarning
    >
      <body className="flex flex-col min-h-screen font-sans antialiased">
        <Provider>{children}</Provider>
      </body>
    </html>
  );
}
