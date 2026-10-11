import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { JSDOM } from 'jsdom';
import {
  project,
  currentRelease,
  assetHref,
  downloadHref,
} from '../src/data/project.ts';
import { launchErrors } from '../scripts/check-launch.mjs';
import { initializeSearch } from '../src/scripts/search.ts';

const routes = [
  '/',
  '/downloads/',
  '/docs/',
  '/404.html',
  ...project.releases.flatMap((r) => [
    '/api/' + r.version + '/',
    ...[
      '',
      'install/',
      'configuration/',
      'compatibility/',
      'wiring/',
      'example-worlds/',
      'troubleshooting/',
      'lua/',
      'developers/',
    ].map((p) => `/docs/${r.version}/${p}`),
    ...(r.api ? [`/docs/${r.version}/workshop/`] : []),
  ]),
];
const builtPath = (route) =>
  resolve(
    'dist',
    route.replace(/^\//, '') + (route.endsWith('/') ? 'index.html' : ''),
  );
const html = (route) => readFileSync(builtPath(route), 'utf8');

function browserEnvironment(
  route,
  { reduced = false, writeText = async () => {} } = {},
) {
  const dom = new JSDOM(html(route), {
    url: project.origin + route,
    runScripts: 'outside-only',
    pretendToBeVisual: true,
  });
  const media = new Map();
  dom.window.matchMedia = (query) => {
    if (!media.has(query)) {
      const value = new dom.window.EventTarget();
      value.matches = query.includes('reduced-motion') ? reduced : false;
      value.change = (matches) => {
        value.matches = matches;
        value.dispatchEvent(new dom.window.Event('change'));
      };
      media.set(query, value);
    }
    return media.get(query);
  };
  Object.defineProperty(dom.window.navigator, 'clipboard', {
    value: { writeText },
    configurable: true,
  });
  for (const script of dom.window.document.querySelectorAll(
    'script[type="module"]',
  )) {
    const source = script.src
      ? readFileSync(builtPath(new URL(script.src).pathname), 'utf8')
      : script.textContent;
    // These custom-page modules have no imports; keep their module scopes separate.
    dom.window.eval(`(() => { ${source}\n })()`);
  }
  return { dom, document: dom.window.document, media };
}

test('all routes have useful metadata, one primary heading, and valid local links and assets', () => {
  for (const route of routes) {
    const dom = new JSDOM(html(route), { url: project.origin + route });
    const doc = dom.window.document;
    assert.equal(doc.querySelectorAll('h1').length, 1, route);
    assert.equal(doc.querySelectorAll('link[rel=canonical]').length, 1, route);
    assert.ok(
      !html(route).includes('__VITE_PRELOAD__'),
      `${route}: unresolved Vite preload token`,
    );
    assert.ok(
      doc.title.includes('WWPG') || doc.title.includes('World-Wide Power Grid'),
      route,
    );
    assert.ok(
      doc.querySelector('meta[name=description]')?.content.length > 35,
      route,
    );
    assert.equal(
      doc.querySelector('meta[name=robots]')?.content,
      project.publicationReady ? undefined : 'noindex, nofollow',
      route,
    );
    assert.equal(
      doc.querySelector('link[rel=canonical]').href,
      project.origin + route,
      route,
    );
    assert.equal(
      doc.querySelector('meta[property="og:image"]').content,
      project.origin + '/images/social.png',
    );
    for (const node of doc.querySelectorAll(
      'a[href],img[src],link[href],script[src]',
    )) {
      const value = node.getAttribute('href') ?? node.getAttribute('src');
      const url = new URL(value, project.origin + route);
      if (
        url.origin !== project.origin ||
        !['http:', 'https:'].includes(url.protocol)
      )
        continue;
      const target = builtPath(url.pathname);
      assert.ok(
        existsSync(target),
        `${route} has missing local destination ${value}`,
      );
      if (node.tagName === 'SCRIPT' && url.pathname.endsWith('.js'))
        assert.ok(
          !readFileSync(target, 'utf8').includes('__VITE_PRELOAD__'),
          `${route}: unresolved external preload token`,
        );
      if (url.hash && target.endsWith('.html')) {
        const targetDom = new JSDOM(readFileSync(target, 'utf8'));
        assert.ok(
          targetDom.window.document.getElementById(
            decodeURIComponent(url.hash.slice(1)),
          ),
          `${route}: missing anchor ${value}`,
        );
        targetDom.window.close();
      }
    }
    dom.window.close();
  }
});

test('release snapshots retain their exact selected source bytes and immutable revision', () => {
  for (const release of project.releases) {
    const base = resolve('content/releases', release.version);
    const manifest = JSON.parse(
      readFileSync(resolve(base, 'manifest.json'), 'utf8'),
    );
    assert.equal(manifest.commit, release.commit);
    assert.equal(manifest.tag, release.tag);
    for (const [path, expected] of Object.entries(manifest.files))
      assert.equal(
        createHash('sha256')
          .update(readFileSync(resolve(base, path)))
          .digest('hex'),
        expected,
        path,
      );
    assert.deepEqual(
      Object.keys(manifest.files).sort(),
      [
        ...release.guides.map((g) => g.source),
        ...(release.extraSources ?? []),
        'src/main/resources/data/wwpg_pinout/programs/yard/wwpg.lua',
        'src/main/resources/data/wwpg_pinout/programs/acceptance.lua',
      ].sort(),
    );
    assert.match(
      html(`/docs/${release.version}/install/`),
      new RegExp(release.commit),
    );
  }
});

test('downloads and requirements match the configured release; Modrinth is optional', () => {
  const doc = new JSDOM(html('/downloads/')).window.document;
  assert.ok(doc.querySelector(`a[href="${downloadHref()}"]`));
  assert.equal(doc.querySelector('#download-url').value, assetHref('jar'));
  for (const asset of ['worlds', 'checksums'])
    assert.ok(doc.querySelector(`a[href="${assetHref(asset)}"]`));
  for (const value of Object.values(currentRelease.requirements))
    assert.ok(doc.querySelector('.requirements').textContent.includes(value));
  for (const node of new JSDOM(html('/docs/')).window.document.querySelectorAll(
    '.docs-release-selector a',
  ))
    assert.ok(routes.includes(new URL(node.href, project.origin).pathname));
  assert.equal(doc.querySelector('a[href*="modrinth.com"]'), null);
});

test('released API and archived availability are distinct; launch still requires matching proof and acceptance', () => {
  const doc = new JSDOM(html(`/api/${currentRelease.version}/`)).window
    .document;
  assert.ok(
    doc.querySelector('main').textContent.includes(currentRelease.api.version),
  );
  for (const ref of currentRelease.api.references)
    assert.ok(
      doc.querySelector(`a[href="/api/${currentRelease.version}/${ref.id}/"]`),
    );
  assert.match(doc.querySelector('main').textContent, /implementation details/);
  for (const release of project.releases.filter((r) => !r.api))
    assert.match(
      html(`/api/${release.version}/`),
      /No public addon API in this release/,
    );
  assert.equal(
    launchErrors(project, null).length,
    project.publicationReady ? 1 : 2,
  );
  assert.equal(
    launchErrors({ ...project, publicationReady: false }, null).length,
    2,
  );
  const changed = { ...project, publicationReady: true };
  assert.ok(
    launchErrors(changed, {
      version: currentRelease.version,
      commit: currentRelease.commit,
      javadoc: true,
      example: true,
    }).length > 0,
  );
  const proof = {
    version: currentRelease.version,
    commit: currentRelease.commit,
    javadoc: true,
    example: true,
    manifestHash: createHash('sha256')
      .update(JSON.stringify(changed))
      .digest('hex'),
  };
  assert.deepEqual(launchErrors(changed, proof), []);
});

test('without JavaScript, core links, native chapter navigation, and the download URL are usable', () => {
  assert.equal(
    new JSDOM(html('/')).window.document.querySelector('[data-circuit]').dataset
      .paused,
    'true',
  );
  const downloads = new JSDOM(html('/downloads/')).window.document;
  assert.ok(downloads.querySelector('#download-url').readOnly);
  assert.ok(downloads.querySelector('.copy-button').hidden);
  assert.ok(downloads.querySelector('.menu-toggle').hidden);
  const guide = new JSDOM(html(`/docs/${currentRelease.version}/lua/`)).window
    .document;
  assert.ok(!guide.querySelector('.docs-search-fallback').hidden);
  assert.ok(guide.querySelector('a[href$="/install/"]'));
  assert.match(guide.querySelector('main').textContent, /setByte/);
  assert.equal(guide.documentElement.dataset.theme, 'dark');
});

test('the built download control copies the exact URL and announces success', async () => {
  let copied;
  const { dom, document } = browserEnvironment('/downloads/', {
    writeText: async (value) => {
      copied = value;
    },
  });
  document.querySelector('.copy-button').click();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(copied, assetHref('jar'));
  assert.equal(
    document.querySelector('.copy-feedback').textContent,
    'Download URL copied.',
  );
  dom.window.close();
});

test('clipboard failure selects the complete download URL and explains manual copy', async () => {
  const { dom, document } = browserEnvironment('/downloads/', {
    writeText: async () => {
      throw new Error('Clipboard denied');
    },
  });
  document.querySelector('.copy-button').click();
  await new Promise((resolve) => setTimeout(resolve, 0));
  const field = document.querySelector('#download-url');
  assert.equal(document.activeElement, field);
  assert.equal(field.selectionStart, 0);
  assert.equal(field.selectionEnd, field.value.length);
  assert.match(
    document.querySelector('.copy-feedback').textContent,
    /URL is selected/,
  );
  dom.window.close();
});

test('schematic motion follows reduced-motion changes and the pause control', () => {
  const { dom, document, media } = browserEnvironment('/', { reduced: true });
  const figure = document.querySelector('[data-circuit]');
  const control = document.querySelector('.motion-toggle');
  assert.equal(figure.dataset.paused, 'true');
  assert.equal(control.hidden, false);
  media.get('(prefers-reduced-motion: reduce)').change(false);
  assert.equal(figure.dataset.paused, 'false');
  control.click();
  assert.equal(figure.dataset.paused, 'true');
  assert.equal(control.getAttribute('aria-pressed'), 'true');
  control.click();
  assert.equal(figure.dataset.paused, 'false');
  media.get('(prefers-reduced-motion: reduce)').change(true);
  assert.equal(figure.dataset.paused, 'true');
  dom.window.close();
});

test('docs are indexed with exact release filters and Lua methods match frozen tested programs', () => {
  const doc = new JSDOM(html(`/docs/${currentRelease.version}/lua/`)).window
    .document;
  assert.ok(
    doc.querySelector(
      `[data-pagefind-filter="version:${currentRelease.version}"]`,
    ),
  );
  assert.ok(existsSync(resolve('dist/pagefind/pagefind.js')));
  const example = readFileSync(
    resolve(
      'content/releases',
      currentRelease.version,
      'src/main/resources/data/wwpg_pinout/programs/acceptance.lua',
    ),
    'utf8',
  );
  for (const method of [
    'pinLayout',
    'pinsConnected',
    'pinsVoltage',
    'comparePin',
    'connectPin',
    'disconnectPin',
    'setByte',
    'setPins',
  ]) {
    assert.ok(example.includes(`p.${method}`));
    assert.ok(doc.querySelector('main').textContent.includes(method));
  }
});

test('search sends the selected release filter and renders safe, version-labeled results', async () => {
  const dom = new JSDOM(html(`/docs/${currentRelease.version}/lua/`));
  const previous = globalThis.document;
  globalThis.document = dom.window.document;
  const input = document.querySelector('#docs-search-input');
  let calls = 0;
  initializeSearch(async () => ({
    search: async (term, options) => {
      calls++;
      assert.equal(term, 'pin');
      assert.equal(options.filters.version, currentRelease.version);
      return {
        results: [
          {
            data: async () => ({
              url: `/docs/${currentRelease.version}/lua/`,
              meta: {
                title: 'Lua computer control',
                version: currentRelease.version,
              },
              excerpt: 'Switch <mark>pins</mark>.',
            }),
          },
        ],
      };
    },
  }));
  input.value = 'pin';
  input.dispatchEvent(new dom.window.Event('input'));
  await new Promise((r) => setTimeout(r, 240));
  assert.equal(calls, 1);
  assert.match(
    document.querySelector('.docs-search-results').textContent,
    /Lua computer control/,
  );
  assert.match(
    document.querySelector('.docs-search-results').textContent,
    new RegExp(currentRelease.version.replaceAll('.', '\\.')),
  );
  assert.equal(
    document.querySelectorAll('.docs-search-results mark').length,
    0,
  );
  globalThis.document = previous;
  dom.window.close();
});

test('SDK and workshop downloads target the current public release and explain runtime requirements', () => {
  const doc = new JSDOM(html('/downloads/')).window.document;
  for (const asset of ['sdk', 'worlds'])
    assert.ok(doc.querySelector(`a[href="${assetHref(asset)}"]`), asset);
  assert.match(
    doc.querySelector('#worlds').textContent,
    /both example addon jars/,
  );
  assert.match(doc.querySelector('#worlds').textContent, /workshop-mods/);
  for (const filename of Object.keys(currentRelease.bundles.worlds.files))
    assert.ok(
      doc.querySelector('#worlds').textContent.includes(filename),
      filename,
    );
  const guide = new JSDOM(html(`/docs/${currentRelease.version}/developers/`))
    .window.document;
  assert.match(
    guide.querySelector('main').textContent,
    /compileOnly 'org.cha0scollective.wwpg:wwpg-api:1.0.0'/,
  );
  assert.match(
    guide.querySelector('main').textContent,
    /Do not register a second WWPG circuit/,
  );
  assert.match(
    guide.querySelector('main').textContent,
    /do not shade API classes/i,
  );
  assert.ok(guide.querySelector(`a[href="${assetHref('sdk')}"]`));
  assert.match(
    guide.querySelector('main').textContent,
    /maven\/wwpg-api-1.0.0-maven.zip/,
  );
});

test('current release pages link only to published top-level downloads, including projected snapshot guides', () => {
  const prefix = `${project.repository}/releases/download/${currentRelease.tag}/`;
  const filenames = new Set(Object.values(currentRelease.downloads));
  for (const route of routes) {
    const dom = new JSDOM(html(route), { url: project.origin + route });
    for (const link of dom.window.document.querySelectorAll('a[href]'))
      if (link.href.startsWith(prefix))
        assert.ok(
          filenames.has(link.href.slice(prefix.length)),
          `${route}: removed download ${link.href}`,
        );
    assert.ok(
      !html(route).includes('{{asset:'),
      `${route}: unresolved download placeholder`,
    );
    dom.window.close();
  }
});

test('generated references preserve released bytes, export only supported packages, and have working local destinations', () => {
  const parsed = new Map();
  const documentFor = (path) => {
    if (!parsed.has(path))
      parsed.set(path, new JSDOM(readFileSync(path, 'utf8')));
    return parsed.get(path).window.document;
  };
  for (const release of project.releases.filter((r) => r.api)) {
    const base = resolve('content/releases', release.version);
    const manifest = JSON.parse(
      readFileSync(resolve(base, 'api-manifest.json'), 'utf8'),
    );
    assert.equal(manifest.apiVersion, release.api.version);
    assert.equal(manifest.commit, release.commit);
    assert.deepEqual(manifest.packages, release.api.packages);
    assert.deepEqual(manifest.artifacts, release.api.artifacts);
    for (const reference of release.api.references) {
      const packages = readFileSync(
        resolve(base, 'java-reference', reference.id, 'element-list'),
        'utf8',
      )
        .trim()
        .split(/\r?\n/)
        .sort();
      assert.deepEqual(packages, [...reference.packages].sort());
    }
    for (const [file, expected] of Object.entries(manifest.files)) {
      assert.equal(
        createHash('sha256')
          .update(readFileSync(resolve(base, 'java-reference', file)))
          .digest('hex'),
        expected,
        file,
      );
      if (!file.endsWith('.html')) continue;
      const route = `/api/${release.version}/${file}`;
      const doc = documentFor(builtPath(route));
      assert.ok(doc.title.includes(`API ${release.api.version}`));
      assert.equal(
        doc.querySelector('meta[name=robots]')?.content,
        project.publicationReady ? undefined : 'noindex, nofollow',
      );
      for (const node of doc.querySelectorAll(
        'a[href],link[href],script[src],img[src]',
      )) {
        const value = node.getAttribute('href') ?? node.getAttribute('src');
        const url = new URL(value, project.origin + route);
        if (
          url.origin !== project.origin ||
          !['http:', 'https:'].includes(url.protocol)
        )
          continue;
        const target = builtPath(url.pathname);
        assert.ok(existsSync(target), `${route}: missing ${value}`);
        if (url.hash && target.endsWith('.html'))
          assert.ok(
            documentFor(target).getElementById(
              decodeURIComponent(url.hash.slice(1)),
            ),
            `${route}: missing anchor ${value}`,
          );
      }
    }
  }
  for (const dom of parsed.values()) dom.window.close();
});

test('search failure preserves chapter navigation and announces a useful fallback', async () => {
  const dom = new JSDOM(html(`/docs/${currentRelease.version}/install/`));
  const previous = globalThis.document;
  globalThis.document = dom.window.document;
  initializeSearch(async () => {
    throw new Error('Index unavailable');
  });
  const input = document.querySelector('#docs-search-input');
  input.value = 'transformer';
  input.dispatchEvent(new dom.window.Event('input'));
  await new Promise((r) => setTimeout(r, 240));
  assert.match(
    document.querySelector('#search-status').textContent,
    /Search is unavailable/,
  );
  assert.ok(document.querySelector('a[href$="/configuration/"]'));
  globalThis.document = previous;
  dom.window.close();
});
