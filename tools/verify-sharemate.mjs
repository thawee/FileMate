import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const origin = process.env.SHAREMATE_URL || 'http://127.0.0.1:18080';
const pin = process.env.SHAREMATE_PIN;
assert(pin, 'Set SHAREMATE_PIN to the PIN shown by the running app');
const folder = `ShareMateVerification-${Date.now()}`;
const destination = `Download/${folder}`;
const shares = [];

async function owner(path, options = {}) {
    return fetch(`${origin}${path}`, {
        ...options,
        headers: { 'X-PIN': pin, ...options.headers },
    });
}

async function createShare(scope, mode) {
    const response = await owner('/api/shares', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...scope, mode, lifetimeMinutes: 15 }),
    });
    assert.equal(response.status, 201, await response.clone().text());
    const share = await response.json();
    assert(share.urlPath.startsWith('/share/'), 'Share uses the isolated guest namespace');
    share.urlPath = share.urlPath.replace(/\/$/, '');
    shares.push(share);
    return share;
}

async function guest(share, path, options = {}) {
    return fetch(`${origin}${share.urlPath}${path}`, options);
}

async function seed(name, content, contentType = 'application/octet-stream') {
    const query = new URLSearchParams({ path: destination, filename: name });
    const response = await owner(`/api/upload?${query}`, {
        method: 'POST', headers: { 'Content-Type': contentType }, body: content,
    });
    assert.equal(response.status, 200, await response.text());
}

try {
    const mkdir = await owner(`/api/mkdir?${new URLSearchParams({ path: 'Download', name: folder })}`, { method: 'POST' });
    assert.equal(mkdir.status, 200, await mkdir.text());
    await seed('original.txt', 'original-content');
    await seed('private.txt', 'not-selected');
    await seed('photo.png', Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6XzQAAAAASUVORK5CYII=', 'base64'), 'image/png');

    for (const path of ['/api/files', '/api/auth/x/api/files', '/api/auth/x/api/delete', '/api/auth/x/api/download/original.txt']) {
        const response = await fetch(`${origin}${path}`);
        assert([401, 404].includes(response.status), `Unauthenticated composed owner route denied, ${path}`);
    }
    console.log('PASS owner route isolation');

    const selected = await createShare({ paths: [`/${destination}/original.txt`] }, 'DOWNLOAD');
    const items = await guest(selected, '/api/items');
    assert.equal(items.status, 200);
    const selectedJson = await items.json();
    const selectedItems = Array.isArray(selectedJson) ? selectedJson : selectedJson.items;
    assert.deepEqual(selectedItems.map(item => item.name), ['original.txt']);
    const selectedPath = selectedItems[0].path;
    assert.equal(await (await guest(selected, `/download?${new URLSearchParams({ path: selectedPath })}`)).text(), 'original-content');
    assert([403, 404].includes((await guest(selected, '/download?path=private.txt')).status));
    assert([403, 404].includes((await guest(selected, '/api/auth/x/api/files')).status));
    assert([403, 404].includes((await guest(selected, '/upload?name=no.txt', { method: 'POST', body: 'no' })).status));
    console.log('PASS selected files cannot expose siblings or owner APIs');

    const upload = await createShare({ folder: `/${destination}` }, 'UPLOAD');
    const uploadItems = await guest(upload, '/api/items');
    assert.equal(uploadItems.status, 200);
    assert.deepEqual((await uploadItems.json()).items, [], 'Upload-only guests cannot see existing filenames');
    assert([403, 404].includes((await guest(upload, '/download?path=original.txt')).status));
    const conflict = await guest(upload, '/upload?name=original.txt', { method: 'POST', body: 'replacement' });
    assert.equal(conflict.status, 409);
    const received = await guest(upload, '/upload?name=received.txt', { method: 'POST', body: 'received-content' });
    assert.equal(received.status, 201, await received.text());
    console.log('PASS upload-only access and filename conflict');

    const folderShare = await createShare({ folder: `/${destination}` }, 'DOWNLOAD_AND_UPLOAD');
    assert.equal(await (await guest(folderShare, '/download?path=original.txt')).text(), 'original-content');
    assert.equal(await (await guest(folderShare, '/download?path=received.txt')).text(), 'received-content');
    for (const path of ['../private.txt', '../../Download', '/../../Download', '..\\private.txt']) {
        assert([400, 403, 404].includes((await guest(folderShare, `/download?${new URLSearchParams({ path })}`)).status));
    }
    const simultaneous = await Promise.all(['first', 'second'].map(body => guest(upload, '/upload?name=concurrent.txt', { method: 'POST', body })));
    assert.deepEqual(simultaneous.map(response => response.status).sort(), [201, 409]);
    console.log('PASS folder confinement and concurrent upload conflict');

    const pageResponse = await guest(folderShare, '');
    assert.equal(pageResponse.status, 200);
    assert.match(pageResponse.headers.get('cache-control') || '', /no-store/);
    assert.equal(pageResponse.headers.get('referrer-policy'), 'no-referrer');

    await guest(folderShare, '/upload?name=attack.html', {
        method: 'POST', body: '<script src="download?path=attack.js"></script>',
    }).then(response => assert.equal(response.status, 201));
    await guest(folderShare, '/upload?name=attack.js', {
        method: 'POST', body: 'window.attackExecuted=true; fetch("/api/files");',
    }).then(response => assert.equal(response.status, 201));

    if (process.env.SHAREMATE_PLAYWRIGHT) {
        const require = createRequire(import.meta.url);
        const { chromium } = require(process.env.SHAREMATE_PLAYWRIGHT);
        const browser = await chromium.launch({ headless: true, executablePath: process.env.SHAREMATE_CHROME });
        try {
            const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
            const page = await context.newPage();
            const errors = [];
            page.on('pageerror', error => errors.push(error.message));
            await page.goto(`${origin}${folderShare.urlPath}`);
            await page.getByText('original.txt', { exact: true }).waitFor();
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'Guest page fits a phone viewport');
            assert.deepEqual(errors, [], 'Guest page has no JavaScript errors');
            if (process.env.SHAREMATE_SCREENSHOT) await page.screenshot({ path: process.env.SHAREMATE_SCREENSHOT, fullPage: true });
            console.log('PASS real guest browser at phone width');
            await page.getByRole('button', { name: 'Present photos' }).click();
            await page.waitForFunction(() => document.getElementById('photo').naturalWidth > 0);
            await page.getByRole('button', { name: 'Close', exact: true }).click();
            console.log('PASS guest slideshow displays a real served image');

            await context.addCookies([{ name: 'pin', value: pin, url: origin }]);
            const hostile = await context.newPage();
            const ownerRequests = [];
            hostile.on('request', request => {
                if (new URL(request.url()).pathname === '/api/files') ownerRequests.push(request.url());
            });
            const downloadEvent = hostile.waitForEvent('download', { timeout: 5000 }).catch(() => null);
            try {
                await hostile.goto(`${origin}${folderShare.urlPath}/download?path=attack.html`);
            } catch (error) {
                assert.match(error.message, /download|ERR_ABORTED/i);
            }
            const download = await downloadEvent;
            if (!download) assert.equal(await hostile.evaluate(() => window.attackExecuted === true), false, 'Shared active content cannot run scripts');
            assert.deepEqual(ownerRequests, [], 'Shared active content cannot call authenticated owner APIs');
            console.log('PASS active content cannot escape guest access with an owner cookie');
        } finally {
            await browser.close();
        }
    }

    const revoke = await owner(`/api/shares/${selected.id}`, { method: 'DELETE' });
    assert.equal(revoke.status, 200);
    assert([404, 410].includes((await guest(selected, '/api/items')).status));
    console.log('PASS revoked guest route denied');
} finally {
    for (const share of shares) await owner(`/api/shares/${share.id}`, { method: 'DELETE' });
    const response = await owner(`/api/delete?${new URLSearchParams({ path: 'Download', name: folder })}`, { method: 'POST' });
    if (response.status !== 200) console.error('Verification fixture cleanup failed', response.status);
}
