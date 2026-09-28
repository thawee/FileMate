let currentPath = '';
let currentFiles = [];
let sortCol = 'name';
let sortDesc = false;
let searchQuery = '';

function getFileTypeSvg(file, isGrid) {
    const size = isGrid ? 52 : 20;
    const isDir = file.isDirectory;
    const name = (file.name || '').toLowerCase();
    
    if (isDir) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="#f59e0b" stroke="#d97706" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>`;
    }
    if (name.match(/\.(mp4|mov|avi|webm|mkv)$/i)) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#8b5cf6" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"></rect><line x1="7" y1="2" x2="7" y2="22"></line><line x1="17" y1="2" x2="17" y2="22"></line><line x1="2" y1="12" x2="22" y2="12"></line><line x1="2" y1="7" x2="7" y2="7"></line><line x1="2" y1="17" x2="7" y2="17"></line><line x1="17" y1="17" x2="22" y2="17"></line><line x1="17" y1="7" x2="22" y2="7"></line></svg>`;
    }
    if (name.match(/\.(mp3|wav|ogg|m4a|flac|aac|opus)$/i)) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#d946ef" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>`;
    }
    if (name.match(/\.pdf$/i)) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>`;
    }
    if (name.match(/\.(zip|rar|7z|tar|gz|bz2|xz)$/i)) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#f97316" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="21 8 21 21 3 21 3 8"></polyline><rect x="1" y="3" width="22" height="5"></rect><line x1="10" y1="12" x2="14" y2="12"></line></svg>`;
    }
    if (name.match(/\.(json|js|ts|py|html|css|kt|java|cpp|c|h|sh|xml|yml|yaml|sql|php|rb|go|rs)$/i)) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#14b8a6" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></svg>`;
    }
    if (name.match(/\.(apk|aab)$/i)) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#22c55e" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="11" width="14" height="10" rx="2"></rect><circle cx="9" cy="7" r="1"></circle><circle cx="15" cy="7" r="1"></circle><line x1="12" y1="3" x2="12" y2="5"></line></svg>`;
    }
    if (name.match(/\.(doc|docx|txt|rtf|odt|csv|log|md)$/i)) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>`;
    }
    return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>`;
}


function showToast(message, type = 'info', duration = 3500) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'status');

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

// ── Focus trap for accessibility ──────────────────────────────────────────────
let _focusTrapModal = null;
let _focusTrapPrev = null;
const FOCUSABLE_SELECTORS = 'a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])';

function trapFocus(modalEl) {
    if (!modalEl) return;
    _focusTrapModal = modalEl;
    _focusTrapPrev = document.activeElement;
    const focusable = Array.from(modalEl.querySelectorAll(FOCUSABLE_SELECTORS));
    if (focusable.length > 0) focusable[0].focus();

    modalEl._trapKeydown = (e) => {
        if (e.key !== 'Tab') return;
        const elems = Array.from(modalEl.querySelectorAll(FOCUSABLE_SELECTORS));
        if (elems.length === 0) { e.preventDefault(); return; }
        const first = elems[0], last = elems[elems.length - 1];
        if (e.shiftKey) {
            if (document.activeElement === first) { e.preventDefault(); last.focus(); }
        } else {
            if (document.activeElement === last) { e.preventDefault(); first.focus(); }
        }
    };
    modalEl.addEventListener('keydown', modalEl._trapKeydown);
}

function releaseFocus() {
    if (_focusTrapModal && _focusTrapModal._trapKeydown) {
        _focusTrapModal.removeEventListener('keydown', _focusTrapModal._trapKeydown);
        _focusTrapModal._trapKeydown = null;
    }
    if (_focusTrapPrev && typeof _focusTrapPrev.focus === 'function') _focusTrapPrev.focus();
    _focusTrapModal = null;
    _focusTrapPrev = null;
}
// ─────────────────────────────────────────────────────────────────────────────

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

function handleSortSelect(val) {
    if (!val) return;
    const [col, dir] = val.split('-');
    sortCol = col;
    sortDesc = (dir === 'desc');
    updateSortIndicators();
    filterAndRenderFiles();
}

function updateSortIndicators() {
    const getIndicator = (col) => sortCol === col ? (sortDesc ? ' ▼' : ' ▲') : '';
    const thName = document.getElementById('thName');
    const thSize = document.getElementById('thSize');
    const thTime = document.getElementById('thTime');
    if (thName) thName.textContent = 'Name' + getIndicator('name');
    if (thSize) thSize.textContent = 'Size' + getIndicator('size');
    if (thTime) thTime.textContent = 'Modified' + getIndicator('time');

    // Keep aria-sort in sync for screen readers
    const ariaSort = (col) => sortCol === col ? (sortDesc ? 'descending' : 'ascending') : 'none';
    if (thName) thName.setAttribute('aria-sort', ariaSort('name'));
    if (thSize) thSize.setAttribute('aria-sort', ariaSort('size'));
    if (thTime) thTime.setAttribute('aria-sort', ariaSort('time'));

    const selector = document.getElementById('sortSelector');
    if (selector) {
        selector.value = `${sortCol}-${sortDesc ? 'desc' : 'asc'}`;
    }
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

let currentZoom = 1;
let panX = 0;
let panY = 0;
let isPanning = false;
let startPanX = 0;
let startPanY = 0;
const MIN_ZOOM = 0.5;
const MAX_ZOOM = 5.0;
const ZOOM_STEP = 0.25;

function applyZoom(imgElement, animate = true) {
    const img = imgElement || document.querySelector('.modal-body img.slideshow-image');
    const badge = document.getElementById('zoomLevelBadge');
    if (badge) {
        badge.textContent = `${Math.round(currentZoom * 100)}%`;
    }

    if (!img) return;

    if (animate) {
        img.classList.remove('panning');
    } else {
        img.classList.add('panning');
    }

    if (currentZoom > 1) {
        img.classList.add('zoomed');
    } else {
        img.classList.remove('zoomed');
        img.classList.remove('grabbing');
    }

    img.style.transform = `translate(${panX}px, ${panY}px) scale(${currentZoom})`;
}

function zoomIn() {
    currentZoom = Math.min(Math.round((currentZoom + ZOOM_STEP) * 100) / 100, MAX_ZOOM);
    applyZoom();
}

function zoomOut() {
    currentZoom = Math.max(Math.round((currentZoom - ZOOM_STEP) * 100) / 100, MIN_ZOOM);
    if (currentZoom <= 1) {
        panX = 0;
        panY = 0;
    }
    applyZoom();
}

function resetZoom() {
    currentZoom = 1;
    panX = 0;
    panY = 0;
    applyZoom();
}

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
    const zoomControls = document.getElementById('zoomControls');
    const zoomDivider = document.getElementById('zoomDivider');

    title.textContent = fileName;
    newTabLink.href = fileUrl;
    body.innerHTML = '';
    resetZoom();

    const ext = fileName.split('.').pop().toLowerCase();
    const isImage = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].includes(ext);
    const isVideo = ['mp4', 'webm', 'mkv', 'mov', 'avi'].includes(ext);
    const isAudio = ['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac', 'opus'].includes(ext);
    const isTextCode = ['txt', 'json', 'md', 'js', 'html', 'css', 'py', 'java', 'kt', 'sh', 'xml', 'yml', 'yaml', 'c', 'cpp', 'h', 'csv', 'tsv', 'log', 'properties', 'gradle', 'kts', 'rs', 'go', 'ts'].includes(ext);

    if (isImage) {
        body.classList.add('image-mode');
        if (zoomControls) zoomControls.style.display = 'inline-flex';
        if (zoomDivider) zoomDivider.style.display = 'block';
        
        if (imagePlaylist.length > 0) {
            const idx = imagePlaylist.findIndex(item => item.name === fileName);
            currentPlaylistIndex = idx !== -1 ? idx : 0;
            if (controls) controls.style.display = 'inline-flex';
            if (counter) {
                counter.style.display = 'inline-block';
                counter.textContent = `${currentPlaylistIndex + 1} / ${imagePlaylist.length}`;
            }
            
            // Preload next image
            if (currentPlaylistIndex < imagePlaylist.length - 1) {
                const preloadImg = new Image();
                preloadImg.src = imagePlaylist[currentPlaylistIndex + 1].url;
            }
        } else {
            if (controls) controls.style.display = 'none';
            if (counter) counter.style.display = 'none';
        }

        // Blurred Background
        const bgImg = document.createElement('img');
        bgImg.src = fileUrl;
        bgImg.className = 'slideshow-bg-blur';
        
        const img = document.createElement('img');
        img.src = fileUrl;
        img.alt = fileName;
        img.className = 'slideshow-image';
        
        // Double-click to toggle zoom (1x <-> 2.5x)
        img.addEventListener('dblclick', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (currentZoom > 1) {
                resetZoom();
            } else {
                currentZoom = 2.5;
                panX = 0;
                panY = 0;
                applyZoom(img);
            }
        });

        // Mouse Drag to Pan when Zoomed
        img.addEventListener('mousedown', (e) => {
            if (currentZoom <= 1 || e.button !== 0) return;
            isPanning = true;
            startPanX = e.clientX - panX;
            startPanY = e.clientY - panY;
            img.classList.add('grabbing');
            e.preventDefault();
        });

        // Touch gestures (Pinch to Zoom & Drag to Pan)
        let touchStartDist = 0;
        let initialZoom = 1;
        img.addEventListener('touchstart', (e) => {
            if (e.touches.length === 1 && currentZoom > 1) {
                isPanning = true;
                startPanX = e.touches[0].clientX - panX;
                startPanY = e.touches[0].clientY - panY;
            } else if (e.touches.length === 2) {
                isPanning = false;
                touchStartDist = Math.hypot(
                    e.touches[0].clientX - e.touches[1].clientX,
                    e.touches[0].clientY - e.touches[1].clientY
                );
                initialZoom = currentZoom;
            }
        }, { passive: true });

        img.addEventListener('touchmove', (e) => {
            if (isPanning && e.touches.length === 1) {
                panX = e.touches[0].clientX - startPanX;
                panY = e.touches[0].clientY - startPanY;
                applyZoom(img, false);
            } else if (e.touches.length === 2 && touchStartDist > 0) {
                const dist = Math.hypot(
                    e.touches[0].clientX - e.touches[1].clientX,
                    e.touches[0].clientY - e.touches[1].clientY
                );
                const scaleFactor = dist / touchStartDist;
                currentZoom = Math.min(Math.max(Math.round(initialZoom * scaleFactor * 100) / 100, MIN_ZOOM), MAX_ZOOM);
                applyZoom(img, false);
            }
        }, { passive: true });

        img.addEventListener('touchend', () => {
            isPanning = false;
            touchStartDist = 0;
            if (currentZoom <= 1) {
                panX = 0;
                panY = 0;
            }
            applyZoom(img, true);
        });

        // Trigger fade in animation
        setTimeout(() => img.classList.add('active'), 10);
        
        body.appendChild(bgImg);
        body.appendChild(img);

        // EXIF logic
        const exifOverlay = document.getElementById('exifOverlay');
        const exifLoc = document.getElementById('exifLocation').querySelector('.exif-text');
        const exifDate = document.getElementById('exifDate').querySelector('.exif-text');
        exifLoc.textContent = '...';
        exifDate.textContent = '...';
        
        // Fetch EXIF data
        fetch('/api/exif?path=' + encodeURIComponent(currentPath === '/' ? '/' + fileName : currentPath + '/' + fileName))
            .then(res => res.json())
            .then(data => {
                if (data.status === 'ok' && (data.datetime || data.location)) {
                    let hasData = false;
                    if (data.location) {
                        exifLoc.textContent = data.location;
                        document.getElementById('exifLocation').style.display = 'flex';
                        hasData = true;
                    } else {
                        document.getElementById('exifLocation').style.display = 'none';
                    }
                    if (data.datetime) {
                        exifDate.textContent = data.datetime;
                        document.getElementById('exifDate').style.display = 'flex';
                        hasData = true;
                    } else {
                        document.getElementById('exifDate').style.display = 'none';
                    }
                    if (hasData) {
                        exifOverlay.style.display = 'flex';
                        exifOverlay.style.opacity = '1';
                    }
                } else {
                    exifOverlay.style.display = 'none';
                }
            })
            .catch(() => {
                exifOverlay.style.display = 'none';
            });

        // Floating Overlay Navigation Arrows for Images
        if (imagePlaylist.length > 1) {
            const prevArrow = document.createElement('div');
            prevArrow.className = 'preview-nav-arrow prev premium-nav-arrow';
            prevArrow.innerHTML = '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>';
            prevArrow.title = 'Previous Image (←)';
            prevArrow.onclick = (e) => { e.stopPropagation(); prevImage(); };

            const nextArrow = document.createElement('div');
            nextArrow.className = 'preview-nav-arrow next premium-nav-arrow';
            nextArrow.innerHTML = '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>';
            nextArrow.title = 'Next Image (→)';
            nextArrow.onclick = (e) => { e.stopPropagation(); nextImage(); };

            body.appendChild(prevArrow);
            body.appendChild(nextArrow);
        }
    } else {
        body.classList.remove('image-mode');
        if (zoomControls) zoomControls.style.display = 'none';
        if (zoomDivider) zoomDivider.style.display = 'none';
        const exifOverlay = document.getElementById('exifOverlay');
        if (exifOverlay) exifOverlay.style.display = 'none';

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

                if (ext === 'md' && typeof marked !== 'undefined') {
                    const container = document.createElement('div');
                    container.className = 'markdown-preview';
                    container.style.padding = '2rem';
                    container.style.color = 'var(--text-color)';
                    container.style.lineHeight = '1.6';
                    container.style.maxWidth = '800px';
                    container.style.margin = '0 auto';
                    container.innerHTML = marked.parse(text);
                    body.innerHTML = '';
                    body.appendChild(container);
                } else {
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
                }
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
    trapFocus(modal);
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

function getSlideshowSpeed() {
    const selector = document.getElementById('slideshowSpeed');
    return selector ? parseInt(selector.value) : 5000;
}

function updateSlideshowSpeed() {
    if (isSlideshowPlaying) {
        stopSlideshow();
        toggleSlideshowPlay();
    }
}

function toggleSlideshowPlay() {
    const playBtn = document.getElementById('slideshowPlayBtn');
    if (isSlideshowPlaying) {
        stopSlideshow();
    } else {
        isSlideshowPlaying = true;
        document.body.classList.add('slideshow-playing');
        if (playBtn) {
            playBtn.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="4" height="16" x="6" y="4"/><rect width="4" height="16" x="14" y="4"/></svg>';
            playBtn.title = 'Pause (Space)';
        }
        slideshowTimer = setInterval(() => {
            nextImage();
        }, getSlideshowSpeed());
    }
}

function stopSlideshow() {
    if (slideshowTimer) {
        clearInterval(slideshowTimer);
        slideshowTimer = null;
    }
    isSlideshowPlaying = false;
    document.body.classList.remove('slideshow-playing');
    const playBtn = document.getElementById('slideshowPlayBtn');
    if (playBtn) {
        playBtn.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="5 3 19 12 5 21 5 3"/></svg>';
        playBtn.title = 'Auto Play (Space)';
    }
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

function closeModalAnimated(modal, onClosed) {
    if (!modal || modal.style.display === 'none') return;
    modal.classList.add('modal-closing');
    setTimeout(() => {
        modal.style.display = 'none';
        modal.classList.remove('modal-closing');
        if (onClosed) onClosed();
    }, 180);
}

function closePreview() {
    stopSlideshow();
    releaseFocus();
    const modal = document.getElementById('previewModal');
    const body = document.getElementById('previewBody');
    if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
    }
    const exifOverlay = document.getElementById('exifOverlay');
    if (exifOverlay) exifOverlay.style.display = 'none';

    closeModalAnimated(modal, () => {
        if (body) {
            body.innerHTML = '';
            body.classList.remove('image-mode');
        }
    });
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
    } else if (e.key === '+' || e.key === '=') {
        e.preventDefault();
        zoomIn();
    } else if (e.key === '-' || e.key === '_') {
        e.preventDefault();
        zoomOut();
    } else if (e.key === '0') {
        e.preventDefault();
        resetZoom();
    }
});

// Global Panning Listeners
window.addEventListener('mousemove', (e) => {
    if (!isPanning) return;
    panX = e.clientX - startPanX;
    panY = e.clientY - startPanY;
    applyZoom(null, false);
});

window.addEventListener('mouseup', () => {
    if (isPanning) {
        isPanning = false;
        const img = document.querySelector('.modal-body img.slideshow-image');
        if (img) img.classList.remove('grabbing');
        applyZoom(null, true);
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

function nameParams(names) {
    return names.map(name => `name=${encodeURIComponent(name)}`).join('&');
}

let movingFileNames = [];
let movingFromPath = '';
let moveTargetPath = '';

async function openMoveModal(fileName) {
    movingFileNames = [fileName];
    movingFromPath = currentPath;
    moveTargetPath = '';

    document.getElementById('moveFileName').textContent = fileName;
    document.getElementById('moveModal').style.display = 'flex';

    await loadMoveFolders();
}

function closeMoveModal() {
    const modal = document.getElementById('moveModal');
    closeModalAnimated(modal, () => {
        movingFileNames = [];
    });
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
    if (movingFileNames.length === 0) return;

    try {
        const response = await fetch(`/api/move?${nameParams(movingFileNames)}&fromPath=${encodeURIComponent(movingFromPath)}&targetPath=${encodeURIComponent(moveTargetPath)}`, {
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

let renamingTargetName = '';

function openRenameModal(fileName) {
    renamingTargetName = fileName;
    const input = document.getElementById('renameInput');
    if (input) input.value = fileName;
    const modal = document.getElementById('renameModal');
    if (modal) modal.style.display = 'flex';
    setTimeout(() => {
        if (input) {
            input.focus();
            const dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0) input.setSelectionRange(0, dotIdx);
            else input.select();
        }
    }, 50);
}

function closeRenameModal() {
    const modal = document.getElementById('renameModal');
    closeModalAnimated(modal, () => {
        renamingTargetName = '';
    });
}

async function confirmRename() {
    const input = document.getElementById('renameInput');
    const newName = input ? input.value.trim() : '';
    if (!newName || newName === renamingTargetName) {
        closeRenameModal();
        return;
    }
    try {
        const resp = await fetch(`/api/rename?path=${encodeURIComponent(currentPath)}&oldName=${encodeURIComponent(renamingTargetName)}&newName=${encodeURIComponent(newName)}`);
        if (!resp.ok) {
            const err = await resp.text();
            throw new Error(err || 'Failed to rename');
        }
        showToast(`Renamed to "${newName}"`, 'success');
        closeRenameModal();
        fetchFiles();
    } catch (e) {
        showToast('Error renaming: ' + e.message, 'error');
    }
}

async function unzipFile(fileName) {
    try {
        showToast(`Extracting ${fileName}...`, 'info');
        const resp = await fetch(`/api/unzip?path=${encodeURIComponent(currentPath)}&name=${encodeURIComponent(fileName)}`, { method: 'POST' });
        if (!resp.ok) {
            const error = await resp.json();
            throw new Error(error.error || 'Failed to unzip');
        }
        const res = await resp.json();
        showToast(res.errors ? `Extracted ${res.unzipped} archive(s); ${res.errors} failed` : `Extracted ${fileName} successfully`, res.errors ? 'error' : 'success');
        fetchFiles();
    } catch (e) {
        showToast('Error extracting archive: ' + e.message, 'error');
    }
}

let lastCheckedIndex = -1;

function handleCheckboxClick(event, checkbox) {
    const isGrid = checkbox.classList.contains('grid-checkbox');
    const checkboxes = Array.from(document.querySelectorAll(isGrid ? '.grid-checkbox' : '.item-checkbox:not(.grid-checkbox)'));
    const currentIndex = parseInt(checkbox.dataset.index, 10);
    
    if (event.shiftKey && lastCheckedIndex !== -1 && lastCheckedIndex !== currentIndex) {
        const start = Math.min(lastCheckedIndex, currentIndex);
        const end = Math.max(lastCheckedIndex, currentIndex);
        const targetState = checkbox.checked;
        
        checkboxes.forEach(cb => {
            const idx = parseInt(cb.dataset.index, 10);
            if (idx >= start && idx <= end) {
                cb.checked = targetState;
                const otherSelector = isGrid ? `.item-checkbox:not(.grid-checkbox)[data-name="${cb.dataset.name}"]` : `.grid-checkbox[data-name="${cb.dataset.name}"]`;
                const otherCb = document.querySelector(otherSelector);
                if (otherCb) otherCb.checked = targetState;
            }
        });
    } else {
        const otherSelector = isGrid ? `.item-checkbox:not(.grid-checkbox)[data-name="${checkbox.dataset.name}"]` : `.grid-checkbox[data-name="${checkbox.dataset.name}"]`;
        const otherCb = document.querySelector(otherSelector);
        if (otherCb) otherCb.checked = checkbox.checked;
    }
    
    lastCheckedIndex = currentIndex;
    updateBatchToolbar();
}

function showContextMenu(e, file, isProtected, isMedia, isZip, fileUrl, escapedName) {
    const menu = document.getElementById('contextMenu');
    if (!menu) return;
    
    let itemsHtml = '';
    if (file.isDirectory) {
        itemsHtml += `<button onclick="hideContextMenu(); navigateTo('${escapedName}')"><span>📁</span> Open Folder</button>`;
        itemsHtml += `<button onclick="hideContextMenu(); showQr('${escapedName}', true)"><span>📱</span> Share QR</button>`;
        if (!isProtected) {
            itemsHtml += `<a href="/api/download-zip?path=${encodeURIComponent(currentPath)}&name=${encodeURIComponent(file.name)}" download style="text-decoration:none;"><button onclick="hideContextMenu()"><span>⬇</span> Download ZIP</button></a>`;
            itemsHtml += `<button onclick="hideContextMenu(); openRenameModal('${escapedName}')"><span>✏️</span> Rename</button>`;
            itemsHtml += `<button onclick="hideContextMenu(); openMoveModal('${escapedName}')"><span>📦</span> Move To...</button>`;
            itemsHtml += `<button class="menu-delete" onclick="hideContextMenu(); deleteItem('${escapedName}')"><span>🗑️</span> Delete</button>`;
        }
    } else {
        if (isMedia) {
            itemsHtml += `<button onclick="hideContextMenu(); openPreview('${escapedName}', '${fileUrl}')"><span>👁️</span> Preview</button>`;
        } else {
            itemsHtml += `<button onclick="hideContextMenu(); window.open('${fileUrl}', '_blank')"><span>👁️</span> View</button>`;
        }
        itemsHtml += `<button onclick="hideContextMenu(); copyLink('${fileUrl}', '${escapedName}')"><span>🔗</span> Copy Link</button>`;
        itemsHtml += `<button onclick="hideContextMenu(); showQr('${escapedName}', false)"><span>📱</span> Share QR</button>`;
        itemsHtml += `<a href="${fileUrl}" download style="text-decoration:none;"><button onclick="hideContextMenu()"><span>⬇</span> Download</button></a>`;
        if (isZip) {
            itemsHtml += `<button onclick="hideContextMenu(); unzipFile('${escapedName}')"><span>📂</span> Extract ZIP</button>`;
        }
        itemsHtml += `<button onclick="hideContextMenu(); openRenameModal('${escapedName}')"><span>✏️</span> Rename</button>`;
        itemsHtml += `<button onclick="hideContextMenu(); openMoveModal('${escapedName}')"><span>📦</span> Move To...</button>`;
        itemsHtml += `<button class="menu-delete" onclick="hideContextMenu(); deleteItem('${escapedName}')"><span>🗑️</span> Delete</button>`;
    }
    
    menu.innerHTML = itemsHtml;
    menu.style.display = 'flex';
    
    const menuWidth = 190;
    const menuHeight = menu.offsetHeight || 220;
    let posX = e.clientX;
    let posY = e.clientY;
    
    if (posX + menuWidth > window.innerWidth) posX = window.innerWidth - menuWidth - 10;
    if (posY + menuHeight > window.innerHeight) posY = window.innerHeight - menuHeight - 10;
    
    menu.style.left = `${Math.max(10, posX)}px`;
    menu.style.top = `${Math.max(10, posY)}px`;
}

function hideContextMenu() {
    const menu = document.getElementById('contextMenu');
    if (menu) menu.style.display = 'none';
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

    if (!confirm(`Are you sure you want to delete ${selected.length} selected item(s)?`)) {
        return;
    }

    try {
        const response = await fetch(`/api/delete?${nameParams(selected.map(f => f.name))}&path=${encodeURIComponent(currentPath)}`, {
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

    movingFileNames = selected.map(f => f.name);
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
    closeModalAnimated(modal);
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
            closeQrModal();
            closeRenameModal();
            hideContextMenu();
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
        closeQrModal();
        closeRenameModal();
        hideContextMenu();
        clearSelection();
    } else if (e.key === 'Delete') {
        const selected = getSelectedFiles();
        if (selected.length > 0) {
            batchDeleteSelected();
        }
    }
});

document.addEventListener('click', (e) => {
    hideContextMenu();

    const previewModal = document.getElementById('previewModal');
    if (e.target === previewModal) closePreview();

    const moveModal = document.getElementById('moveModal');
    if (e.target === moveModal) closeMoveModal();

    const mkdirModal = document.getElementById('mkdirModal');
    if (e.target === mkdirModal) closeMkdirModal();

    const renameModal = document.getElementById('renameModal');
    if (e.target === renameModal) closeRenameModal();

    if (e.target.closest('.card-menu-dropdown button')) {
        document.querySelectorAll('.card-menu-dropdown.show').forEach(d => d.classList.remove('show'));
    } else if (!e.target.closest('.card-menu-wrapper')) {
        document.querySelectorAll('.card-menu-dropdown.show').forEach(d => d.classList.remove('show'));
    }
});

function toggleCardMenu(event, btn) {
    event.stopPropagation();
    const dropdown = btn.nextElementSibling;
    if (!dropdown) return;
    const isVisible = dropdown.classList.contains('show');
    document.querySelectorAll('.card-menu-dropdown.show').forEach(d => {
        if (d !== dropdown) d.classList.remove('show');
    });
    dropdown.classList.toggle('show', !isVisible);
}

document.addEventListener('DOMContentLoaded', async () => {
    // Process auto-login PIN from barcode/QR query param
    const urlParams = new URLSearchParams(window.location.search);
    const pinParam = urlParams.get('pin');
    if (pinParam) {
        try {
            const authResp = await fetch('/api/auth?pin=' + encodeURIComponent(pinParam));
            if (authResp.ok) {
                showToast('Authenticated via QR Code', 'success', 2500);
            }
            // Sanitize address bar so PIN isn't preserved in history or bookmarks
            const cleanUrl = window.location.pathname + (window.location.hash || '');
            window.history.replaceState({}, document.title, cleanUrl);
        } catch (_) {}
    }

    fetchSystemInfo();
    fetchFiles();

    const previewBody = document.getElementById('previewBody');
    if (previewBody) {
        previewBody.addEventListener('wheel', (e) => {
            if (!previewBody.classList.contains('image-mode')) return;
            e.preventDefault();
            const delta = e.deltaY < 0 ? ZOOM_STEP : -ZOOM_STEP;
            const newZoom = Math.min(Math.max(Math.round((currentZoom + delta) * 100) / 100, MIN_ZOOM), MAX_ZOOM);
            if (newZoom !== currentZoom) {
                currentZoom = newZoom;
                if (currentZoom <= 1) {
                    panX = 0;
                    panY = 0;
                }
                applyZoom();
            }
        }, { passive: false });
    }

    const fileInput = document.getElementById('fileInput');
    fileInput.addEventListener('change', handleFileUpload);

    document.getElementById('refreshBtn').addEventListener('click', fetchFiles);

    const dragOverlay = document.getElementById('dragOverlay');
    let dragCounter = 0;

    window.addEventListener('dragenter', (e) => {
        e.preventDefault();
        dragCounter++;
        const dest = document.getElementById('dragDestinationText');
        if (dest) {
            dest.textContent = `Uploading to: /${currentPath || 'Root'}`;
        }
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

let showHiddenFiles = false;

async function fetchFiles() {
    try {
        const tbody = document.getElementById('fileListBody');
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 2rem;">Loading files...</td></tr>';

        const response = await fetch(`/api/files?path=${encodeURIComponent(currentPath)}&showHidden=${showHiddenFiles}`);
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
        
        // Helper function to format uptime
        const formatUptime = (seconds) => {
            const h = Math.floor(seconds / 3600);
            const m = Math.floor((seconds % 3600) / 60);
            const s = Math.floor(seconds % 60);
            if (h > 0) return `${h}h ${m}m`;
            if (m > 0) return `${m}m ${s}s`;
            return `${s}s`;
        };

        let storageHtml = '';
        if (info.totalBytes && info.totalBytes > 0) {
            const usedBytes = info.totalBytes - info.freeBytes;
            const usedPct = Math.round((usedBytes / info.totalBytes) * 100);
            storageHtml = `<span>•</span><span>💾 ${formatBytes(info.freeBytes)} free of ${formatBytes(info.totalBytes)} (${usedPct}% used)</span>`;
        }

        systemInfoElement.innerHTML = `
            <span>📱 ${info.model} (Android ${info.osVersion})</span>
            <span>•</span>
            <span>App v${info.appVersion || '1.0'}</span>
            <span>•</span>
            <span>⏱️ Uptime: ${formatUptime(info.uptimeSeconds || 0)}</span>
            ${storageHtml}
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

    files.forEach((file, index) => {
        const fileUrl = `/api/download/${encodeURIComponent(file.name)}?path=${encodeURIComponent(currentPath)}&_t=${file.lastModified}`;
        const filePath = currentPath ? (currentPath + '/' + file.name) : file.name;
        const thumbUrl = `/api/thumbnail?path=${encodeURIComponent(filePath)}&_t=${file.lastModified}`;
        const isImage = file.name.match(/\.(jpg|jpeg|png|gif|webp|svg|bmp|ico)$/i);
        const isZip = file.name.match(/\.zip$/i);
        const isMedia = isImage || file.name.match(/\.(mp4|webm|mkv|mov|avi|mp3|wav|ogg|m4a|flac|aac|opus|pdf|txt|json|md|js|py|html|css|kt|java)$/i);
        const escapedName = escapeJsArg(file.name);

        const isProtected = file.isDirectory && currentPath === "" && new Set([
            "Android", "DCIM", "Pictures", "Movies", "Music", "Download", "Documents",
            "Alarms", "Notifications", "Ringtones", "Podcasts", "Audiobooks"
        ]).has(file.name);

        const iconHtml = isImage 
            ? `<img src="${thumbUrl}" class="file-thumbnail" alt="${escapeHtml(file.name)}" loading="lazy" onerror="this.onerror=null; this.src='${fileUrl}';" />`
            : getFileTypeSvg(file, false);

        if (tbody) {
            const tr = document.createElement('tr');
            tr.oncontextmenu = (e) => {
                e.preventDefault();
                showContextMenu(e, file, isProtected, isMedia, isZip, fileUrl, escapedName);
            };

            const checkboxHtml = isProtected ? `<td style="text-align: center;"></td>` : `<td style="text-align: center;" onclick="event.stopPropagation();">
                <input type="checkbox" class="custom-checkbox item-checkbox" data-index="${index}" data-name="${escapedName}" data-is-dir="${file.isDirectory}" onclick="handleCheckboxClick(event, this)" />
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
                        ${isProtected ? '' : `<a href="/api/download-zip?path=${encodeURIComponent(currentPath)}&name=${encodeURIComponent(file.name)}" class="download-btn" download title="Download ZIP">⬇ ZIP</a>`}
                        <div class="card-menu-wrapper">
                            <button class="btn-action card-menu-btn" onclick="toggleCardMenu(event, this)" aria-label="More options" title="More options">•••</button>
                            <div class="card-menu-dropdown">
                                <button onclick="navigateTo('${escapedName}')"><span>📁</span> Open Folder</button>
                                <button onclick="showQr('${escapedName}', true)"><span>📱</span> Share QR</button>
                                ${isProtected ? '' : `<button onclick="openRenameModal('${escapedName}')"><span>✏️</span> Rename</button>`}
                                ${isProtected ? '' : `<button onclick="openMoveModal('${escapedName}')"><span>📦</span> Move To...</button>`}
                                ${isProtected ? '' : `<button class="menu-delete" onclick="deleteItem('${escapedName}')"><span>🗑️</span> Delete</button>`}
                            </div>
                        </div>
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
                        ${isZip ? `<button class="btn-action view-btn" onclick="unzipFile('${escapedName}')" title="Extract ZIP Archive">📦 Unzip</button>` : ''}
                        <a href="${fileUrl}" class="download-btn" download title="Download file">⬇ Download</a>
                        <div class="card-menu-wrapper">
                            <button class="btn-action card-menu-btn" onclick="toggleCardMenu(event, this)" aria-label="More options" title="More options">•••</button>
                            <div class="card-menu-dropdown">
                                ${isMedia ? `<button onclick="openPreview('${escapedName}', '${fileUrl}')"><span>👁️</span> Preview</button>` : `<button onclick="window.open('${fileUrl}', '_blank')"><span>👁️</span> View</button>`}
                                <button onclick="copyLink('${fileUrl}', '${escapedName}')"><span>🔗</span> Copy Link</button>
                                <button onclick="showQr('${escapedName}', false)"><span>📱</span> Share QR</button>
                                ${isZip ? `<button onclick="unzipFile('${escapedName}')"><span>📂</span> Extract ZIP</button>` : ''}
                                <button onclick="openRenameModal('${escapedName}')"><span>✏️</span> Rename</button>
                                <button onclick="openMoveModal('${escapedName}')"><span>📦</span> Move To...</button>
                                <button class="menu-delete" onclick="deleteItem('${escapedName}')"><span>🗑️</span> Delete</button>
                            </div>
                        </div>
                    </td>
                `;
            }
            tbody.appendChild(tr);
        }

        if (gridContainer) {
            const card = document.createElement('div');
            card.className = 'grid-card';
            card.oncontextmenu = (e) => {
                e.preventDefault();
                showContextMenu(e, file, isProtected, isMedia, isZip, fileUrl, escapedName);
            };

            const previewContent = isImage 
                ? `<img src="${thumbUrl}" alt="${escapeHtml(file.name)}" loading="lazy" onerror="this.onerror=null; this.src='${fileUrl}';" />`
                : getFileTypeSvg(file, true);

            const gridCheckbox = isProtected ? '' : `<input type="checkbox" class="custom-checkbox item-checkbox grid-checkbox" data-index="${index}" data-name="${escapedName}" data-is-dir="${file.isDirectory}" onclick="event.stopPropagation(); handleCheckboxClick(event, this);" />`;

            if (file.isDirectory) {
                card.innerHTML = `
                    ${gridCheckbox}
                    <div class="grid-preview" onclick="navigateTo('${escapedName}')" style="cursor: pointer;">
                        ${previewContent}
                    </div>
                    <div class="grid-name" title="${escapeHtml(file.name)}" onclick="navigateTo('${escapedName}')" style="cursor: pointer;">${escapeHtml(file.name)}</div>
                    <div class="grid-meta">${formatDate(file.lastModified)}</div>
                    <div class="grid-actions">
                        ${isProtected ? `<button onclick="navigateTo('${escapedName}')" class="btn-action grid-main-btn" title="Open Folder"><span>📁</span> Open</button>` : `<a href="/api/download-zip?path=${encodeURIComponent(currentPath)}&name=${encodeURIComponent(file.name)}" class="download-btn grid-main-btn" download title="Download ZIP"><span>⬇</span> ZIP</a>`}
                        <div class="card-menu-wrapper">
                            <button class="btn-action card-menu-btn" onclick="toggleCardMenu(event, this)" aria-label="More options" title="More options">•••</button>
                            <div class="card-menu-dropdown">
                                <button onclick="navigateTo('${escapedName}')"><span>📁</span> Open Folder</button>
                                <button onclick="showQr('${escapedName}', true)"><span>📱</span> Share QR</button>
                                ${isProtected ? '' : `<button onclick="openRenameModal('${escapedName}')"><span>✏️</span> Rename</button>`}
                                ${isProtected ? '' : `<button onclick="openMoveModal('${escapedName}')"><span>📦</span> Move To...</button>`}
                                ${isProtected ? '' : `<button class="menu-delete" onclick="deleteItem('${escapedName}')"><span>🗑️</span> Delete</button>`}
                            </div>
                        </div>
                    </div>
                `;
            } else {
                card.innerHTML = `
                    ${gridCheckbox}
                    <div class="grid-preview ${isMedia ? 'clickable' : ''}" onclick="${isMedia ? `openPreview('${escapedName}', '${fileUrl}')` : `window.open('${fileUrl}', '_blank')`}" style="cursor: pointer;">
                        ${previewContent}
                    </div>
                    <div class="grid-name" title="${escapeHtml(file.name)}" onclick="${isMedia ? `openPreview('${escapedName}', '${fileUrl}')` : `window.open('${fileUrl}', '_blank')`}" style="cursor: pointer;">${escapeHtml(file.name)}</div>
                    <div class="grid-meta">${formatBytes(file.size)}</div>
                    <div class="grid-actions">
                        <a href="${fileUrl}" class="download-btn grid-main-btn" download title="Download file">
                            <span>⬇</span> Download
                        </a>
                        <div class="card-menu-wrapper">
                            <button class="btn-action card-menu-btn" onclick="toggleCardMenu(event, this)" aria-label="More options" title="More options">•••</button>
                            <div class="card-menu-dropdown">
                                ${isMedia ? `<button onclick="openPreview('${escapedName}', '${fileUrl}')"><span>👁️</span> Preview</button>` : `<button onclick="window.open('${fileUrl}', '_blank')"><span>👁️</span> View</button>`}
                                <button onclick="copyLink('${fileUrl}', '${escapedName}')"><span>🔗</span> Copy Link</button>
                                <button onclick="showQr('${escapedName}', false)"><span>📱</span> Share QR</button>
                                ${isZip ? `<button onclick="unzipFile('${escapedName}')"><span>📂</span> Extract ZIP</button>` : ''}
                                <button onclick="openRenameModal('${escapedName}')"><span>✏️</span> Rename</button>
                                <button onclick="openMoveModal('${escapedName}')"><span>📦</span> Move To...</button>
                                <button class="menu-delete" onclick="deleteItem('${escapedName}')"><span>🗑️</span> Delete</button>
                            </div>
                        </div>
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

function toggleTheme() {
    const isLight = document.body.classList.toggle('light-theme');
    const btn = document.getElementById('themeToggleBtn');
    if (btn) btn.textContent = isLight ? '🌙' : '☀️';
    localStorage.setItem('webfs-theme', isLight ? 'light' : 'dark');
}

document.addEventListener('DOMContentLoaded', () => {
    if (localStorage.getItem('webfs-theme') === 'light') {
        document.body.classList.add('light-theme');
        const btn = document.getElementById('themeToggleBtn');
        if (btn) btn.textContent = '🌙';
    }
});

function batchDownloadZip() {
    const selected = getSelectedFiles();
    if (selected.length === 0) {
        showToast('No items selected for download.', 'info');
        return;
    }
    const url = `/api/download-zip?path=${encodeURIComponent(currentPath)}&${nameParams(selected.map(f => f.name))}`;
    
    const a = document.createElement('a');
    a.href = url;
    a.download = 'archive.zip';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    
    clearSelection();
}

function showQr(name, isFolder) {
    const qrModal = document.getElementById('qrModal');
    const qrImage = document.getElementById('qrImage');
    const qrFileName = document.getElementById('qrFileName');
    
    qrFileName.textContent = 'Share ' + name;
    
    const qrUrl = `/api/qr?path=${encodeURIComponent(currentPath)}&name=${encodeURIComponent(name)}&isFolder=${isFolder}&_t=${new Date().getTime()}`;
    qrImage.src = qrUrl;
    
    qrModal.style.display = 'flex';
}

function closeQrModal() {
    const modal = document.getElementById('qrModal');
    closeModalAnimated(modal);
}

document.addEventListener('click', (e) => {
    const qrModal = document.getElementById('qrModal');
    if (e.target === qrModal) closeQrModal();
});
