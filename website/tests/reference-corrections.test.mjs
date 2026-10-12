import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { annotateReference, loadCorrections } from '../scripts/reference-corrections.mjs';
const project=JSON.parse(readFileSync('src/data/project.json','utf8'));
const release=project.releases.find(r=>r.version==='0.2.1');
const base='content/releases/0.2.1/';
const manifest=JSON.parse(readFileSync(base+'api-manifest.json','utf8'));
const corrections=loadCorrections(process.cwd(),release,manifest);
test('every correction targets a real frozen member without changing input bytes',()=>{
 for(const note of corrections.annotations){
  const path=base+'java-reference/'+note.page;
  const bytes=readFileSync(path);
  assert.equal(createHash('sha256').update(bytes).digest('hex'),manifest.files[note.page]);
  const rendered=annotateReference(bytes.toString('utf8'),note.page,corrections,project.repository);
  assert.match(rendered,/Documentation correction/);
  assert.ok(rendered.includes(note.member));
  assert.ok(rendered.includes(corrections.sourceCommit));
  assert.ok(readFileSync(path).equals(bytes));
 }
});
test('a missing target cannot silently drop a correction',()=>{
 assert.throws(()=>annotateReference('<html></html>',corrections.annotations[0].page,corrections,project.repository),/target/);
});
test('incorrect release identity cannot apply another contract',()=>{
 assert.throws(()=>loadCorrections(process.cwd(),{...release,commit:'0'.repeat(40)},manifest),/identity/);
});
test('duplicate anchors cannot ambiguously place a correction',()=>{
 const note=corrections.annotations[0];
 const html=`<section id="${note.anchor}"></section><section id="${note.anchor}"></section>`;
 assert.throws(()=>annotateReference(html,note.page,corrections,project.repository),/target/);
});
