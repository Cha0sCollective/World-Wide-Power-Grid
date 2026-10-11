import {
  readFileSync,
  writeFileSync,
  mkdirSync,
  rmSync,
  existsSync,
} from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, dirname, posix, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const project = JSON.parse(
  readFileSync(resolve(root, 'src/data/project.json'), 'utf8'),
);
const generated = resolve(root, 'src/content/docs');
const apiOutput = resolve(root, 'public/api');
// This directory contains only derived content and is explicitly ignored.
if (!generated.startsWith(root + '/') && !generated.startsWith(root + '\\'))
  throw new Error('Invalid generated path');
rmSync(generated, { recursive: true, force: true });
rmSync(apiOutput, { recursive: true, force: true });
mkdirSync(generated, { recursive: true });

export function rewriteLinks(markdown, source, release, repository) {
  const rendered = new Map(release.guides.map((g) => [g.source, g.slug]));
  return markdown.replace(/\]\(([^\s)]+)\)/g, (whole, destination) => {
    if (/^(https?:|mailto:|#)/.test(destination)) return whole;
    const [path, fragment] = destination.split('#');
    const absolute = posix.normalize(posix.join(posix.dirname(source), path));
    const hash = fragment ? `#${fragment}` : '';
    const slug = rendered.get(absolute);
    if (slug) return `](/docs/${release.version}/${slug}/${hash})`;
    const kind =
      absolute.endsWith('/') || !posix.extname(absolute) ? 'tree' : 'blob';
    return `](${repository}/${kind}/${release.commit}/${absolute}${hash})`;
  });
}

for (const release of project.releases) {
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(release.version))
    throw new Error('Invalid release path');
  const base = resolve(root, 'content/releases', release.version);
  const manifest = JSON.parse(
    readFileSync(resolve(base, 'manifest.json'), 'utf8'),
  );
  if (manifest.commit !== release.commit || manifest.tag !== release.tag)
    throw new Error('Snapshot release mismatch');
  if (release.api) {
    const apiManifest = JSON.parse(
      readFileSync(resolve(base, 'api-manifest.json'), 'utf8'),
    );
    if (
      apiManifest.commit !== release.commit ||
      apiManifest.apiVersion !== release.api.version ||
      JSON.stringify(apiManifest.packages) !==
        JSON.stringify(release.api.packages)
    )
      throw new Error('API snapshot release or exported packages mismatch');
    const archive = resolve(base, 'java-reference');
    const editionOutput = resolve(apiOutput, release.version);
    for (const [file, expected] of Object.entries(apiManifest.files)) {
      const input = resolve(archive, file);
      const output = resolve(editionOutput, file);
      if (
        !input.startsWith(archive + sep) ||
        !output.startsWith(editionOutput + sep)
      )
        throw new Error('Invalid API snapshot path');
      const bytes = readFileSync(input);
      if (createHash('sha256').update(bytes).digest('hex') !== expected)
        throw new Error(`Changed API reference snapshot: ${file}`);
      mkdirSync(dirname(output), { recursive: true });
      if (file.endsWith('.html')) {
        const canonical = `${project.origin}/api/${release.version}/${file.endsWith('/index.html') ? file.slice(0, -10) : file}`;
        const metadata = `<meta name="description" content="WWPG ${release.version}, API ${release.api.version}: released public addon API reference."><link rel="canonical" href="${canonical}"><meta property="og:image" content="${project.origin}/images/social.png">${project.publicationReady ? '' : '<meta name="robots" content="noindex, nofollow">'}`;
        const navigation = `<nav aria-label="WWPG documentation" style="padding:14px 20px;background:#0b0f10;color:#e9ece5;font:14px Arial,sans-serif"><a style="color:#c5ddbe" href="/api/${release.version}/">WWPG ${release.version} / API ${release.api.version}</a> / <a style="color:#c5ddbe" href="/docs/${release.version}/developers/">Addon guide</a> / <a style="color:#c5ddbe" href="/docs/">Documentation editions</a></nav>`;
        writeFileSync(
          output,
          bytes
            .toString('utf8')
            .replace(
              /<title>(.*?)<\/title>/s,
              `<title>WWPG ${release.version} / API ${release.api.version} — $1</title>`,
            )
            .replace(
              '</head>',
              metadata +
                '<link rel="icon" type="image/svg+xml" href="/favicon.svg"><link rel="stylesheet" href="/styles/api.css"></head>',
            )
            .replace(
              /<body([^>]*)>/i,
              '<body$1 data-pagefind-ignore>' + navigation,
            ),
        );
      } else writeFileSync(output, bytes);
    }
  }
  const writePage = (slug, title, description, body) => {
    const target = resolve(
      generated,
      'docs',
      release.version,
      slug ? `${slug}.md` : 'index.md',
    );
    mkdirSync(dirname(target), { recursive: true });
    const frontmatter = `---\ntitle: ${JSON.stringify(title)}\ndescription: ${JSON.stringify(description)}\neditUrl: false\n---\n\n`;
    const edition = `:::note[Release ${release.version}]\nThese instructions apply to **WWPG ${release.version}**${release.api ? ` / API ${release.api.version}` : ''} and its pinned dependencies. [Choose a release](/docs/).\n:::\n\n`;
    writeFileSync(target, frontmatter + edition + body + '\n', 'utf8');
  };
  for (const guide of release.guides) {
    const bytes = readFileSync(resolve(base, guide.source));
    if (
      createHash('sha256').update(bytes).digest('hex') !==
      manifest.files[guide.source]
    )
      throw new Error(`Changed frozen source: ${guide.source}`);
    const sourceText = bytes.toString('utf8');
    if (guide.stopBefore && !sourceText.includes(guide.stopBefore))
      throw new Error('Guide selection boundary is missing');
    const body = rewriteLinks(
      (guide.stopBefore
        ? sourceText.split(guide.stopBefore)[0]
        : sourceText
      ).replace(/^# .+\r?\n/, ''),
      guide.source,
      release,
      project.repository,
    );
    writePage(
      guide.slug,
      guide.title,
      guide.description,
      body +
        `\n\n[Release-pinned source](${project.repository}/blob/${release.commit}/${guide.source})`,
    );
  }
  const prefix = `/docs/${release.version}/`;
  const requirements = Object.entries(release.requirements)
    .map(([name, version]) => `| ${name} | ${version} |`)
    .join('\n');
  const overview = `Connect Create: Electro Energetics and Power Grid in the same stationary factory, using their native equipment, wires, and controls.\n\n## Choose a starting point\n\n- [Install WWPG](${prefix}install/) — client and server setup, with the exact required versions.\n- [Configuration](${prefix}configuration/) — solver settings and platform requirements.\n- [Supported content](${prefix}compatibility/) — equipment and release boundaries.\n- [Wiring and native tools](${prefix}wiring/) — connect, measure, and repair.\n- [Explore the test yards](${prefix}example-worlds/) — hands-on examples with instructions and reset actions.\n- [Lua computer control](${prefix}lua/) — the optional Pinout integration.\n- [Addon development](${prefix}developers/) — ${release.api ? `API ${release.api.version} setup, lifecycle, and working examples` : 'public API availability'}.\n- [Diagnostics and troubleshooting](${prefix}troubleshooting/) — investigate an unexpected result.\n\n${release.api ? `- [Extension API Workshop](${prefix}workshop/) — the two addon examples in a playable save.\n\n` : ''}## Pinned dependencies\n\n| Dependency | Version |\n| --- | --- |\n${requirements}\n\nThe community modpack and WWPG are separately versioned. Follow the requirements for the release you install. The future Ch4oS player manual will cover the pack's own gameplay edition.\n\n[Back to WWPG](/)`;
  writePage(
    '',
    `${release.version} documentation`,
    'Release-specific installation, compatibility, scripting, and addon development for WWPG.',
    overview,
  );
  for (const [slug, title, description] of [
    [
      'configuration',
      'Configuration',
      'Configure the Power Grid solver and check the backend actually used.',
    ],
    [
      'troubleshooting',
      'Diagnostics and troubleshooting',
      'Find the source of unexpected circuit readings and report a reproducible problem.',
    ],
    [
      'lua',
      'Lua computer control',
      'Use the native Pinout peripheral to switch eight pins and read signed voltage in mixed circuits.',
    ],
    [
      'developers',
      'Addon development',
      'Released SDK setup, working examples, lifecycle, and compatibility for WWPG addon authors.',
    ],
  ]) {
    const file = resolve(root, 'content/guides', release.version, `${slug}.md`);
    if (!existsSync(file)) throw new Error(`Missing reviewed guide ${file}`);
    const body = readFileSync(file, 'utf8')
      .replaceAll('{{docs}}', prefix)
      .replaceAll('{{source}}', `${project.repository}/blob/${release.commit}`)
      .replaceAll('{{version}}', release.version);
    writePage(slug, title, description, body);
  }
}
console.log(`Prepared ${project.releases.length} release documentation set(s)`);
