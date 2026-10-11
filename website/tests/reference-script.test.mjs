import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { JSDOM } from 'jsdom';
import { project } from '../src/data/project.ts';

test('published Javadoc URL templates display and copy literal text, including character references and raw test markup', () => {
  for (const release of project.releases.filter((item) => item.api))
    for (const reference of release.api.references) {
      const root = `dist/api/${release.version}/${reference.id}/`;
      const script = readFileSync(root + 'search-page.js', 'utf8');
      const jquery = readFileSync(root + 'script-dir/jquery-3.7.1.min.js', 'utf8');
      const prefix = `${project.origin}/api/${release.version}/${reference.id}/`;
      const rawMarkup = prefix + '<img data-reference-probe>/search.html?q=old#fragment';
      // The raw URL seam tests the writer independently of browser URL
      // normalization. It does not claim a demonstrated browser exploit.
      for (const address of [new URL(rawMarkup).href, rawMarkup,
        prefix + '&lt;reference-probe&gt;/search.html?q=old#fragment']) {
        const dom = new JSDOM('<span id="page-search-link"></span><button id="page-search-copy"><span>Copy</span></button><button id="page-search-expand"></button><input type="checkbox" id="search-redirect">', {runScripts:'outside-only'});
        Object.defineProperty(dom.window.HTMLElement.prototype, 'innerText', {
          configurable: true,
          get() { return this.textContent; },
        });
        dom.window.eval(jquery);
        const realJquery = dom.window.jQuery;
        const copied = [];
        runInNewContext(script, {
          document: {location: {href: address}},
          window: dom.window,
          $: (value) => {
            if (typeof value === 'function') return value();
            // This test targets the URL-template controls; the separately
            // verified symbol-search handler is not activated here.
            if (value === dom.window) return {on() {}};
            return realJquery(value);
          },
          copyToClipboard: (value) => copied.push(value),
          switchCopyLabel() {},
        });
        const expected = address.split(/[#?]/)[0] + '?q=%s';
        const span = dom.window.document.querySelector('#page-search-link');
        const copy = dom.window.document.querySelector('#page-search-copy');
        const redirect = dom.window.document.querySelector('#search-redirect');
        assert.equal(span.textContent, expected);
        assert.equal(span.childElementCount, 0);
        copy.click();
        assert.equal(copied.at(-1), expected);
        redirect.click();
        assert.equal(redirect.checked, true);
        assert.equal(span.textContent, expected + '&r=1');
        assert.equal(span.childElementCount, 0);
        copy.click();
        assert.equal(copied.at(-1), expected + '&r=1');
        redirect.click();
        assert.equal(span.textContent, expected);
        dom.window.close();
      }
    }
});
