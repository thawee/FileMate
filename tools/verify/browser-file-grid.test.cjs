const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const script = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/script.js'), 'utf8');

function decodeAttribute(value) {
    return value.replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
}

function browser() {
    const elements = new Map();
    const downloads = [];
    let context;

    class Element {
        constructor(tagName = 'div', attributes = {}) {
            this.tagName = tagName;
            this.attributes = attributes;
            this.dataset = Object.fromEntries(Object.entries(attributes)
                .filter(([name]) => name.startsWith('data-'))
                .map(([name, value]) => [name.slice(5).replace(/-([a-z])/g, (_, letter) => letter.toUpperCase()), value]));
            this.classList = { contains: name => (attributes.class || '').split(' ').includes(name) };
            this.children = [];
            this.style = {};
            this.textContent = '';
            this.checked = false;
            this.src = attributes.src;
        }
        set innerHTML(markup) {
            this.markup = markup;
            this.children = Array.from(markup.matchAll(/<(input|img)\b([^>]+)>/g), match => {
                const attributes = Object.fromEntries(Array.from(match[2].matchAll(/([\w-]+)="([^"]*)"/g), attr => [attr[1], decodeAttribute(attr[2])]));
                return new Element(match[1], attributes);
            });
        }
        get innerHTML() {
            return this.markup ?? this.textContent.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }
        getAttribute(name) { return this.attributes[name] ?? null; }
        appendChild(child) { this.children.push(child); }
        removeChild(child) { this.children.splice(this.children.indexOf(child), 1); }
        click() { downloads.push(this.href); }
        error() {
            const handler = vm.runInContext(`(function () { ${this.attributes.onerror} })`, context);
            handler.call(this);
        }
    }

    for (const id of ['fileListBody', 'fileGridContainer', 'emptyState', 'batchToolbar', 'batchCount', 'selectAllBtn', 'selectAllCheckbox']) {
        elements.set(id, new Element());
    }
    const boxes = () => ['fileListBody', 'fileGridContainer'].flatMap(id => elements.get(id).children.flatMap(row => row.children.filter(child => child.tagName === 'input')));
    const matching = selector => {
        if (selector.includes('[data-name=')) {
            const match = selector.match(/\[data-name="([^"]*)"\]$/);
            if (!match) throw new SyntaxError('Invalid filename selector');
            return matching(selector.slice(0, selector.indexOf('[data-name='))).filter(box => box.dataset.name === match[1].replace(/\\(.)/g, '$1'));
        }
        const index = selector.match(/\[data-index="(\d+)"\]/)?.[1];
        return boxes().filter(box => {
            if (selector.startsWith('.grid-checkbox') && !box.classList.contains('grid-checkbox')) return false;
            if (selector.includes(':not(.grid-checkbox)') && box.classList.contains('grid-checkbox')) return false;
            if (selector.includes(':checked') && !box.checked) return false;
            return index === undefined || box.dataset.index === index;
        });
    };
    context = vm.createContext({
        document: {
            addEventListener() {},
            getElementById: id => elements.get(id),
            createElement: tag => new Element(tag),
            querySelectorAll: matching,
            querySelector: selector => matching(selector)[0] ?? null,
            body: new Element('body')
        },
        window: { addEventListener() {} },
        sessionStorage: { getItem() {} },
        console
    });
    vm.runInContext(script, context, { filename: 'script.js' });
    return {
        elements, downloads,
        render(names) { context.renderFiles(names.map(name => ({ name, isDirectory: false, size: 16, lastModified: 1 }))); },
        boxes,
        selected: () => JSON.parse(JSON.stringify(context.getSelectedFiles())),
        click(index, grid, checked = true, shiftKey = false) {
            const checkbox = boxes().find(box => box.dataset.index === String(index) && box.classList.contains('grid-checkbox') === grid);
            checkbox.checked = checked;
            context.handleCheckboxClick({ shiftKey }, checkbox);
        },
        download() { context.batchDownloadZip(); },
        selectAll() { context.toggleSelectAll({ checked: true }); }
    };
}

test('two selected grid images produce two ZIP entries with mirrored list boxes', () => {
    const page = browser();
    page.render(['first.png', 'second.png']);
    page.click(0, true);
    page.click(1, true);
    assert.deepEqual(page.selected(), [{ name: 'first.png', isDir: false }, { name: 'second.png', isDir: false }]);
    assert.equal(page.elements.get('batchCount').textContent, 2);
    assert.equal(page.boxes().filter(box => box.checked).length, 4);
    page.download();
    assert.deepEqual(new URL(page.downloads[0], 'http://phone').searchParams.getAll('name'), ['first.png', 'second.png']);
    assert.equal(page.boxes().filter(box => box.checked).length, 0);
});

test('select all preserves literal apostrophes, quotes, ampersands, and backslashes once per filename', () => {
    const page = browser();
    const names = ["owner's art.svg", 'a"quote.png', 'a\\folder.png', 'a&b.png'];
    page.render(names);
    page.selectAll();
    assert.deepEqual(page.selected().map(file => file.name), names);
    page.download();
    assert.deepEqual(new URL(page.downloads[0], 'http://phone').searchParams.getAll('name'), names);
});

test('ordinary and range selections synchronize both views for special filenames', () => {
    const page = browser();
    page.render(["owner's art.svg", 'a"quote.png', 'a\\folder.png']);
    page.click(1, true);
    assert.equal(page.boxes().filter(box => box.checked).length, 2);
    page.click(1, false, false);
    assert.equal(page.boxes().filter(box => box.checked).length, 0);
    page.click(0, true);
    page.click(2, true, true, true);
    assert.equal(page.boxes().filter(box => box.checked).length, 6);
    page.click(0, false, false, true);
    assert.equal(page.boxes().filter(box => box.checked).length, 0);
});

test('failed thumbnails fall back to the original apostrophe SVG in both views', () => {
    const page = browser();
    page.render(["owner's art.svg"]);
    for (const id of ['fileListBody', 'fileGridContainer']) {
        const image = page.elements.get(id).children[0].children.find(child => child.tagName === 'img');
        assert.match(image.src, /^\/api\/thumbnail/);
        image.error();
        assert.equal(image.src, "/api/download/owner's%20art.svg?path=&_t=1");
        assert.equal(image.onerror, null);
    }
});
