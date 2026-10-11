import type { APIRoute } from 'astro';
import { project } from '../data/project';
export const GET: APIRoute = () =>
  new Response(
    project.publicationReady
      ? `User-agent: *\nAllow: /\nSitemap: ${project.origin}/sitemap-index.xml\n`
      : 'User-agent: *\nDisallow: /\n',
    { headers: { 'Content-Type': 'text/plain; charset=utf-8' } },
  );
