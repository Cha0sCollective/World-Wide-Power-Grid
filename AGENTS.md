# Repository working conventions

Use `dev/` for working branches, for example `dev/documentation-cleanup`.

Current user and developer guides start at [docs/README.md](docs/README.md).
Write player instructions in plain language: what to operate, what should
happen, how to reset it, and which mods are required. Keep internal test and
release details in developer references.

Keep letters, presentations and outreach drafts for upstream authors outside
the repository. Repository documentation should explain the product and its use.

Preserve published release assets, tags, checksums and captured acceptance
evidence. Historical notes describe their named release or development snapshot;
label them clearly when adding navigation to current guides.

Publish future releases through [docs/RELEASING.md](docs/RELEASING.md) and
`tools/release_downloads.py`: five uploaded downloads, with the mod jar first,
at most three highlights and 250 words of notes. Keep SDK contents, worlds and
test evidence inside their respective bundles. Acceptance gates still apply.
