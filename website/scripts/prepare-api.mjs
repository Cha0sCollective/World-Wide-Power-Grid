import {
  readFileSync,
  writeFileSync,
  existsSync,
  mkdirSync,
  rmSync,
  readdirSync,
} from 'node:fs';
import { resolve, dirname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { assertFrozenManifest } from './frozen-manifest.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(root, process.argv[2] ?? '..');
const cachedAssets = process.argv[3] ? resolve(process.argv[3]) : null;
const project = JSON.parse(
  readFileSync(resolve(root, 'src/data/project.json'), 'utf8'),
);
const release = project.releases.find(
  (r) => r.version === project.currentRelease,
);
const api = release?.api;
const proofFile = resolve(root, '.api-proof.json');
rmSync(proofFile, { force: true });
if (!api?.packages?.length)
  throw new Error('No released public addon API is configured.');
if (
  execFileSync('git', ['rev-parse', 'HEAD'], {
    cwd: repo,
    encoding: 'utf8',
  }).trim() !== release.commit
)
  throw new Error('Use the exact pinned release checkout.');
const underRepo = (path) => {
  const target = resolve(repo, path);
  if (!target.startsWith(repo + sep))
    throw new Error('API inputs must stay inside the release checkout');
  return target;
};
const sha = (bytes) => createHash('sha256').update(bytes).digest('hex');
const scratch = underRepo(`.website-api-checks/${Date.now()}`);
mkdirSync(scratch, { recursive: true });
const python =
  process.env.WWPG_PYTHON ??
  (process.platform === 'win32' ? 'python' : 'python3');
const download = async (artifact) => {
  if (
    !/^[A-Za-z0-9._-]+$/.test(artifact.file) ||
    !/^[a-f0-9]{64}$/.test(artifact.sha256)
  )
    throw new Error('Invalid API artifact descriptor');
  const bytes = cachedAssets
    ? readFileSync(resolve(cachedAssets, artifact.file))
    : await (async () => {
        const response = await fetch(
          `${project.repository}/releases/download/${release.tag}/${artifact.file}`,
        );
        if (!response.ok)
          throw new Error(
            `Download failed: ${artifact.file}, ${response.status}`,
          );
        return Buffer.from(await response.arrayBuffer());
      })();
  if (sha(bytes) !== artifact.sha256)
    throw new Error(`Released SDK checksum mismatch: ${artifact.file}`);
  return bytes;
};
const bundledFiles = new Map();
for (const [id, bundle] of Object.entries(release.bundles ?? {})) {
  if (!/^[A-Za-z0-9_-]+$/.test(id)) throw new Error('Invalid bundle ID');
  const archive = resolve(scratch, bundle.file);
  writeFileSync(archive, await download(bundle));
  const extracted = resolve(scratch, `bundle-${id}`);
  execFileSync(
    python,
    [resolve(root, 'scripts/extract-reference.py'), archive, extracted],
    { stdio: 'inherit' },
  );
  for (const [filename, relative] of Object.entries(bundle.files)) {
    const path = resolve(extracted, relative);
    if (!path.startsWith(extracted + sep) || bundledFiles.has(filename))
      throw new Error('Invalid or duplicate bundled artifact path');
    bundledFiles.set(filename, path);
  }
}
const paths = {};
for (const [role, artifact] of Object.entries(api.artifacts)) {
  if (
    !/^[A-Za-z0-9._-]+$/.test(artifact.file) ||
    !/^[a-f0-9]{64}$/.test(artifact.sha256)
  )
    throw new Error('Invalid API artifact descriptor');
  const bytes = bundledFiles.has(artifact.file)
    ? readFileSync(bundledFiles.get(artifact.file))
    : await download(artifact);
  if (sha(bytes) !== artifact.sha256)
    throw new Error(`Released SDK checksum mismatch: ${artifact.file}`);
  paths[role] = resolve(scratch, artifact.file);
  writeFileSync(paths[role], bytes);
}
const wrapper = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
const tasks = api.references.map((ref) => ref.javadocTask);
if (tasks.some((task) => !/^[A-Za-z][\w:-]*$/.test(task)))
  throw new Error('Invalid API Javadoc task');
execFileSync(wrapper, [...tasks, '--no-daemon'], {
  cwd: repo,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
const args = [underRepo(api.verifier)];
for (const role of [
  'runtime',
  'api',
  'cee_api',
  'example',
  'upstream_example',
  'maven',
  'example_source',
  'upstream_source',
])
  args.push(`--${role.replaceAll('_', '-')}`, paths[role]);
const sdkOutput = resolve(scratch, 'sdk-verification');
args.push('--source-sha', release.commit, '--output', sdkOutput);
execFileSync(python, args, { cwd: repo, stdio: 'inherit' });
const sdkProof = JSON.parse(
  readFileSync(resolve(sdkOutput, 'attempt.json'), 'utf8'),
);
if (
  !sdkProof.completed ||
  !sdkProof.passed ||
  sdkProof.source_sha !== release.commit
)
  throw new Error('The extracted SDK verification did not pass');

// Freeze the exact generated reference shipped in the release, rather than a
// new Javadoc timestamp. The same release's API-only tasks are checked above.
const files = {};
const contents = new Map();
const packageList = (path) =>
  readFileSync(resolve(path, 'element-list'), 'utf8')
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith('module:'))
    .sort();
for (const ref of api.references) {
  if (!/^[A-Za-z0-9_-]+$/.test(ref.id))
    throw new Error('Invalid API reference path');
  const extracted = resolve(scratch, `reference-${ref.id}`);
  execFileSync(
    python,
    [
      resolve(root, 'scripts/extract-reference.py'),
      paths[ref.artifact],
      extracted,
    ],
    { stdio: 'inherit' },
  );
  for (const directory of [extracted, underRepo(ref.referenceDirectory)]) {
    if (
      !existsSync(resolve(directory, 'index.html')) ||
      JSON.stringify(packageList(directory)) !==
        JSON.stringify([...ref.packages].sort())
    )
      throw new Error('Javadoc differs from the supported package allowlist');
  }
  const collect = (directory, prefix) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (entry.isSymbolicLink())
        throw new Error('API reference cannot contain symlinks');
      const relative = prefix + entry.name;
      if (entry.isDirectory())
        collect(resolve(directory, entry.name), relative + '/');
      else {
        const bytes = readFileSync(resolve(directory, entry.name));
        contents.set(relative, bytes);
        files[relative] = sha(bytes);
      }
    }
  };
  collect(extracted, ref.id + '/');
}
const base = resolve(root, 'content/releases', release.version);
const manifestFile = resolve(base, 'api-manifest.json');
const manifest =
  JSON.stringify(
    {
      version: release.version,
      apiVersion: api.version,
      commit: release.commit,
      packages: api.packages,
      artifacts: api.artifacts,
      files,
    },
    null,
    2,
  ) + '\n';
if (existsSync(manifestFile))
  assertFrozenManifest(readFileSync(manifestFile, 'utf8'), JSON.parse(manifest));
const target = resolve(base, 'java-reference');
for (const [relative, bytes] of contents) {
  const output = resolve(target, relative);
  if (!output.startsWith(target + sep))
    throw new Error('Invalid reference path');
  if (existsSync(output) && !readFileSync(output).equals(bytes))
    throw new Error('Refusing to change a frozen API reference');
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, bytes);
}
// Filesystem enumeration order differs across operating systems. Keep the
// original snapshot bytes when its complete JSON contents match.
if (!existsSync(manifestFile)) writeFileSync(manifestFile, manifest);
writeFileSync(
  proofFile,
  JSON.stringify(
    {
      version: release.version,
      commit: release.commit,
      manifestHash: sha(JSON.stringify(project)),
      javadoc: true,
      example: true,
      sdkVerification: resolve(sdkOutput, 'attempt.json'),
    },
    null,
    2,
  ) + '\n',
);
console.log(
  'Verified both released Javadoc surfaces and independently compiled both downloadable examples against the exact SDK.',
);
