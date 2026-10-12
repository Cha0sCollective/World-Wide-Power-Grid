import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

export function loadCorrections(root, release, manifest) {
  const path = resolve(root, 'content/corrections', release.version + '.json');
  if (!existsSync(path)) return null;
  const data = JSON.parse(readFileSync(path, 'utf8'));
  if (data.release !== release.version || data.apiVersion !== release.api.version ||
      data.sourceCommit !== release.commit || !/^\d{4}-\d{2}-\d{2}$/.test(data.date))
    throw new Error('Reference correction release identity mismatch');
  const keys = new Set();
  for (const note of data.annotations) {
    const key = note.page + '#' + note.anchor;
    if (!Object.hasOwn(manifest.files, note.page) || keys.has(key) ||
        !/^[\w./]+\.java$/.test(note.source) || note.source.includes('..') ||
        /<(script|iframe|object)\b|\bon\w+\s*=/i.test(note.body))
      throw new Error('Invalid or duplicate reference correction: ' + key);
    keys.add(key);
  }
  return data;
}

export function annotateReference(source, file, corrections, repository) {
  if (!corrections) return source;
  const notes = corrections.annotations.filter(note => note.page === file);
  const escape = value => value.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  for (const note of notes) {
    const pattern = /<section\b[^>]*\bid="([^"]+)"[^>]*>/g;
    const matches = [...source.matchAll(pattern)].filter(m => m[1] === note.anchor);
    if (matches.length !== 1) throw new Error('Missing/duplicate reference correction target: ' + file + '#' + note.anchor);
    const match = matches[0];
    const box = `<aside class="reference-correction" aria-label="Documentation correction"><strong>Documentation correction · ${corrections.date}</strong><p>WWPG ${corrections.release} / API ${corrections.apiVersion} · ${escape(note.member)}</p>${note.body}<p><a href="${repository}/blob/${corrections.sourceCommit}/${note.source}">Verified released source</a> · <a href="/docs/${corrections.release}/api-limits/">Complete validation table</a></p><p>The released reference is preserved; this annotation clarifies existing behavior.</p></aside>`;
    const offset = match.index + match[0].length;
    source = source.slice(0, offset) + box + source.slice(offset);
  }
  return source;
}
