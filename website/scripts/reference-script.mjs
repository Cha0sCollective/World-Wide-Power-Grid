/** Apply website-only hardening after immutable source hashes are verified. */
export function renderReferenceScript(file, bytes) {
  if (!file.endsWith('/search-page.js')) return bytes;
  const source = bytes.toString('utf8');
  const expected = 'searchLink.html(href);';
  if (source.split(expected).length !== 2)
    throw new Error(`Unexpected released Javadoc URL writer: ${file}`);
  return Buffer.from(source.replace(expected, 'searchLink.text(href);'), 'utf8');
}
