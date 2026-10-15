# Publishing a release

Every release uses five downloads and short notes. Players should immediately
see the mod jar; worlds, development tools and test records each get one ZIP.

| File | Audience |
| --- | --- |
| `wwpg-VERSION.jar` | Everyone installing WWPG |
| `wwpg-VERSION-example-worlds.zip` | Players trying the Stationary Yard or API Workshop |
| `wwpg-VERSION-developer-sdk.zip` | Add-on developers |
| `wwpg-VERSION-verification.zip` | Reviewers inspecting the accepted files and test records |
| `wwpg-VERSION-checksums.txt` | Anyone checking downloaded files |

GitHub adds its source ZIP and tar archive automatically. Do not upload separate
API jars, world saves, logs, screenshots or licenses as additional release assets.
They belong inside the appropriate ZIP. Include licenses in the grouped downloads.

Release notes explain the change in plain language, provide the download table,
and link installation and support guides. They allow at most **three highlights
and 250 words total**. Keep detailed changes in the changelog and test records
in the verification archive.

## Prepare the accepted inputs

Full-suite runs require an explicit user request, including the end-of-day
run. During development, check the changed area. A release that changes only
the runtime's version metadata may reuse preserved passing full acceptance:
record its original source/artifact identities and verify every other runtime
entry is unchanged. SDK or packaging changes still need focused checks.
Never describe carried-forward acceptance or an interrupted run as a fresh pass.

Windows acceptance may also come from a preserved full local run, combined with
the complete four-way Linux CI matrix. Use `retained_windows_acceptance` only
with version-equivalent `acceptance_reuse`. Its validator reparses the original
locked process outcomes, logs, unit XML, cold-state oracles and lifecycle markers;
four unverified summaries cannot replace CI. Preserve the original ledger and
source. Set `ci_source_sha` when the independently checked CI commit differs.
The manual Build workflow's `platforms=linux` option runs only the missing
platform. This does not authorize running it automatically.

An optional `benchmark_world` descriptor adds the original mixed capacity save
inside the example-worlds ZIP. Its inventory must bind every entry, label the
actual 14,650-node workload, and state that 20 TPS remains uncertified. It is
separate from the four playable demonstration saves. Keep private benchmark
tooling and detailed performance evidence out of the release inputs.

If installation instructions change after runtime acceptance, freeze their own
documentation commit. An optional `documentation_snapshot` file descriptor in
the manifest locks a JSON record with `schema: 1`, that commit's `source_sha`,
the accepted `runtime_source_sha`, and a `files` map for every `SDK_DOCUMENTS`
input in `package_api_release.py`. Capture raw files from the named Git commit;
the packager checks their hashes and retains them with the evidence. Runtime
source, artifact hashes, CI records and the release tag keep their tested
identities. README-only example archives need fresh independent builds with
identical compiled outputs. Starter preparation accepts `--runtime-source-sha`
for this case and records its actual project checkout separately.

1. Update `mod_version` and, when appropriate, `api_version` in `gradle.properties`.
   Keep the CI commands, independent example dependencies and API comparisons
   aligned with those versions. A mod release does not automatically change the API version.
2. Complete the [API release checks](API_RELEASE_CHECKS.md). Freeze the exact
   source commit, artifacts, worlds and acceptance evidence in the new version's
   verification record. Preserve prior records and failed attempts.
3. Edit [release/presentation.json](../release/presentation.json): write a short
   player-facing summary and up to three highlights; confirm installation versions.
   An empty highlights list is allowed. Use the changelog for the full detail.
4. Create a fresh accepted bundle:

```text
python tools/package_api_release.py --manifest release/VERSION/verification.json --output build/distributions/wwpg-VERSION-release.zip --release
```

Use the SHA256 printed by this command in the next step. A candidate package
does not qualify, even if its files can be grouped successfully.

## Stage and inspect the five downloads

```text
python tools/release_downloads.py stage --bundle build/distributions/wwpg-VERSION-release.zip --sha256 ACCEPTED_BUNDLE_SHA256 --output build/release-VERSION
python tools/release_downloads.py check --staged build/release-VERSION
```

Replace `VERSION` and `ACCEPTED_BUNDLE_SHA256` with the actual values. Use a fresh
output directory for each attempt. Staging reruns the artifact and acceptance
validators against the bundle's retained inputs; it does not launch Minecraft
or create passing evidence.

Inspect `release-notes.md` and the three ZIPs. The worlds bundle includes all four
world editions, the two optional Workshop jars, instructions and licenses. The
SDK contains both APIs, sources, Javadoc, Maven repository, example projects,
guides and licenses. Inner world and SDK files retain their accepted bytes.
The verification ZIP is an unchanged copy of the complete accepted bundle.

`download-layout.json` records the frozen source and all five file hashes and
sizes. Only the files in `downloads/` are uploaded. Keep the notes, presentation
and layout together; changing them requires staging a fresh attempt.

## Upload a draft, then publish

Use an authenticated GitHub CLI account with permission to create releases.
First inspect the upload plan without accessing GitHub:

```text
python tools/release_downloads.py publish --staged build/release-VERSION --plan
```

Upload the draft:

```text
python tools/release_downloads.py publish --staged build/release-VERSION
```

Review its notes and downloads on GitHub. Publish that draft with:

```text
python tools/release_downloads.py publish --staged build/release-VERSION --publish
```

The publisher checks local acceptance again, verifies the five uploaded hashes
and sizes, and only then makes the draft public. A version containing a suffix
such as `-beta.1` becomes a prerelease. The new tag targets the manifest's frozen
source commit. An existing tag must resolve to that exact commit.

If an upload stops partway, rerun the same command with the same staged directory.
It resumes a matching draft by uploading missing files. Changed files, unexpected
assets, a different draft, or an existing public release stop publication.
The tool never deletes assets, overwrites uploads, moves tags or edits public releases.

Commit the final notes, presentation and `download-layout.json` under
`release/VERSION/` as the publication record; keep large artifacts in the release
downloads. Update the installation, status, support and changelog links to the
new release. Earlier published assets and acceptance records stay unchanged.

## Check tooling changes

```text
python tools/test_release_process.py
python tools/test_api_compatibility.py
```

These checks run on Windows and Linux in the existing CI matrix. They cover
grouping, byte preservation, note limits, rejected candidates, corrupted inputs,
draft recovery and publication safeguards. They do not replace gameplay acceptance.

## Include the authoring starter in the next SDK

The existing 0.2.1 downloads remain unchanged. Future SDKs include the validation,
tutorial, workflow and troubleshooting guides with bundle-valid navigation.
Export `examples/adjustable-load` with `package_api_release.py --example-source`;
standard Unix extraction must preserve its executable wrapper.

Add an `authoring_starter` object to the new release manifest, containing locked
`source` and `proof` file descriptors (`path`, `sha256`, `bytes`). Its proof must
identify the frozen release source, `source_archive_sha256`, the actually resolved
`compiled_against_api_sha256`, `uses_wwpg_project_classes: false`, `passed: true`,
and the locked independent build, dependency and gameplay/client records described
in [API release checks](API_RELEASE_CHECKS.md#adjustable-load-starter-in-the-next-sdk).
The graphical checks deferred during documentation work remain a release gate. The accepted archive carries
this source ZIP into the existing SDK's `examples/` folder; no sixth download or
new mandatory runtime mod is introduced. All normal release gates still apply.
