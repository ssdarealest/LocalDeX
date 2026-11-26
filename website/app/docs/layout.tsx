import { source } from '@/lib/source';
import { DocsLayout } from '@/layouts/docs';
import { baseOptions } from '@/lib/layout.shared';

export default function Layout({ children }: LayoutProps<'/docs'>) {
  return (
    <DocsLayout
      tree={source.getPageTree()}
      {...baseOptions()}
      sidebar={{
        footer: (
          <div className="pt-2 flex items-center justify-between text-[11px] font-mono uppercase tracking-[0.06em] text-fd-muted-foreground">
            <span>GPL-3.0 FOSS</span>
            <a href="/llms.txt" className="hover:text-fd-foreground transition-colors">
              llms.txt
            </a>
          </div>
        ),
      }}
    >
      {children}
    </DocsLayout>
  );
}
