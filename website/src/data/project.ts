import data from './project.json' with { type: 'json' };

export interface AddonApi {
  version: string;
  packages: string[];
  verifier: string;
  artifacts: Record<string, { file: string; sha256: string }>;
  references: {
    id: string;
    title: string;
    artifact: string;
    packages: string[];
    javadocTask: string;
    referenceDirectory: string;
  }[];
}
export interface Release {
  version: string;
  tag: string;
  commit: string;
  channel: string;
  date: string;
  requirements: Record<string, string>;
  optionalRequirements: Record<string, string>;
  downloads: Record<string, string>;
  guides: {
    source: string;
    slug: string;
    title: string;
    description: string;
    stopBefore?: string;
  }[];
  extraSources?: string[];
  api: AddonApi | null;
}
export const project = data as Omit<typeof data, 'releases' | 'modrinthUrl'> & {
  releases: Release[];
  modrinthUrl: string | null;
};
export const currentRelease = project.releases.find(
  (r) => r.version === project.currentRelease,
)!;
export const docsHref = (version = currentRelease.version) =>
  `/docs/${version}/`;
export const releaseHref = (release = currentRelease) =>
  `${project.repository}/releases/tag/${release.tag}`;
export const assetHref = (asset: string, release = currentRelease) =>
  `${project.repository}/releases/download/${release.tag}/${release.downloads[asset]}`;
export const downloadHref = () => project.modrinthUrl ?? assetHref('jar');
export const sourceHref = (path: string, release = currentRelease) =>
  `${project.repository}/blob/${release.commit}/${path}`;
