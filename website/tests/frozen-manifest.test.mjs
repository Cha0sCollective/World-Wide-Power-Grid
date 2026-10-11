import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { assertFrozenManifest } from '../scripts/frozen-manifest.mjs';

const frozenText = readFileSync('content/releases/0.2.0/api-manifest.json', 'utf8');
const frozen = JSON.parse(frozenText);

test('the released reference accepts different filesystem enumeration order', () => {
  const reordered = {
    ...frozen,
    files: Object.fromEntries(Object.entries(frozen.files).reverse()),
    artifacts: Object.fromEntries(Object.entries(frozen.artifacts).reverse()),
  };
  assert.notEqual(JSON.stringify(reordered), JSON.stringify(frozen));
  assert.doesNotThrow(() => assertFrozenManifest(frozenText, reordered));
  assert.equal(readFileSync('content/releases/0.2.0/api-manifest.json', 'utf8'), frozenText);
});

test('frozen reference protection rejects changed hashes, files, packages, and release identity', () => {
  const mutations = [
    (candidate) => { candidate.files[Object.keys(candidate.files)[0]] = '0'.repeat(64); },
    (candidate) => { delete candidate.files[Object.keys(candidate.files)[0]]; },
    (candidate) => { candidate.files['core/unreviewed.html'] = '0'.repeat(64); },
    (candidate) => { candidate.artifacts.api.sha256 = '0'.repeat(64); },
    (candidate) => { candidate.packages.push('org.cha0scollective.wwpg.internal'); },
    (candidate) => { candidate.commit = '0'.repeat(40); },
    (candidate) => { candidate.apiVersion = '2.0.0'; },
  ];
  for (const mutate of mutations) {
    const candidate = structuredClone(frozen);
    mutate(candidate);
    assert.throws(() => assertFrozenManifest(frozenText, candidate), /frozen API manifest/);
  }
});
