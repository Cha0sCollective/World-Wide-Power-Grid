import { isDeepStrictEqual } from 'node:util';

/** Compare every field and hash without treating JSON object order as data. */
export function assertFrozenManifest(existing, candidate) {
  if (!isDeepStrictEqual(JSON.parse(existing), candidate))
    throw new Error('Refusing to change a frozen API manifest');
}
