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
