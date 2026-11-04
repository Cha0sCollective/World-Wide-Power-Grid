/** Apply website-only hardening after immutable source hashes are verified. */
export function renderReferenceScript(file, bytes) {
  if (!file.endsWith('/search-page.js')) return bytes;
  const source = bytes.toString('utf8');
  const legacy = 'searchLink.html(href);';
  const safe = 'searchLink.text(href);';
  const legacyCount = source.split(legacy).length - 1;
  const safeCount = source.split(safe).length - 1;
  if (legacyCount === 1 && safeCount === 0)
    return Buffer.from(source.replace(legacy, safe), 'utf8');
  if (legacyCount === 0 && safeCount === 1) return bytes;
  throw new Error(`Unexpected released Javadoc URL writer: ${file}`);
}
