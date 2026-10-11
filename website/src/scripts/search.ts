interface SearchResult {
  url: string;
  meta: { title?: string; version?: string };
  excerpt: string;
}
interface Pagefind {
  search: (
    term: string,
    options: { filters: { version: string } },
  ) => Promise<{ results: { data: () => Promise<SearchResult> }[] }>;
}
export function initializeSearch(
  load = async (): Promise<Pagefind> => {
    // Use the preview or published origin for this generated public asset.
    const path = new URL('/pagefind/pagefind.js', window.location.href).href;
    return import(/* @vite-ignore */ path);
  },
) {
  const open = document.querySelector<HTMLButtonElement>('.docs-search-open');
  const fallback = document.querySelector<HTMLElement>('.docs-search-fallback');
  const dialog = document.querySelector<HTMLDialogElement>(
    '.docs-search-dialog',
  );
  const input = document.querySelector<HTMLInputElement>('#docs-search-input');
  const status = document.querySelector<HTMLElement>('#search-status');
  const list = document.querySelector<HTMLElement>('.docs-search-results');
  if (!open || !dialog || !input || !status || !list) return;
  const version = dialog.dataset.version ?? '';
  let request = 0;
  let timer: ReturnType<typeof setTimeout>;
  let engine: Promise<Pagefind> | undefined;
  open.hidden = false;
  if (fallback) fallback.hidden = true;
  const show = () => {
    if (!dialog.open) dialog.showModal();
    input.focus();
  };
  open.addEventListener('click', show);
  dialog
    .querySelector('.docs-search-close')
    ?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => open.focus());
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && dialog.open) {
      event.preventDefault();
      dialog.close();
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      show();
    }
  });
  const search = async () => {
    const id = ++request;
    const term = input.value.trim();
    list.replaceChildren();
    if (!term) {
      status.textContent = 'Enter a topic to search.';
      return;
    }
    status.textContent = 'Searching…';
    try {
      engine ??= load();
      const found = await (await engine).search(term, { filters: { version } });
      const results = await Promise.all(
        found.results.slice(0, 12).map((result) => result.data()),
      );
      if (id !== request || input.value.trim() !== term) return;
      status.textContent = found.results.length
        ? `${found.results.length} result${found.results.length === 1 ? '' : 's'} in ${version}.`
        : `No results in ${version}. Try another term.`;
      for (const result of results) {
        const item = document.createElement('li');
        const anchor = document.createElement('a');
        anchor.href = result.url;
        const title = document.createElement('strong');
        title.textContent = result.meta.title ?? 'Documentation';
        const label = document.createElement('small');
        label.textContent = `WWPG ${result.meta.version ?? version}`;
        const excerpt = document.createElement('p');
        excerpt.textContent = result.excerpt.replace(/<[^>]*>/g, '');
        anchor.append(title, label, excerpt);
        item.append(anchor);
        list.append(item);
      }
    } catch {
      if (id !== request) return;
      engine = undefined;
      status.textContent =
        'Search is unavailable. Browse this release using the chapter navigation.';
    }
  };
  input.addEventListener('input', () => {
    ++request;
    clearTimeout(timer);
    timer = setTimeout(search, 180);
  });
}
