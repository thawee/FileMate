const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const assets = path.resolve(__dirname, '../../app/src/main/assets');
const script = fs.readFileSync(path.join(assets, 'script.js'), 'utf8');
const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');

function browser() {
    const elements = new Map();
    const requests = [];
    const refreshedPaths = [];
    let maxActive = 0;

    class Element {
        constructor() {
            this.style = {};
            this.textContent = '';
            this.disabled = false;
            this.markup = null;
        }
        set innerHTML(value) {
            this.markup = value;
            for (const match of value.matchAll(/id="([^"]+)"/g)) {
                elements.set(match[1], new Element());
            }
        }
        get innerHTML() {
            return this.markup ?? this.textContent.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }
    }
    for (const match of html.matchAll(/id="([^"]+)"/g)) elements.set(match[1], new Element());

    class XHR {
        constructor() {
            this.upload = {};
            this.headers = {};
            this.finished = false;
            this.aborted = false;
            requests.push(this);
        }
        open(method, url) { this.method = method; this.url = url; }
        setRequestHeader(name, value) { this.headers[name] = value; }
        send(body) {
            this.body = body;
            maxActive = Math.max(maxActive, requests.filter(request => request.body && !request.finished && !request.aborted).length);
        }
        abort() { this.aborted = true; this.onabort(); }
        complete(status = 200, response = '') {
            this.finished = true;
            this.status = status;
            this.responseText = response;
            this.onload();
        }
        disconnect() { this.finished = true; this.onerror(); }
        progress(loaded, total) { this.upload.onprogress({ loaded, total, lengthComputable: true }); }
    }
    class FormData {
        constructor() { this.fields = new Map(); }
        append(name, value) { this.fields.set(name, value); }
    }
    const session = new Map();
    const context = vm.createContext({
        document: {
            addEventListener() {},
            getElementById: id => elements.get(id),
            createElement: () => new Element()
        },
        window: { addEventListener() {} },
        sessionStorage: {
            getItem: key => session.get(key),
            setItem: (key, value) => session.set(key, value),
            removeItem: key => session.delete(key)
        },
        XMLHttpRequest: XHR, FormData, console,
        btoa: value => Buffer.from(value).toString('base64')
    });
    vm.runInContext(script, context, { filename: 'script.js' });
    context.fetchFiles = () => refreshedPaths.push(vm.runInContext('currentPath', context));
    return {
        run: source => vm.runInContext(source, context),
        add(files) { context.handleFiles(files); },
        requests, refreshedPaths, elements,
        get maxActive() { return maxActive; },
        get tasksMarkup() { return elements.get('uploadTaskList').innerHTML; },
        get summary() { return elements.get('uploadQueueSummary').textContent; }
    };
}

function file(name, size = 512) { return { name, size }; }

test('queued destinations survive navigation and additional batches serialize', () => {
    const page = browser();
    page.run("currentPath = 'Pictures/Trip 1'; setActivePin('1234');");
    const first = file('first.jpg');
    page.add([first, file('second.jpg')]);
    assert.equal(page.requests.length, 1);
    assert.equal(page.requests[0].url, '/api/upload?path=Pictures%2FTrip%201');
    assert.equal(page.requests[0].headers['X-PIN'], '1234');
    assert.equal(page.requests[0].headers.Authorization, 'Basic YWRtaW46MTIzNA==');
    assert.equal(page.requests[0].body.fields.get('file'), first);

    page.run("currentPath = 'Documents';");
    page.add([file('notes.txt')]);
    assert.equal(page.requests.length, 1);
    page.requests[0].complete();
    assert.equal(page.requests[1].url, '/api/upload?path=Pictures%2FTrip%201');
    assert.deepEqual(page.refreshedPaths, []);
    page.requests[1].complete();
    assert.equal(page.requests[2].url, '/api/upload?path=Documents');
    page.requests[2].complete(201);
    assert.deepEqual(page.refreshedPaths, ['Documents']);
    assert.equal(page.maxActive, 1);
    assert.equal(page.summary, '3 uploaded, 0 pending, 0 failed, 0 cancelled');
    assert.match(page.tasksMarkup, /Uploaded/);
    assert.equal(page.elements.get('cancelUploadsBtn').disabled, true);
});

test('active cancellation ignores late results and retry uses the original destination', () => {
    const page = browser();
    page.run("currentPath = 'Original';");
    page.add([file('a.jpg'), file('b.jpg')]);
    const original = page.requests[0];
    original.progress(60, 100);
    page.run('cancelUpload(1)');
    assert.equal(original.aborted, true);
    assert.equal(page.requests.length, 2);
    assert.equal(page.summary, '0 uploaded, 1 pending, 0 failed, 1 cancelled');
    page.run("currentPath = 'Other'; retryUpload(1);");
    assert.equal(page.requests.length, 2);
    page.requests[1].complete();
    const retry = page.requests[2];
    assert.equal(retry.url, '/api/upload?path=Original');
    original.complete();
    assert.equal(page.requests.length, 3);
    assert.equal(page.summary, '1 uploaded, 1 pending, 0 failed, 0 cancelled');
    retry.complete();
    page.run('retryUpload(1); retryUpload(2);');
    assert.equal(page.requests.length, 3);
    assert.equal(page.summary, '2 uploaded, 0 pending, 0 failed, 0 cancelled');
    assert.equal(page.maxActive, 1);
});

test('queued cancellation never sends a request and cancel all does not start the next task', () => {
    const page = browser();
    page.add([file('a'), file('b'), file('c')]);
    page.run('cancelUpload(2)');
    assert.equal(page.requests.length, 1);
    page.run('cancelAllUploads()');
    assert.equal(page.requests.length, 1);
    assert.equal(page.requests[0].aborted, true);
    assert.equal(page.summary, '0 uploaded, 0 pending, 0 failed, 3 cancelled');
    page.run('retryUpload(2)');
    assert.equal(page.requests.length, 2);
    assert.equal(page.requests[1].body.fields.get('file').name, 'b');
    page.requests[1].complete();
    assert.equal(page.summary, '1 uploaded, 0 pending, 0 failed, 2 cancelled');
});

test('HTTP and network failures stay retryable without reuploading successful files', () => {
    const page = browser();
    page.add([file('denied'), file('offline'), file('ok')]);
    page.requests[0].complete(403, '{"error":"Upload is disabled for this session"}');
    assert.match(page.tasksMarkup, /Upload is disabled for this session/);
    page.requests[1].disconnect();
    page.requests[2].complete();
    assert.equal(page.summary, '1 uploaded, 0 pending, 2 failed, 0 cancelled');
    page.run('retryUpload(1); retryUpload(2); retryUpload(3);');
    assert.equal(page.requests.length, 4);
    page.requests[3].complete();
    page.requests[4].complete();
    assert.equal(page.requests.length, 5);
    assert.equal(page.summary, '3 uploaded, 0 pending, 0 failed, 0 cancelled');
    assert.equal(page.maxActive, 1);
});

test('sending all bytes waits for server confirmation and preserves accessible controls', () => {
    const page = browser();
    page.add([file('photo.jpg')]);
    page.requests[0].progress(0, 0);
    assert.equal(page.summary, '0 uploaded, 1 pending, 0 failed, 0 cancelled');
    assert.doesNotMatch(page.elements.get('upload-status-1').textContent, /NaN/);
    page.requests[0].progress(100, 100);
    assert.equal(page.elements.get('upload-progress-1').value, 100);
    assert.equal(page.elements.get('upload-status-1').textContent, 'Finishing...');
    assert.equal(page.summary, '0 uploaded, 1 pending, 0 failed, 0 cancelled');
    assert.match(page.tasksMarkup, /aria-label="Cancel photo.jpg"/);
    assert.match(page.tasksMarkup, /aria-label="Upload progress for photo.jpg"/);
    page.requests[0].complete();
    assert.equal(page.summary, '1 uploaded, 0 pending, 0 failed, 0 cancelled');
    page.run('clearFinishedUploads()');
    assert.equal(page.elements.get('uploadProgressContainer').style.display, 'none');
});

test('file names and server errors cannot inject markup into transfer rows', () => {
    const page = browser();
    page.add([file('photo" onfocus="bad() <img>.jpg')]);
    assert.match(page.tasksMarkup, /aria-label="Cancel photo&quot; onfocus=&quot;bad\(\) &lt;img&gt;.jpg"/);
    assert.doesNotMatch(page.tasksMarkup, /<img>/);
    page.requests[0].complete(500, '{"error":"<script>bad()</script>"}');
    assert.match(page.tasksMarkup, /&lt;script&gt;bad\(\)&lt;\/script&gt;/);
    assert.doesNotMatch(page.tasksMarkup, /<script>/);
});
