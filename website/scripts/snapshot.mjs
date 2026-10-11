import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const website = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(process.argv[2] ?? resolve(website, '..'));
const project = JSON.parse(
  readFileSync(resolve(website, 'src/data/project.json'), 'utf8'),
);
for (const release of project.releases) {
  const commit = execFileSync(
    'git',
    ['-C', repo, 'rev-parse', `${release.tag}^{commit}`],
    { encoding: 'utf8' },
  ).trim();
  if (commit !== release.commit)
    throw new Error(`Tag ${release.tag} does not match its pinned commit`);
  const files = [
    ...release.guides.map((g) => g.source),
    ...(release.extraSources ?? []),
    'src/main/resources/data/wwpg_pinout/programs/yard/wwpg.lua',
    'src/main/resources/data/wwpg_pinout/programs/acceptance.lua',
  ];
  const manifest = {
    version: release.version,
    tag: release.tag,
    commit,
    files: {},
  };
  const destination = resolve(website, 'content/releases', release.version);
  for (const path of files) {
    const bytes = execFileSync('git', [
      '-C',
      repo,
      'show',
      `${commit}:${path}`,
    ]);
    const target = resolve(destination, path);
    if (existsSync(target) && !readFileSync(target).equals(bytes))
      throw new Error(`Refusing to overwrite published snapshot ${path}`);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, bytes);
    manifest.files[path] = createHash('sha256').update(bytes).digest('hex');
  }
  writeFileSync(
    resolve(destination, 'manifest.json'),
    JSON.stringify(manifest, null, 2) + '\n',
  );
  console.log(`Snapshotted ${release.version} at ${commit}`);
}
