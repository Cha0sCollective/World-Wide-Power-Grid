import { defineCollection } from 'astro:content';
import { docsLoader, i18nLoader } from '@astrojs/starlight/loaders';
import { docsSchema, i18nSchema } from '@astrojs/starlight/schema';

// Keep periods in release identifiers; the generic loader otherwise normalizes them.
export const collections = {
  docs: defineCollection({
    loader: docsLoader({
      generateId: ({ entry }) =>
        entry.replace(/\.(md|mdx)$/, '').replace(/\/index$/, ''),
    }),
    schema: docsSchema(),
  }),
  i18n: defineCollection({ loader: i18nLoader(), schema: i18nSchema() }),
};
