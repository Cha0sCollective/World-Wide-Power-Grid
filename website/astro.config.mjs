import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import sitemap from '@astrojs/sitemap';
import project from './src/data/project.json' with { type: 'json' };
import { readFileSync } from 'node:fs';

const pages = [
  ['Overview', ''],
  ['Install WWPG', 'install/'],
  ['Configuration', 'configuration/'],
  ['Supported content', 'compatibility/'],
  ['Wiring and tools', 'wiring/'],
  ['Example worlds', 'example-worlds/'],
  ['Diagnostics and troubleshooting', 'troubleshooting/'],
  ['Lua computer control', 'lua/'],
  ['Addon development', 'developers/'],
];
export default defineConfig({
  site: project.origin,
  output: 'static',
  trailingSlash: 'always',
  // Keep client modules external so Vite can finish resolving dynamic-import
  // preloads before Astro renders the pages (including the Pagefind loader).
  vite: { build: { assetsInlineLimit: 0 } },
  integrations: [
    starlight({
      title: 'WWPG / Documentation',
      description: project.description,
      favicon: '/favicon.svg',
      disable404Route: true,
      defaultLocale: 'root',
      locales: { root: { label: 'English', lang: 'en' } },
      social: [
        { icon: 'github', label: 'Source code', href: project.repository },
      ],
      customCss: ['./src/styles/docs.css'],
      components: {
        Head: './src/components/docs/Head.astro',
        Search: './src/components/docs/Search.astro',
        ContentPanel: './src/components/docs/ContentPanel.astro',
        Footer: './src/components/docs/Footer.astro',
        PageTitle: './src/components/docs/PageTitle.astro',
        ThemeProvider: './src/components/docs/ThemeProvider.astro',
        ThemeSelect: './src/components/docs/SiteReturn.astro',
      },
      sidebar: [
        { label: 'Documentation home', link: '/docs/' },
        ...project.releases.map((release) => ({
          label: `${release.version}${release.version === project.currentRelease ? ' · current' : ' · archived'}`,
          collapsed: release.version !== project.currentRelease,
          items: [
            ...pages,
            ...(release.api ? [['API Workshop', 'workshop/']] : []),
          ].map(([label, slug]) => ({
            label,
            link: `/docs/${release.version}/${slug}`,
          })),
        })),
      ],
      head: [
        { tag: 'meta', attrs: { name: 'theme-color', content: '#0b0f10' } },
        ...(!project.publicationReady
          ? [
              {
                tag: 'meta',
                attrs: { name: 'robots', content: 'noindex, nofollow' },
              },
            ]
          : []),
        {
          tag: 'meta',
          attrs: {
            property: 'og:image',
            content: `${project.origin}/images/social.png`,
          },
        },
      ],
    }),
    sitemap({
      filter: (page) => !page.includes('/404'),
      customPages: project.releases
        .filter((release) => release.api)
        .flatMap((release) => {
          const manifest = JSON.parse(
            readFileSync(
              new URL(
                `./content/releases/${release.version}/api-manifest.json`,
                import.meta.url,
              ),
              'utf8',
            ),
          );
          return Object.keys(manifest.files)
            .filter(
              (file) =>
                file.endsWith('.html') && !file.endsWith('/search.html'),
            )
            .map(
              (file) =>
                `${project.origin}/api/${release.version}/${file.endsWith('/index.html') ? file.slice(0, -10) : file}`,
            );
        }),
    }),
  ],
});
