# Repository working conventions

Use `dev/` for working branches, for example `dev/documentation-cleanup`.

Run focused checks for the code, documentation or packaging being changed.
The full suite means full headless acceptance. Graphical client acceptance is
a separate manually requested lane; see docs/TESTING.md. Publishing validates
applicable headless and graphical evidence without launching either suite.
Run the full testing suite only when the user explicitly requests it. The
end-of-day run is also manual: wait for the user to say they are done and to
run the tests. Do not schedule it or automatically run full suites on pushes,
pull requests, version bumps or releases. Reuse preserved passing acceptance
when the tested runtime is unchanged, with explicit artifact comparisons;
never relabel old evidence or interrupted runs as fresh passes.

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
