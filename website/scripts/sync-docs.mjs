#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const websiteRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(websiteRoot, '..');
const docsDir = path.join(repoRoot, 'docs');
const guideDir = path.join(docsDir, 'guide');
const targetDocsDir = path.join(websiteRoot, 'content', 'docs');

const isCheckMode = process.argv.includes('--check');

if (!fs.existsSync(docsDir) || !fs.existsSync(guideDir)) {
  console.log('Skipping docs sync: ../docs directory not found (standalone website build).');
  process.exit(0);
}

const PAGE_ICONS = {
  index: 'MonitorSmartphone',
  installation: 'Download',
  'setup-pairing': 'ShieldCheck',
  'display-profiles': 'SlidersHorizontal',
  'viewer-and-controls': 'MousePointerClick',
  'foldables-and-quick-settings': 'Smartphone',
  troubleshooting: 'Wrench',
  technical: 'Cpu',
  'building-from-source': 'Terminal',
};

const KNOWN_SLUGS = new Set(Object.keys(PAGE_ICONS));

function splitFrontmatter(raw) {
  const match = raw.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$/);
  if (!match) {
    return { frontmatter: {}, body: raw };
  }
  const frontmatter = {};
  for (const line of match[1].split(/\r?\n/)) {
    const kv = line.match(/^([A-Za-z0-9_-]+):\s*(.*)$/);
    if (kv) {
      frontmatter[kv[1]] = kv[2];
    }
  }
  return { frontmatter, body: match[2] };
}

function buildFrontmatter(slug, frontmatter) {
  const title = frontmatter.title ?? slug;
  const description = frontmatter.description ?? '';
  const icon = frontmatter.icon ?? PAGE_ICONS[slug];
  const lines = ['---', `title: ${title}`];
  if (description) lines.push(`description: ${description}`);
  if (icon) lines.push(`icon: ${icon}`);
  lines.push('---', '');
  return lines.join('\n');
}

function rewriteLinksAndFences(markdown) {
  // Rewrite markdown links inside docs to Next/Fumadocs /docs/<slug> routes.
  let out = markdown
    .replace(/\(\.\.\/TECHNICAL\.md(#[^)]+)?\)/g, (_, hash = '') => `(/docs/technical${hash})`)
    .replace(/\(\.\/([a-z0-9-]+)\.md(#[^)]+)?\)/g, (_, slug, hash = '') =>
      KNOWN_SLUGS.has(slug) ? `(/docs/${slug}${hash})` : `(./${slug}.md${hash})`
    )
    .replace(/\[([^\]]+)\]\(\.\.\/AGENTS\.md\)/g, '[$1](https://github.com/ssdarealest/LocalDeX/blob/main/AGENTS.md)')
    .replace(/\[([^\]]+)\]\(\.\.\/PENDING\.md\)/g, '[$1](https://github.com/ssdarealest/LocalDeX/blob/main/PENDING.md)');

  // Split by fenced code blocks so prose transforms never touch code blocks.
  const segments = out.split(/(```[^\n]*\r?\n[\s\S]*?```)/g);
  for (let i = 0; i < segments.length; i++) {
    if (i % 2 === 1) {
      // Code fence: add `text` info string to bare ``` blocks for crisp Shiki rendering.
      segments[i] = segments[i].replace(/^```\r?\n/, '```text\n');
    } else {
      // Convert single-paragraph GFM blockquotes (`> **Title:** body`) into `<Callout>`.
      segments[i] = segments[i].replace(
        /^>\s*\*\*([^:*\n]+):?\*\*\s*(.+)$/gm,
        (_, title, body) => `<Callout type="info" title="${title.trim()}">\n${body.trim()}\n</Callout>`
      );
    }
  }
  return segments.join('');
}

function enhanceIndexMdx(body) {
  // Render numbered session steps inside Fumadocs <Steps> and doc map inside <Cards>.
  let out = body.replace(
    /(## How a Session Works\r?\n\r?\n)([\s\S]*?)(\r?\n## Documentation Map)/,
    (_, heading, stepsBlock, nextHeading) => {
      const steps = [...stepsBlock.matchAll(/^\d+\.\s+\*\*([^:*]+):\*\*\s*(.+)$/gm)].map(
        ([, title, desc]) => `<Step>\n### ${title.trim()}\n${desc.trim()}\n</Step>`
      );
      if (steps.length === 0) return _;
      return `${heading}<Steps>\n${steps.join('\n\n')}\n</Steps>\n${nextHeading}`;
    }
  );

  out = out.replace(
    /(## Documentation Map\r?\n\r?\n)([\s\S]*)$/,
    (_, _heading, listBlock) => {
      const cards = [...listBlock.matchAll(/^-\s+\*\*\[([^\]]+)\]\(([^)]+)\):\*\*\s*(.+)$/gm)].map(
        ([, title, href, desc]) =>
          `  <Card\n    title="${title.replace(/"/g, '&quot;')}"\n    href="${href}"\n    description="${desc.trim().replace(/"/g, '&quot;')}"\n  />`
      );
      if (cards.length === 0) return _;
      return `## Explore the Guide\n\n<Cards>\n${cards.join('\n')}\n</Cards>\n`;
    }
  );

  return out;
}

function enhanceTroubleshootingMdx(body) {
  return body.replace(
    /(## Wireless Debugging & Pairing\r?\n\r?\n)([\s\S]*?)(\r?\n## Display & Window Behavior)/,
    (_, heading, faqBlock, nextHeading) => {
      const sections = faqBlock.trim().split(/\r?\n(?=### )/);
      const accordions = sections
        .map((sec) => {
          const match = sec.match(/^###\s+(.+?)\r?\n\r?\n([\s\S]+)$/);
          if (!match) return null;
          const [, rawTitle, content] = match;
          const id = rawTitle
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-|-$/g, '')
            .slice(0, 48);
          const safeTitle = rawTitle.replace(/"/g, "'");
          const indented = content
            .trim()
            .split(/\r?\n/)
            .map((line) => (line ? `    ${line}` : ''))
            .join('\n');
          return `  <Accordion title="${safeTitle}" id="${id}">\n${indented}\n  </Accordion>`;
        })
        .filter(Boolean);
      if (accordions.length === 0) return _;
      return `${heading}<Accordions>\n${accordions.join('\n\n')}\n</Accordions>\n${nextHeading}`;
    }
  );
}

function transformMarkdownToMdx(slug, rawContent) {
  const { frontmatter, body } = splitFrontmatter(rawContent);
  let normalizedBody = body.replace(/^\r?\n+/, '');

  if (slug === 'technical') {
    normalizedBody = normalizedBody.replace(/^#\s+LocalDeX\s+[—-]\s+Technical Breakdown\r?\n+/, '');
  }

  normalizedBody = rewriteLinksAndFences(normalizedBody);

  if (slug === 'index') {
    normalizedBody = enhanceIndexMdx(normalizedBody);
  } else if (slug === 'troubleshooting') {
    normalizedBody = enhanceTroubleshootingMdx(normalizedBody);
  }

  return `${buildFrontmatter(slug, frontmatter)}\n${normalizedBody.trimEnd()}\n`;
}

function generateMetaJson() {
  const guideMetaPath = path.join(guideDir, 'meta.json');
  const guideMeta = JSON.parse(fs.readFileSync(guideMetaPath, 'utf8'));
  const pages = [];
  for (const item of guideMeta.pages ?? []) {
    if (item === '---Development---') {
      pages.push('---Architecture & Development---');
      pages.push('technical');
    } else {
      pages.push(item);
    }
  }
  if (!pages.includes('technical')) {
    pages.push('technical');
  }
  return `${JSON.stringify({ title: 'Documentation', pages }, null, 2)}\n`;
}

const syncPairs = [
  { slug: 'technical', src: path.join(docsDir, 'TECHNICAL.md') },
  ...fs
    .readdirSync(guideDir)
    .filter((f) => f.endsWith('.md'))
    .sort()
    .map((file) => ({
      slug: path.basename(file, '.md'),
      src: path.join(guideDir, file),
    })),
];

fs.mkdirSync(targetDocsDir, { recursive: true });

let driftCount = 0;

for (const { slug, src } of syncPairs) {
  const dest = path.join(targetDocsDir, `${slug}.mdx`);
  const expected = transformMarkdownToMdx(slug, fs.readFileSync(src, 'utf8'));
  const current = fs.existsSync(dest) ? fs.readFileSync(dest, 'utf8') : null;

  if (current !== expected) {
    driftCount++;
    if (isCheckMode) {
      console.error(`Docs out of sync: ${path.relative(repoRoot, dest)} differs from ${path.relative(repoRoot, src)}`);
    } else {
      fs.writeFileSync(dest, expected, 'utf8');
      console.log(`Synced ${path.relative(repoRoot, src)} -> ${path.relative(repoRoot, dest)}`);
    }
  }
}

const metaDest = path.join(targetDocsDir, 'meta.json');
const expectedMeta = generateMetaJson();
const currentMeta = fs.existsSync(metaDest) ? fs.readFileSync(metaDest, 'utf8') : null;
if (currentMeta !== expectedMeta) {
  driftCount++;
  if (isCheckMode) {
    console.error('Docs out of sync: website/content/docs/meta.json differs from docs/guide/meta.json');
  } else {
    fs.writeFileSync(metaDest, expectedMeta, 'utf8');
    console.log('Synced docs/guide/meta.json -> website/content/docs/meta.json');
  }
}

if (isCheckMode && driftCount > 0) {
  console.error('Run `pnpm --dir website sync:docs` to synchronize website MDX docs.');
  process.exit(1);
}

if (driftCount === 0) {
  console.log('Website docs are up to date with docs/.');
}
