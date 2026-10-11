import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const project = JSON.parse(
  readFileSync(resolve(root, 'src/data/project.json'), 'utf8'),
);
export function launchErrors(project, proof) {
  const release = project.releases.find(
    (r) => r.version === project.currentRelease,
  );
  const errors = [];
  if (!project.publicationReady)
    errors.push(
      'Publication is disabled pending visual acceptance and the first launch.',
    );
  if (!release?.api?.packages?.length)
    errors.push(
      'The current release has no explicitly exported public addon API packages.',
    );
  if (
    !release?.api?.version ||
    !release.api.verifier ||
    !release.api.artifacts ||
    !release.api.references?.length
  )
    errors.push(
      'The API reference and compiling quick-start integration are not configured.',
    );
  if (
    !proof ||
    proof.version !== release?.version ||
    proof.commit !== release?.commit ||
    proof.manifestHash !==
      createHash('sha256').update(JSON.stringify(project)).digest('hex') ||
    proof.javadoc !== true ||
    proof.example !== true
  )
    errors.push(
      'A matching successful Javadoc and example-compilation run is required.',
    );
  return errors;
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const file = resolve(root, '.api-proof.json');
  const proof = existsSync(file)
    ? JSON.parse(readFileSync(file, 'utf8'))
    : null;
  const errors = launchErrors(project, proof);
  if (errors.length) {
    console.error(errors.join('\n'));
    process.exitCode = 1;
  } else console.log('API launch requirements passed.');
}
