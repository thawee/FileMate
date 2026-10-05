'use strict';
const base = location.pathname.replace(/\/$/, '');
const $ = id => document.getElementById(id);
let currentPath = '', photos = [], photoIndex = 0, photoTimer;
const endpoint = (route, params = {}) => `${base}/${route}?${new URLSearchParams(params)}`;
async function readJson(response) {
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { throw new Error('Unexpected server response'); }
  if (!response.ok) throw new Error(data.error || 'Request failed');
  return data;
}
async function load() {
  try {
    const data = await readJson(await fetch(endpoint('api/items', {path: currentPath}), {cache: 'no-store'}));
    $('title').textContent = data.label;
    $('details').textContent = `${data.mode === 'UPLOAD' ? 'Send files to this folder' : 'Open or save these files'} · Expires ${new Date(data.expiresAtMillis).toLocaleString()}`;
    $('upload').hidden = data.mode === 'DOWNLOAD';
    $('back').hidden = !currentPath;
    $('items').replaceChildren();
    photos = data.items.filter(item => !item.isDirectory && /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(item.name));
    $('present').hidden = !photos.length;
    for (const item of data.items) {
      const row = document.createElement('div'); row.className = 'item';
      const label = document.createElement(item.isDirectory ? 'button' : 'a');
      label.textContent = item.name;
      if (item.isDirectory) label.onclick = () => { currentPath = item.path; load(); };
      else { label.href = endpoint('download', {path: item.path}); label.target = '_blank'; label.rel = 'noreferrer'; }
      row.append(label);
      if (!item.isDirectory) {
        const save = document.createElement('a'); save.textContent = 'Save'; save.href = label.href; save.download = item.name; row.append(save);
      }
      $('items').append(row);
    }
  } catch (error) { $('status').textContent = error.message; $('upload').hidden = true; $('items').replaceChildren(); }
}
$('back').onclick = () => { currentPath = currentPath.split('/').slice(0, -1).join('/'); load(); };
$('send').onclick = async () => {
  const files = [...$('pick').files];
  if (!files.length) { $('status').textContent = 'Choose files to upload'; return; }
  $('send').disabled = true;
  const results = [];
  for (const file of files) {
    try {
      if (file.size > 20 * 1024 * 1024) throw new Error('Exceeds 20 MiB limit');
      $('status').textContent = `Uploading ${file.name}`;
      await readJson(await fetch(endpoint('upload', {name: file.name}), {method: 'POST', body: file}));
      results.push(`${file.name}: uploaded`);
    } catch (error) { results.push(`${file.name}: ${error.message}`); }
  }
  $('send').disabled = false; $('pick').value = ''; await load(); $('status').textContent = results.join('\n');
};
function showPhoto() {
  const photo = photos[photoIndex];
  $('photo').src = endpoint('download', {path: photo.path}); $('photo').alt = photo.name; $('caption').textContent = photo.name;
}
$('present').onclick = () => { photoIndex = 0; showPhoto(); $('slideshow').showModal(); photoTimer = setInterval(() => { photoIndex = (photoIndex + 1) % photos.length; showPhoto(); }, 5000); };
$('close').onclick = () => $('slideshow').close();
$('slideshow').onclose = () => { clearInterval(photoTimer); $('photo').removeAttribute('src'); };
$('previous').onclick = () => { photoIndex = (photoIndex + photos.length - 1) % photos.length; showPhoto(); };
$('next').onclick = () => { photoIndex = (photoIndex + 1) % photos.length; showPhoto(); };
load();
