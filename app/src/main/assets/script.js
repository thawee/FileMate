let currentPath = '';
let currentFiles = [];
let sortCol = 'name';
let sortDesc = false;
let searchQuery = '';

function showToast(message, type = 'info', duration = 3500) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '❌';

    toast.innerHTML = `<span>${icon}</span><span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('toast-out');
        setTimeout(() => {
            if (toast.parentNode) toast.parentNode.removeChild(toast);
        }, 300);
    }, duration);
}

function copyLink(fileUrl, fileName) {
    const fullUrl = window.location.origin + fileUrl;
    navigator.clipboard.writeText(fullUrl).then(() => {
        showToast(`Copied link for "${fileName}"`, 'success');
    }).catch(() => {
        showToast(`Failed to copy link`, 'error');
    });
}

function handleSearch() {
    const input = document.getElementById('searchInput');
    const clearBtn = document.getElementById('clearSearchBtn');
    searchQuery = input ? input.value.trim().toLowerCase() : '';
    if (clearBtn) clearBtn.style.display = searchQuery ? 'block' : 'none';
    filterAndRenderFiles();
}

function clearSearch() {
    const input = document.getElementById('searchInput');
    const clearBtn = document.getElementById('clearSearchBtn');
    if (input) input.value = '';
    if (clearBtn) clearBtn.style.display = 'none';
    searchQuery = '';
    filterAndRenderFiles();
}

function handleSort(col) {
    if (sortCol === col) {
        sortDesc = !sortDesc;
    } else {
        sortCol = col;
        sortDesc = false;
    }
    updateSortIndicators();
    filterAndRenderFiles();
}

function updateSortIndicators() {
    const getIndicator = (col) => sortCol === col ? (sortDesc ? ' ▼' : ' ▲') : '';
    document.getElementById('thName').textContent = 'Name' + getIndicator('name');
    document.getElementById('thSize').textContent = 'Size' + getIndicator('size');
    document.getElementById('thTime').textContent = 'Modified' + getIndicator('time');
}

function filterAndRenderFiles() {
    let filesToRender = currentFiles;
    if (searchQuery) {
        filesToRender = currentFiles.filter(f => f.name.toLowerCase().includes(searchQuery));
    }

    filesToRender.sort((a, b) => {
        if (a.isDirectory !== b.isDirectory) {
            return a.isDirectory ? -1 : 1;
        }
        let result = 0;
        if (sortCol === 'name') {
            result = a.name.localeCompare(b.name);
        } else if (sortCol === 'size') {
            result = a.size - b.size;
        } else if (sortCol === 'time') {
            result = a.lastModified - b.lastModified;
        }
        return sortDesc ? -result : result;
    });

    renderFiles(filesToRender);
}

function formatDate(timestamp) {
    if (!timestamp) return '--';
    const d = new Date(timestamp);
    const pad = (n) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

let imagePlaylist = [];
let currentPlaylistIndex = -1;
let slideshowTimer = null;
let isSlideshowPlaying = false;

function escapeJsArg(text) {
    return text.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

async function openPreview(fileName, fileUrl) {
    const modal = document.getElementById('previewModal');
    const title = document.getElementById('previewTitle');
    const body = document.getElementById('previewBody');
    const newTabLink = document.getElementById('previewNewTab');
    const controls = document.getElementById('slideshowControls');
    const counter = document.getElementById('slideshowCounter');

    title.textContent = fileName;
    newTabLink.href = fileUrl;
    body.innerHTML = '';

    const ext = fileName.split('.').pop().toLowerCase();
    const isImage = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].includes(ext);
    const isVideo = ['mp4', 'webm', 'mkv', 'mov', 'avi'].includes(ext);
    const isAudio = ['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac', 'opus'].includes(ext);
    const isTextCode = ['txt', 'json', 'md', 'js', 'html', 'css', 'py', 'java', 'kt', 'sh', 'xml', 'yml', 'yaml', 'c', 'cpp', 'h', 'csv', 'tsv', 'log', 'properties', 'gradle', 'kts', 'rs', 'go', 'ts'].includes(ext);

    if (isImage) {
        if (imagePlaylist.length > 0) {
            const idx = imagePlaylist.findIndex(item => item.name === fileName);
            currentPlaylistIndex = idx !== -1 ? idx : 0;
            if (controls) controls.style.display = 'inline-flex';
            if (counter) {
                counter.style.display = 'inline-block';
                counter.textContent = `${currentPlaylistIndex + 1} / ${imagePlaylist.length}`;
            }
        } else {
            if (controls) controls.style.display = 'none';
            if (counter) counter.style.display = 'none';
        }

        const img = document.createElement('img');
        img.src = fileUrl;
        img.alt = fileName;
        img.style.maxHeight = '75vh';
        img.style.maxWidth = '100%';
        img.style.objectFit = 'contain';
        body.appendChild(img);

        // Floating Overlay Navigation Arrows for Images
        if (imagePlaylist.length > 1) {
            const prevArrow = document.createElement('div');
            prevArrow.className = 'preview-nav-arrow prev';
            prevArrow.innerHTML = '‹';
            prevArrow.title = 'Previous Image (←)';
            prevArrow.onclick = (e) => { e.stopPropagation(); prevImage(); };

            const nextArrow = document.createElement('div');
            nextArrow.className = 'preview-nav-arrow next';
            nextArrow.innerHTML = '›';
            nextArrow.title = 'Next Image (→)';
            nextArrow.onclick = (e) => { e.stopPropagation(); nextImage(); };

            body.appendChild(prevArrow);
            body.appendChild(nextArrow);
        }
    } else {
        if (controls) controls.style.display = 'none';
        if (counter) counter.style.display = 'none';
        stopSlideshow();

        if (isVideo) {
            const video = document.createElement('video');
            video.src = fileUrl;
            video.controls = true;
            video.autoplay = true;
            body.appendChild(video);
        } else if (isAudio) {
            const audio = document.createElement('audio');
            audio.src = fileUrl;
            audio.controls = true;
            audio.autoplay = true;
            body.appendChild(audio);
        } else if (isTextCode) {
            body.innerHTML = '<div style="padding: 2rem; color: var(--text-muted);">Loading content...</div>';
            try {
                const resp = await fetch(fileUrl);
                if (!resp.ok) throw new Error('Failed to load file');
                const text = await resp.text();

                const container = document.createElement('div');
                container.className = 'code-preview-container';
                const pre = document.createElement('pre');
                pre.className = 'code-preview';
                const code = document.createElement('code');
                code.textContent = text.length > 500000 ? text.substring(0, 500000) + '\n... [File truncated]' : text;
                pre.appendChild(code);
                container.appendChild(pre);
                body.innerHTML = '';
                body.appendChild(container);
            } catch (e) {
                body.innerHTML = `<div style="padding: 2rem; color: var(--danger-color);">Error loading preview: ${escapeHtml(e.message)}</div>`;
            }
        } else {
            const iframe = document.createElement('iframe');
            iframe.src = fileUrl;
            iframe.style.width = '100%';
            iframe.style.height = '65vh';
            iframe.style.border = 'none';
            iframe.style.borderRadius = '8px';
            body.appendChild(iframe);
        }
    }

    modal.style.display = 'flex';
}

function showPlaylistImage(index) {
    if (!imagePlaylist || imagePlaylist.length === 0) return;
    currentPlaylistIndex = (index + imagePlaylist.length) % imagePlaylist.length;
    const item = imagePlaylist[currentPlaylistIndex];
    openPreview(item.name, item.url);
}

function prevImage() {
    showPlaylistImage(currentPlaylistIndex - 1);
}

function nextImage() {
    showPlaylistImage(currentPlaylistIndex + 1);
}

function toggleSlideshowPlay() {
    const playBtn = document.getElementById('slideshowPlayBtn');
    if (isSlideshowPlaying) {
        stopSlideshow();
    } else {
        isSlideshowPlaying = true;
        if (playBtn) playBtn.textContent = '⏸ Pause';
        slideshowTimer = setInterval(() => {
            nextImage();
        }, 3000);
    }
}

function stopSlideshow() {
    if (slideshowTimer) {
        clearInterval(slideshowTimer);
        slideshowTimer = null;
    }
    isSlideshowPlaying = false;
    const playBtn = document.getElementById('slideshowPlayBtn');
    if (playBtn) playBtn.textContent = '▶ Play';
}

function toggleFullscreenPreview() {
    const modal = document.getElementById('previewModal');
    if (!modal) return;
    if (!document.fullscreenElement) {
        modal.requestFullscreen().catch(err => {
            showToast('Fullscreen request failed', 'error');
        });
    } else {
        document.exitFullscreen();
    }
}

async function quickDeleteCurrentImage() {
    if (!imagePlaylist || imagePlaylist.length === 0 || currentPlaylistIndex < 0) return;

    const currentImg = imagePlaylist[currentPlaylistIndex];
    const fileName = currentImg.name;

    if (!confirm(`Delete "${fileName}" and advance to next image?`)) return;

    try {
        const response = await fetch(`/api/delete?name=${encodeURIComponent(fileName)}&path=${encodeURIComponent(currentPath)}`, {
            method: 'POST'
        });

        const result = await response.json();
        if (!response.ok || !result.success) {
            throw new Error(result.error || 'Delete failed');
        }

        showToast(`Deleted ${fileName}`, 'success');

        // Remove from imagePlaylist and currentFiles
        imagePlaylist.splice(currentPlaylistIndex, 1);
        currentFiles = currentFiles.filter(f => f.name !== fileName);

        // Refresh file list rendering in background
        filterAndRenderFiles();

        if (imagePlaylist.length === 0) {
            closePreview();
        } else {
            if (currentPlaylistIndex >= imagePlaylist.length) {
                currentPlaylistIndex = 0;
            }
            showPlaylistImage(currentPlaylistIndex);
        }
    } catch (e) {
        showToast(`Error deleting file: ${e.message}`, 'error');
    }
}

function closePreview() {
    stopSlideshow();
    const modal = document.getElementById('previewModal');
    const body = document.getElementById('previewBody');
    if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
    }
    if (body) body.innerHTML = '';
    if (modal) modal.style.display = 'none';
}

// Global Keyboard Navigation for Slideshow
document.addEventListener('keydown', (e) => {
    const modal = document.getElementById('previewModal');
    if (!modal || modal.style.display !== 'flex') return;

    if (['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) return;

    if (e.key === 'ArrowLeft') {
        e.preventDefault();
        prevImage();
    } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        nextImage();
    } else if (e.key === ' ') {
        e.preventDefault();
        toggleSlideshowPlay();
    } else if (e.key === 'f' || e.key === 'F') {
        e.preventDefault();
        toggleFullscreenPreview();
    } else if (e.key === 'Delete') {
        e.preventDefault();
        quickDeleteCurrentImage();
    }
});

async function deleteItem(fileName) {
    if (!confirm(`Are you sure you want to delete "${fileName}"?`)) {
        return;
    }

    try {
        const response = await fetch(`/api/delete?name=${encodeURIComponent(fileName)}&path=${encodeURIComponent(currentPath)}`, {
            method: 'POST'
        });
        if (!response.ok) throw new Error('Failed to delete item');
        const res = await response.json();
        if (res.status === 'ok') {
            showToast(`Deleted "${fileName}"`, 'success');
            fetchFiles();
        } else {
            showToast('Delete failed', 'error');
        }
    } catch (error) {
        console.error('Error deleting item:', error);
        showToast('Error deleting item: ' + error.message, 'error');
    }
}

let movingFileName = '';
let movingFromPath = '';
let moveTargetPath = '';

async function openMoveModal(fileName) {
    movingFileName = fileName;
    movingFromPath = currentPath;
    moveTargetPath = '';

    document.getElementById('moveFileName').textContent = fileName;
    document.getElementById('moveModal').style.display = 'flex';

    await loadMoveFolders();
}

function closeMoveModal() {
    const modal = document.getElementById('moveModal');
    if (modal) modal.style.display = 'none';
    movingFileName = '';
}

async function loadMoveFolders() {
    const listEl = document.getElementById('moveFolderList');
    const breadcrumbEl = document.getElementById('moveBreadcrumb');
    const confirmBtn = document.getElementById('confirmMoveBtn');

    listEl.innerHTML = '<div style="padding: 1rem; text-align: center; color: var(--text-muted);">Loading folders...</div>';

    breadcrumbEl.innerHTML = 'Target: <b>' + (moveTargetPath === '' ? '🏠 Home' : '🏠 Home/' + escapeHtml(moveTargetPath)) + '</b>';

    if (moveTargetPath === movingFromPath) {
        confirmBtn.disabled = true;
        confirmBtn.style.opacity = '0.5';
    } else {
        confirmBtn.disabled = false;
        confirmBtn.style.opacity = '1';
    }

    try {
        const response = await fetch('/api/files?path=' + encodeURIComponent(moveTargetPath));
        if (!response.ok) throw new Error('Failed to load folders');
        const files = await response.json();

        listEl.innerHTML = '';

        if (moveTargetPath !== '') {
            const backItem = document.createElement('div');
            backItem.className = 'move-folder-item';
            backItem.innerHTML = '<span>⬅</span> <b>.. (Up one level)</b>';
            backItem.onclick = () => {
                const parts = moveTargetPath.split('/');
                parts.pop();
                moveTargetPath = parts.join('/');
                loadMoveFolders();
            };
            listEl.appendChild(backItem);
        }

        const folders = files.filter(f => f.isDirectory);

        if (folders.length === 0 && moveTargetPath === '') {
            listEl.innerHTML = '<div style="padding: 1rem; text-align: center; color: var(--text-muted);">No subfolders found</div>';
            return;
        }

        folders.forEach(folder => {
            const item = document.createElement('div');
            item.className = 'move-folder-item';
            item.innerHTML = `<span>📁</span> <span>${escapeHtml(folder.name)}</span>`;
            item.onclick = () => {
                moveTargetPath = moveTargetPath === '' ? folder.name : moveTargetPath + '/' + folder.name;
                loadMoveFolders();
            };
            listEl.appendChild(item);
        });

        if (folders.length === 0 && moveTargetPath !== '') {
            const emptyNotice = document.createElement('div');
            emptyNotice.style.cssText = 'padding: 1rem; text-align: center; color: var(--text-muted); font-size: 0.85rem;';
            emptyNotice.textContent = 'No nested subfolders here.';
            listEl.appendChild(emptyNotice);
        }

    } catch (error) {
        console.error('Error loading move folders:', error);
        listEl.innerHTML = '<div style="padding: 1rem; text-align: center; color: var(--danger-color);">Failed to load folders</div>';
    }
}

async function confirmMove() {
    if (!movingFileName) return;

    try {
        const isBatch = movingFileName.includes(',');
        const paramKey = isBatch ? 'names' : 'name';
        const response = await fetch(`/api/move?${paramKey}=${encodeURIComponent(movingFileName)}&fromPath=${encodeURIComponent(movingFromPath)}&targetPath=${encodeURIComponent(moveTargetPath)}`, {
            method: 'POST'
        });

        if (!response.ok) {
            const errText = await response.text();
            throw new Error(errText || 'Failed to move file');
        }

        const res = await response.json();
        if (res.status === 'ok') {
            showToast(`Moved ${res.moved} item(s)`, 'success');
            closeMoveModal();
            clearSelection();
            fetchFiles();
        } else {
            showToast('Move failed', 'error');
        }
    } catch (error) {
        console.error('Error moving file:', error);
        showToast('Error moving file: ' + error.message, 'error');
    }
}

function getSelectedFiles() {
    const checkboxes = document.querySelectorAll('.item-checkbox:checked');
    const files = [];
    checkboxes.forEach(cb => {
        files.push({
            name: cb.getAttribute('data-name'),
            isDir: cb.getAttribute('data-is-dir') === 'true'
        });
    });
    return files;
}

function updateBatchToolbar() {
    const selected = getSelectedFiles();
    const toolbar = document.getElementById('batchToolbar');
    const countEl = document.getElementById('batchCount');
    const selectAllBtn = document.getElementById('selectAllBtn');
    const allCheckboxes = document.querySelectorAll('.item-checkbox');

    const allChecked = allCheckboxes.length > 0 && Array.from(allCheckboxes).every(cb => cb.checked);
    const master = document.getElementById('selectAllCheckbox');
    if (master) master.checked = allChecked;

    if (selectAllBtn) {
        selectAllBtn.textContent = allChecked ? '☐ Deselect All' : '☑ Select All';
    }

    if (selected.length > 0) {
        if (countEl) countEl.textContent = selected.length;
        if (toolbar) toolbar.style.display = 'flex';
    } else {
        if (toolbar) toolbar.style.display = 'none';
    }
}

function toggleSelectAll(master) {
    const checkboxes = document.querySelectorAll('.item-checkbox');
    checkboxes.forEach(cb => {
        cb.checked = master.checked;
    });
    updateBatchToolbar();
}

function toggleSelectAllBtn() {
    const checkboxes = document.querySelectorAll('.item-checkbox');
    if (checkboxes.length === 0) return;

    const allChecked = Array.from(checkboxes).every(cb => cb.checked);
    checkboxes.forEach(cb => {
        cb.checked = !allChecked;
    });

    updateBatchToolbar();
}

function clearSelection() {
    const master = document.getElementById('selectAllCheckbox');
    if (master) master.checked = false;
    const checkboxes = document.querySelectorAll('.item-checkbox');
    checkboxes.forEach(cb => {
        cb.checked = false;
    });
    updateBatchToolbar();
}

async function batchDeleteSelected() {
    const selected = getSelectedFiles();

    if (selected.length === 0) {
        showToast('No items selected for deletion.', 'info');
        return;
    }

    const namesList = selected.map(f => f.name).join(',');
    if (!confirm(`Are you sure you want to delete ${selected.length} selected item(s)?`)) {
        return;
    }

    try {
        const response = await fetch(`/api/delete?names=${encodeURIComponent(namesList)}&path=${encodeURIComponent(currentPath)}`, {
            method: 'POST'
        });
        if (!response.ok) throw new Error('Failed to delete items');
        const res = await response.json();
        if (res.status === 'ok') {
            showToast(`Deleted ${res.deleted} item(s)`, 'success');
            clearSelection();
            fetchFiles();
        } else {
            showToast('Batch delete completed with issues', 'error');
        }
    } catch (error) {
        console.error('Error in batch delete:', error);
        showToast('Error deleting items: ' + error.message, 'error');
    }
}

async function openBatchMoveModal() {
    const selected = getSelectedFiles();

    if (selected.length === 0) {
        showToast('No items selected for moving.', 'info');
        return;
    }

    movingFileName = selected.map(f => f.name).join(',');
    movingFromPath = currentPath;
    moveTargetPath = '';

    document.getElementById('moveFileName').textContent = `${selected.length} selected item(s)`;
    document.getElementById('moveModal').style.display = 'flex';

    await loadMoveFolders();
}

function openMkdirModal() {
    const input = document.getElementById('newFolderName');
    if (input) input.value = '';
    const modal = document.getElementById('mkdirModal');
    if (modal) modal.style.display = 'flex';
    setTimeout(() => { if (input) input.focus(); }, 100);
}

function closeMkdirModal() {
    const modal = document.getElementById('mkdirModal');
    if (modal) modal.style.display = 'none';
}

async function createFolder() {
    const nameInput = document.getElementById('newFolderName');
    const folderName = nameInput ? nameInput.value.trim() : '';

    if (!folderName) {
        showToast('Please enter a folder name', 'info');
        return;
    }

    try {
        const response = await fetch(`/api/mkdir?name=${encodeURIComponent(folderName)}&path=${encodeURIComponent(currentPath)}`, {
            method: 'POST'
        });

        if (!response.ok) {
            const errText = await response.text();
            throw new Error(errText || 'Failed to create folder');
        }

        const res = await response.json();
        if (res.status === 'ok') {
            showToast(`Created folder "${folderName}"`, 'success');
            closeMkdirModal();
            fetchFiles();
        } else {
            showToast('Create folder failed', 'error');
        }
    } catch (error) {
        console.error('Error creating folder:', error);
        showToast('Error creating folder: ' + error.message, 'error');
    }
}

document.addEventListener('keydown', (e) => {
    const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
    if (activeTag === 'input' || activeTag === 'textarea') {
        if (e.key === 'Escape') {
            closePreview();
            closeMoveModal();
            closeMkdirModal();
            closeToolsModal();
        }
        return;
    }

    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a') {
        e.preventDefault();
        toggleSelectAllBtn();
    } else if (e.key === '/') {
        e.preventDefault();
        const searchInput = document.getElementById('searchInput');
        if (searchInput) searchInput.focus();
    } else if (e.key === 'Escape') {
        closePreview();
        closeMoveModal();
        closeMkdirModal();
        closeToolsModal();
        clearSelection();
    } else if (e.key === 'Delete') {
        const selected = getSelectedFiles();
        if (selected.length > 0) {
            batchDeleteSelected();
        }
    }
});

document.addEventListener('click', (e) => {
    const previewModal = document.getElementById('previewModal');
    if (e.target === previewModal) closePreview();

    const moveModal = document.getElementById('moveModal');
    if (e.target === moveModal) closeMoveModal();

    const mkdirModal = document.getElementById('mkdirModal');
    if (e.target === mkdirModal) closeMkdirModal();

    const toolsModal = document.getElementById('toolsModal');
    if (e.target === toolsModal) closeToolsModal();
});

// Storage & Maintenance Tools Logic
function openToolsModal() {
    const modal = document.getElementById('toolsModal');
    if (modal) modal.style.display = 'flex';
    loadStorageStats();
}

function closeToolsModal() {
    const modal = document.getElementById('toolsModal');
    if (modal) modal.style.display = 'none';
}

async function runCleanEmptyFolders() {
    if (!confirm('Scan and delete all empty folders recursively in current directory?')) return;

    try {
        const response = await fetch(`/api/clean-empty-folders?path=${encodeURIComponent(currentPath)}`, {
            method: 'POST'
        });
        if (!response.ok) throw new Error('Failed to clean empty folders');
        const res = await response.json();
        if (res.status === 'ok') {
            if (res.removedCount > 0) {
                showToast(`Removed ${res.removedCount} empty folder(s)`, 'success');
            } else {
                showToast('No empty folders found', 'info');
            }
            fetchFiles();
            loadStorageStats();
        } else {
            showToast('Clean empty folders failed', 'error');
        }
    } catch (error) {
        console.error('Error cleaning empty folders:', error);
        showToast('Error cleaning empty folders: ' + error.message, 'error');
    }
}

async function runCleanJunkFiles() {
    if (!confirm('Purge all OS junk files (.DS_Store, Thumbs.db, .tmp, etc.) recursively?')) return;

    try {
        const response = await fetch(`/api/clean-junk-files?path=${encodeURIComponent(currentPath)}`, {
            method: 'POST'
        });
        if (!response.ok) throw new Error('Failed to clean junk files');
        const res = await response.json();
        if (res.status === 'ok') {
            if (res.removedCount > 0) {
                showToast(`Purged ${res.removedCount} junk file(s), freed ${formatBytes(res.freedBytes)}`, 'success');
            } else {
                showToast('No OS junk files found', 'info');
            }
            fetchFiles();
            loadStorageStats();
        } else {
            showToast('Purge junk files failed', 'error');
        }
    } catch (error) {
        console.error('Error purging junk files:', error);
        showToast('Error purging junk files: ' + error.message, 'error');
    }
}

async function loadStorageStats() {
    const totalText = document.getElementById('storageTotalText');
    const topFilesList = document.getElementById('topFilesList');
    if (totalText) totalText.textContent = 'Analyzing storage usage...';
    if (topFilesList) topFilesList.innerHTML = '<div style="padding: 0.5rem; color: var(--text-muted); text-align: center;">Scanning files...</div>';

    try {
        const response = await fetch(`/api/storage-stats?path=${encodeURIComponent(currentPath)}`);
        if (!response.ok) throw new Error('Failed to fetch storage stats');
        const stats = await response.json();

        if (totalText) {
            totalText.textContent = `Total Used: ${formatBytes(stats.totalSize)} across ${stats.totalFiles} file(s) & ${stats.totalFolders} folder(s). Found ${stats.emptyFoldersCount} empty folder(s) & ${stats.junkFilesCount} junk file(s).`;
        }

        const total = stats.totalSize || 1;
        const cats = stats.categories || {};

        const setSeg = (id, size) => {
            const el = document.getElementById(id);
            if (el) el.style.width = Math.min(100, Math.max(size > 0 ? 1 : 0, (size / total) * 100)) + '%';
        };

        setSeg('segImages', cats.images ? cats.images.size : 0);
        setSeg('segVideos', cats.videos ? cats.videos.size : 0);
        setSeg('segAudio', cats.audio ? cats.audio.size : 0);
        setSeg('segDocs', cats.docs ? cats.docs.size : 0);
        setSeg('segArchives', cats.archives ? cats.archives.size : 0);
        setSeg('segOther', cats.other ? cats.other.size : 0);

        const setLeg = (id, size, count) => {
            const el = document.getElementById(id);
            if (el) el.textContent = `${formatBytes(size)} (${count})`;
        };

        setLeg('legImages', cats.images ? cats.images.size : 0, cats.images ? cats.images.count : 0);
        setLeg('legVideos', cats.videos ? cats.videos.size : 0, cats.videos ? cats.videos.count : 0);
        setLeg('legAudio', cats.audio ? cats.audio.size : 0, cats.audio ? cats.audio.count : 0);
        setLeg('legDocs', cats.docs ? cats.docs.size : 0, cats.docs ? cats.docs.count : 0);
        setLeg('legArchives', cats.archives ? cats.archives.size : 0, cats.archives ? cats.archives.count : 0);
        setLeg('legOther', cats.other ? cats.other.size : 0, cats.other ? cats.other.count : 0);

        if (topFilesList) {
            topFilesList.innerHTML = '';
            const topFiles = stats.topFiles || [];
            if (topFiles.length === 0) {
                topFilesList.innerHTML = '<div style="padding: 0.5rem; color: var(--text-muted); text-align: center;">No files found</div>';
                return;
            }

            topFiles.forEach(file => {
                const fileDir = file.path.includes('/') ? file.path.substring(0, file.path.lastIndexOf('/')) : '';
                const escapedName = escapeJsArg(file.name);
                const escapedDir = escapeJsArg(fileDir);

                const item = document.createElement('div');
                item.className = 'top-file-item';
                item.innerHTML = `
                    <div class="top-file-info">
                        <span class="top-file-name" title="${escapeHtml(file.name)}">${escapeHtml(file.name)}</span>
                        <span class="top-file-path" title="${escapeHtml(file.path)}">${escapeHtml(file.path)}</span>
                    </div>
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                        <span style="font-weight: 600; color: var(--primary-color);">${formatBytes(file.size)}</span>
                        <button class="btn-action delete-btn" style="padding: 0.2rem 0.4rem; font-size: 0.75rem;" onclick="deleteTopFile('${escapedName}', '${escapedDir}')" title="Delete File">🗑️</button>
                    </div>
                `;
                topFilesList.appendChild(item);
            });
        }
    } catch (error) {
        console.error('Error loading storage stats:', error);
        if (totalText) totalText.textContent = 'Failed to analyze storage stats';
    }
}

async function deleteTopFile(fileName, parentPath) {
    if (!confirm(`Delete largest file "${fileName}"?`)) return;

    try {
        const response = await fetch(`/api/delete?name=${encodeURIComponent(fileName)}&path=${encodeURIComponent(parentPath)}`, {
            method: 'POST'
        });
        if (!response.ok) throw new Error('Failed to delete file');
        const res = await response.json();
        if (res.status === 'ok') {
            showToast(`Deleted "${fileName}"`, 'success');
            fetchFiles();
            loadStorageStats();
        } else {
            showToast('Delete failed', 'error');
        }
    } catch (error) {
        console.error('Error deleting file:', error);
        showToast('Error deleting file: ' + error.message, 'error');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    fetchSystemInfo();
    fetchFiles();

    const fileInput = document.getElementById('fileInput');
    fileInput.addEventListener('change', handleFileUpload);

    document.getElementById('refreshBtn').addEventListener('click', fetchFiles);

    const dragOverlay = document.getElementById('dragOverlay');
    let dragCounter = 0;

    window.addEventListener('dragenter', (e) => {
        e.preventDefault();
        dragCounter++;
        dragOverlay.style.display = 'flex';
    });

    window.addEventListener('dragleave', (e) => {
        e.preventDefault();
        dragCounter--;
        if (dragCounter === 0) {
            dragOverlay.style.display = 'none';
        }
    });

    window.addEventListener('dragover', (e) => {
        e.preventDefault();
    });

    window.addEventListener('drop', (e) => {
        e.preventDefault();
        dragCounter = 0;
        dragOverlay.style.display = 'none';

        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            handleFiles(e.dataTransfer.files);
        }
    });
});

async function fetchFiles() {
    try {
        const tbody = document.getElementById('fileListBody');
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 2rem;">Loading files...</td></tr>';

        const response = await fetch('/api/files?path=' + encodeURIComponent(currentPath));
        if (!response.ok) throw new Error('Failed to fetch files');
        const files = await response.json();
        currentFiles = files;
        updateSortIndicators();
        filterAndRenderFiles();
        updateBreadcrumb();
    } catch (error) {
        console.error('Error fetching files:', error);
        document.getElementById('emptyState').style.display = 'block';
        document.getElementById('emptyState').innerHTML = '<p>Error loading files.</p>';
    }
}

async function fetchSystemInfo() {
    try {
        const response = await fetch('/api/system');
        if (!response.ok) throw new Error('Failed to fetch system info');
        const info = await response.json();

        const systemInfoElement = document.getElementById('systemInfo');
        systemInfoElement.innerHTML = `
            <span>📱 ${info.model}</span>
            <span>•</span>
            <span>Android ${info.osVersion} (API ${info.apiLevel})</span>
            <span>•</span>
            <span>🛡️ Proxy: ${window.location.hostname}:${info.proxyPort || 8081}</span>
        `;
    } catch (error) {
        console.error('Error fetching system info:', error);
        document.getElementById('systemInfo').textContent = 'Web File Server';
    }
}

let viewMode = 'list';

function toggleViewMode() {
    viewMode = viewMode === 'list' ? 'grid' : 'list';
    const btn = document.getElementById('viewToggleBtn');
    if (btn) btn.textContent = viewMode === 'list' ? '▦ Grid' : '☰ List';

    const tableEl = document.querySelector('.file-list');
    const gridEl = document.getElementById('fileGridContainer');

    if (viewMode === 'grid') {
        if (tableEl) tableEl.style.display = 'none';
        if (gridEl) gridEl.style.display = 'grid';
    } else {
        if (tableEl) tableEl.style.display = 'table';
        if (gridEl) gridEl.style.display = 'none';
    }
    filterAndRenderFiles();
}

function renderFiles(files) {
    const tbody = document.getElementById('fileListBody');
    const gridContainer = document.getElementById('fileGridContainer');
    const emptyState = document.getElementById('emptyState');

    if (tbody) tbody.innerHTML = '';
    if (gridContainer) gridContainer.innerHTML = '';

    // Populate Image Playlist for Slideshow
    imagePlaylist = files
        .filter(f => !f.isDirectory && f.name.match(/\.(jpg|jpeg|png|gif|webp|svg|bmp|ico)$/i))
        .map(f => ({
            name: f.name,
            url: `/api/download/${encodeURIComponent(f.name)}?path=${encodeURIComponent(currentPath)}&_t=${f.lastModified}`
        }));

    if (files.length === 0) {
        if (emptyState) emptyState.style.display = 'block';
        return;
    }

    if (emptyState) emptyState.style.display = 'none';

    files.forEach(file => {
        const fileUrl = `/api/download/${encodeURIComponent(file.name)}?path=${encodeURIComponent(currentPath)}&_t=${file.lastModified}`;
        const isImage = file.name.match(/\.(jpg|jpeg|png|gif|webp|svg|bmp|ico)$/i);
        const isMedia = isImage || file.name.match(/\.(mp4|webm|mkv|mov|avi|mp3|wav|ogg|m4a|flac|aac|opus|pdf|txt|json|md|js|py|html|css|kt|java)$/i);
        const escapedName = escapeJsArg(file.name);

        let iconEmoji = '📄';
        if (file.isDirectory) {
            iconEmoji = '📁';
        } else if (isImage) {
            iconEmoji = '🖼️';
        } else if (file.name.match(/\.(mp4|mov|avi|webm|mkv)$/i)) {
            iconEmoji = '🎬';
        } else if (file.name.match(/\.(mp3|wav|ogg|m4a|flac|aac|opus)$/i)) {
            iconEmoji = '🎵';
        } else if (file.name.match(/\.(pdf)$/i)) {
            iconEmoji = '📕';
        } else if (file.name.match(/\.(json|js|py|html|css|kt|java|cpp|c|h|sh|xml|yml|yaml)$/i)) {
            iconEmoji = '💻';
        }

        const iconHtml = isImage 
            ? `<img src="${fileUrl}" class="file-thumbnail" alt="${escapeHtml(file.name)}" loading="lazy" />`
            : iconEmoji;

        if (tbody) {
            const tr = document.createElement('tr');
            const checkboxHtml = `<td style="text-align: center;" onclick="event.stopPropagation();">
                <input type="checkbox" class="custom-checkbox item-checkbox" data-name="${escapedName}" data-is-dir="${file.isDirectory}" onclick="updateBatchToolbar()" />
            </td>`;

            if (file.isDirectory) {
                tr.innerHTML = `
                    ${checkboxHtml}
                    <td>
                        <div class="file-name clickable" onclick="navigateTo('${escapedName}')" style="cursor: pointer; color: var(--primary);">
                            <span class="file-icon">${iconHtml}</span>
                            <span>${escapeHtml(file.name)}</span>
                        </div>
                    </td>
                    <td>--</td>
                    <td>${formatDate(file.lastModified)}</td>
                    <td class="action-links">
                        <button class="btn-action move-btn" onclick="openMoveModal('${escapedName}')">📦 Move</button>
                        <button class="btn-action delete-btn" onclick="deleteItem('${escapedName}')">🗑️ Delete</button>
                    </td>
                `;
            } else {
                tr.innerHTML = `
                    ${checkboxHtml}
                    <td>
                        <div class="file-name ${isMedia ? 'clickable' : ''}" ${isMedia ? `onclick="openPreview('${escapedName}', '${fileUrl}')"` : ''} style="${isMedia ? 'cursor: pointer;' : ''}">
                            <span class="file-icon">${iconHtml}</span>
                            <span>${escapeHtml(file.name)}</span>
                        </div>
                    </td>
                    <td>${formatBytes(file.size)}</td>
                    <td>${formatDate(file.lastModified)}</td>
                    <td class="action-links">
                        ${isMedia ? `<button class="btn-action view-btn" onclick="openPreview('${escapedName}', '${fileUrl}')">👁️ View</button>` : `<a href="${fileUrl}" target="_blank" class="btn-action view-btn">👁️ View</a>`}
                        <button class="btn-action copy-btn" onclick="copyLink('${fileUrl}', '${escapedName}')" title="Copy Link">🔗 Link</button>
                        <button class="btn-action move-btn" onclick="openMoveModal('${escapedName}')">📦 Move</button>
                        <a href="${fileUrl}" class="download-btn" download>⬇ Download</a>
                        <button class="btn-action delete-btn" onclick="deleteItem('${escapedName}')">🗑️ Delete</button>
                    </td>
                `;
            }
            tbody.appendChild(tr);
        }

        if (gridContainer) {
            const card = document.createElement('div');
            card.className = 'grid-card';

            const previewContent = isImage 
                ? `<img src="${fileUrl}" alt="${escapeHtml(file.name)}" loading="lazy" />`
                : iconEmoji;

            const gridCheckbox = `<input type="checkbox" class="custom-checkbox item-checkbox grid-checkbox" data-name="${escapedName}" data-is-dir="${file.isDirectory}" onclick="event.stopPropagation(); updateBatchToolbar();" />`;

            if (file.isDirectory) {
                card.innerHTML = `
                    ${gridCheckbox}
                    <div class="grid-preview" onclick="navigateTo('${escapedName}')" style="cursor: pointer;">
                        ${previewContent}
                    </div>
                    <div class="grid-name" title="${escapeHtml(file.name)}">${escapeHtml(file.name)}</div>
                    <div class="grid-meta">${formatDate(file.lastModified)}</div>
                    <div class="grid-actions">
                        <button class="btn-action move-btn" onclick="openMoveModal('${escapedName}')" title="Move">📦</button>
                        <button class="btn-action delete-btn" onclick="deleteItem('${escapedName}')" title="Delete">🗑️</button>
                    </div>
                `;
            } else {
                card.innerHTML = `
                    ${gridCheckbox}
                    <div class="grid-preview ${isMedia ? 'clickable' : ''}" ${isMedia ? `onclick="openPreview('${escapedName}', '${fileUrl}')"` : ''} style="${isMedia ? 'cursor: pointer;' : ''}">
                        ${previewContent}
                    </div>
                    <div class="grid-name" title="${escapeHtml(file.name)}">${escapeHtml(file.name)}</div>
                    <div class="grid-meta">${formatBytes(file.size)}</div>
                    <div class="grid-actions">
                        ${isMedia ? `<button class="btn-action view-btn" onclick="openPreview('${escapedName}', '${fileUrl}')" title="View">👁️</button>` : `<a href="${fileUrl}" target="_blank" class="btn-action view-btn" title="View">👁️</a>`}
                        <button class="btn-action copy-btn" onclick="copyLink('${fileUrl}', '${escapedName}')" title="Copy Link">🔗</button>
                        <button class="btn-action move-btn" onclick="openMoveModal('${escapedName}')" title="Move">📦</button>
                        <a href="${fileUrl}" class="download-btn" download title="Download">⬇</a>
                        <button class="btn-action delete-btn" onclick="deleteItem('${escapedName}')" title="Delete">🗑️</button>
                    </div>
                `;
            }
            gridContainer.appendChild(card);
        }
    });

    updateBatchToolbar();
}

async function handleFileUpload(event) {
    handleFiles(event.target.files);
    event.target.value = '';
}

async function handleFiles(files) {
    if (!files || files.length === 0) return;

    const progressContainer = document.getElementById('uploadProgressContainer');
    const progressBar = document.getElementById('uploadProgressBar');
    const uploadFilename = document.getElementById('uploadFilename');

    progressContainer.style.display = 'block';
    let uploadedCount = 0;

    for (let i = 0; i < files.length; i++) {
        const file = files[i];
        uploadFilename.textContent = file.name;
        progressBar.style.width = '0%';

        const formData = new FormData();
        formData.append('file', file);

        try {
            await new Promise((resolve, reject) => {
                const xhr = new XMLHttpRequest();
                xhr.open('POST', '/api/upload?path=' + encodeURIComponent(currentPath), true);

                xhr.upload.onprogress = (e) => {
                    if (e.lengthComputable) {
                        const percentComplete = (e.loaded / e.total) * 100;
                        progressBar.style.width = percentComplete + '%';
                    }
                };

                xhr.onload = () => {
                    if (xhr.status === 200) {
                        uploadedCount++;
                        resolve();
                    } else {
                        reject(new Error(`Server returned ${xhr.status}`));
                    }
                };

                xhr.onerror = () => reject(new Error('Network error during upload'));

                xhr.send(formData);
            });
        } catch (error) {
            console.error('Error uploading file:', error);
            showToast('Failed to upload ' + file.name + ': ' + error.message, 'error');
        }
    }

    progressContainer.style.display = 'none';
    if (uploadedCount > 0) {
        showToast(`Successfully uploaded ${uploadedCount} file(s)`, 'success');
    }
    fetchFiles();
}

function formatBytes(bytes, decimals = 2) {
    if (!+bytes) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}

function navigateTo(folderName) {
    if (currentPath === '') {
        currentPath = folderName;
    } else {
        currentPath = currentPath + '/' + folderName;
    }
    fetchFiles();
}

function navigateUp() {
    if (currentPath === '') return;
    const segments = currentPath.split('/');
    segments.pop();
    currentPath = segments.join('/');
    fetchFiles();
}

function updateBreadcrumb() {
    const breadcrumb = document.getElementById('breadcrumb');
    if (!breadcrumb) return;

    breadcrumb.innerHTML = '';

    const homeLink = document.createElement('span');
    homeLink.style.cssText = 'font-size: 1.2rem; cursor:pointer;';
    homeLink.textContent = '🏠 Home';
    homeLink.addEventListener('click', () => { currentPath = ''; fetchFiles(); });
    breadcrumb.appendChild(homeLink);

    if (currentPath !== '') {
        const parts = currentPath.split('/');
        let accumulatedPath = '';

        parts.forEach((part) => {
            if (part === '') return;
            accumulatedPath += (accumulatedPath === '' ? part : '/' + part);

            const separator = document.createElement('span');
            separator.style.color = 'var(--text-muted)';
            separator.textContent = ' / ';
            breadcrumb.appendChild(separator);

            const link = document.createElement('span');
            link.style.cursor = 'pointer';
            link.textContent = part;
            const pathValue = accumulatedPath;
            link.addEventListener('click', () => { currentPath = pathValue; fetchFiles(); });
            breadcrumb.appendChild(link);
        });
    }
}
