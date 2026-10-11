# WWPG website

An independent static Astro + Starlight site for `https://wwpg.ch4os.live`. The community showcase lives in Ch4oS.Live at `/mods/wwpg/`; the player handbook will be published separately at `/manual/`.

## Local preview

Use Node 24 (minimum 22.12). From this directory:

```sh
npm ci
npm run check
npm run build
npm test
npm run preview
```

The preview binds to `http://127.0.0.1:4322`. `npm run dev` prepares the docs and starts the development server on that port. Search uses the production Pagefind index: use `build` followed by `preview` to test it. Rebuild and restart after changing release metadata or guide content. On restricted Windows hosts, set `ASTRO_TELEMETRY_DISABLED=1` and use an execution environment that permits native Node module loading.

## Release metadata and content

Edit `src/data/project.json` for the origin, current release, requirements, download filenames, optional Modrinth URL, source commits, and published documentation editions. The `modrinthUrl` remains `null` until an approved listing exists. Set the actual approved project URL to make Modrinth the primary download destination; direct jar and test-world links remain pinned to GitHub.

`content/releases/<version>/` contains a small explicit selection of immutable release-source files and their SHA-256 manifest. `npm run snapshot` reads the configured tags from the parent mod repository and refuses changed existing snapshot files. Every build verifies these bytes and converts selected Markdown into ignored `src/content/docs/`. Relative guide links resolve to local pages; other source links resolve to the pinned GitHub commit. No private handbook research, capture databases, mod jars, or broad source-tree export is included.

Reviewed website guides live in `content/guides/<version>/`. Add a complete set for a new release, then snapshot its selected sources, update the current-release field, and build. Preserve earlier editions. Each guide carries a release notice; search filters to the active edition and labels each result. No visitor requests to GitHub or Modrinth APIs are required.

The current edition covers stable WWPG 0.2.1, supporting NeoForge 21.1.231 / 21.1.248, and independently versioned API 1.0.0. The 0.2.0 edition retains its original requirements and released assets. The beta.5 edition remains available with its original source bytes and explicit lack of a public Java API. Lua calls are checked against each release's actual acceptance program; website validation does not claim new Minecraft gameplay execution.

## Released addon API and launch validation

The `api` object pins the public package allowlist, the two release-owned API-only Javadoc tasks, the released SDK assets and their SHA-256 values, and the release's extracted-SDK verification tool. It includes `wwpg-api:1.0.0` and `wwpg-cee-api:1.0.0`; the downloadable Maven ZIP supplies both coordinates without GitHub credentials.

The public release distributes a developer-SDK bundle and an example-worlds bundle. `bundles` pins each outer ZIP's checksum and the selected member paths. Preparation verifies the outer ZIPs before bounded extraction, then checks the original API and example hashes before compiling. The Maven and source ZIPs are in the SDK; the example runtime jars are in the worlds bundle. The individual artifact and frozen-reference bytes are unchanged. Rendered guides translate old individual download links to the current bundles, while preserving the immutable release snapshots.

With Java 21 and Python 3 available, run:

```sh
npm run prepare:api -- /absolute/path/to/exact-release-checkout
```

Use a separate checkout at the configured immutable release commit. The command downloads and checks the pinned artifacts, runs `apiJavadoc` and `ceeApiJavadoc`, then invokes the release's own SDK verifier. That verifier extracts both released example source ZIPs and the Maven ZIP, builds each standalone project, checks the actual resolved API hashes, and requires output jars identical to the published examples. It does not depend on WWPG's implementation classes. An optional second argument supplies a directory of already downloaded assets; every asset still undergoes the same SHA-256 check. `WWPG_PYTHON` can select a Python executable on hosts where it is not on PATH.

The frozen Java reference comes from the **generated Javadoc jars published with the release**, preserving their exact bytes and timestamps. Both newly generated and published references must have exactly the supported packages. `content/releases/<version>/java-reference/{core,cee}/` and `api-manifest.json` retain these immutable snapshots; builds verify hashes and add website navigation, metadata, and a dark stylesheet only to the rendered copies. `/api/<mod-version>/` identifies the independent API version and links both contracts. The generated references also provide their own Java symbol search.

Commit the frozen reference and manifest with the website release update. Re-running preparation refuses changed frozen bytes. Temporary build evidence stays in the separate release checkout's `.website-api-checks/`, while the ignored `.api-proof.json` binds successful Javadoc and both example builds to the exact release and website configuration. Neither is published.

`npm run check:launch` requires matching proof and `publicationReady: true`. Keep this flag false until independent review and visual acceptance are complete. Preview pages remain non-indexable. The manual workflow runs preparation again before launch, so changing metadata invalidates stale proof.

## GitHub Pages and DNS at launch

The check workflow validates website changes on pushes and pull requests; it does not deploy. The separate **Publish WWPG website** workflow is manually dispatched from `main`. It checks out the pinned mod release separately, validates the two released references and independently compiles both downloadable examples, checks the launch requirements, then checks, builds, tests, uploads, and deploys the website.

At launch, configure this repository's Pages source as GitHub Actions and its custom domain as `wwpg.ch4os.live`. Add the GoDaddy CNAME host `wwpg` pointing directly to `Cha0sCollective.github.io`. Verify the domain and enable HTTPS after the certificate is available. Preserve the apex, `www`, mail records, and Minecraft records. GitHub Actions publishing needs no `CNAME` file.

After the dedicated site is reachable over HTTPS, set `wwpg.websiteReady: true` in Ch4oS.Live's shared site data to activate its dedicated-site action. Until then, the showcase links to the existing public GitHub project and releases.

The documentation remains distinct from the future pack player manual. The current handbook PG guide targets 0.6.1; the public pack currently uses PG 0.6.2 and CEE 1.1.1; WWPG 0.2.0 and beta.5 require PG 0.6.2 and CEE 1.1.3. Publish cross-links only when the receiving chapter exists and identifies its own edition.

## Assets and dependencies

The mechanical electrical artwork and favicon are SVG source. `public/images/social.png` is a 1200×630 raster export of `social.svg` for sharing services. Fonts are self-hosted. The scoped PostCSS selector-parser override fixes the audited parser advisory while retaining the planned Astro and Starlight versions; production CSS rendering is included in visual validation.
