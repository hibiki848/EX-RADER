(() => {
  const form = document.querySelector('.experience-form');
  if (!form || form.dataset.autosaveEnabled !== 'true') return;

  const DEBOUNCE_MS = 4000;
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  const statusEl = document.getElementById('autosave-status');
  const banner = document.getElementById('draft-restore-banner');
  const restoreBtn = document.getElementById('draft-restore-apply');
  const dismissBtn = document.getElementById('draft-restore-dismiss');
  const createUrl = form.dataset.autosaveCreateUrl;

  let draftId = form.dataset.postId || null;
  let updateUrl = form.dataset.autosaveUpdateUrl || null;
  let timer = null;
  let dirty = false;
  let submitting = false;
  let inFlight = false;

  function storageKey() {
    return 'exradar:draft:' + (draftId || 'new');
  }

  function snapshot() {
    const data = {};
    form.querySelectorAll('[name]').forEach((el) => {
      if (el.type === 'checkbox') {
        if (!data[el.name]) data[el.name] = [];
        if (el.checked) data[el.name].push(el.value);
      } else {
        data[el.name] = el.value;
      }
    });
    return data;
  }

  function saveLocalBackup() {
    try {
      localStorage.setItem(storageKey(), JSON.stringify({ data: snapshot(), savedAt: Date.now() }));
    } catch (e) {
      /* localStorageが使えない環境(プライベートモード等)では諦めて何もしない */
    }
  }

  function clearLocalBackup() {
    try {
      localStorage.removeItem(storageKey());
    } catch (e) {}
  }

  function setStatus(text) {
    if (statusEl) statusEl.textContent = text;
  }

  function formatTime(date) {
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    return hh + ':' + mm;
  }

  function scheduleAutosave() {
    dirty = true;
    if (timer) clearTimeout(timer);
    timer = setTimeout(runAutosave, DEBOUNCE_MS);
  }

  async function runAutosave() {
    if (submitting || inFlight) return;
    saveLocalBackup();
    inFlight = true;
    setStatus('保存中...');
    try {
      const body = new URLSearchParams(new FormData(form));
      const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
      const url = draftId ? updateUrl : createUrl;
      const res = await fetch(url, { method: 'POST', headers, body, credentials: 'same-origin' });
      if (!res.ok) throw new Error('autosave failed: ' + res.status);
      const result = await res.json();
      if (!draftId) {
        draftId = String(result.id);
        updateUrl = '/experiences/' + draftId + '/draft/autosave';
        form.dataset.postId = draftId;
      }
      clearLocalBackup();
      dirty = false;
      setStatus('保存済み ' + formatTime(new Date()));
    } catch (e) {
      setStatus('保存に失敗しました');
    } finally {
      inFlight = false;
    }
  }

  form.addEventListener('input', scheduleAutosave);
  form.addEventListener('change', scheduleAutosave);

  form.addEventListener('submit', () => {
    submitting = true;
    if (timer) clearTimeout(timer);
  });

  // 投稿(公開)ボタンが押されたら、以後このドラフトの自動保存は二度と行わない。
  // 検証エラーで同じフォームが再表示された場合も、内容はサーバー側の再描画で
  // 保持されるため、ローカルバックアップを消しても入力内容は失われない。
  document.getElementById('publish-submit')?.addEventListener('click', () => {
    clearLocalBackup();
  });

  window.addEventListener('beforeunload', (e) => {
    if (dirty && !submitting) {
      e.preventDefault();
      e.returnValue = '';
    }
  });

  function applySnapshot(data) {
    Object.keys(data).forEach((name) => {
      const value = data[name];
      if (Array.isArray(value)) {
        form.querySelectorAll('[name="' + CSS.escape(name) + '"]').forEach((el) => {
          if (el.type === 'checkbox') el.checked = value.includes(el.value);
        });
      } else {
        const el = form.querySelector('[name="' + CSS.escape(name) + '"]');
        if (el) el.value = value;
      }
    });
  }

  function checkForLocalBackup() {
    let stored;
    try {
      const raw = localStorage.getItem(storageKey());
      if (!raw) return;
      stored = JSON.parse(raw);
    } catch (e) {
      return;
    }
    if (!stored || !stored.data || !banner) return;
    banner.hidden = false;
    restoreBtn?.addEventListener('click', () => {
      applySnapshot(stored.data);
      banner.hidden = true;
    });
    dismissBtn?.addEventListener('click', () => {
      clearLocalBackup();
      banner.hidden = true;
    });
  }

  checkForLocalBackup();
})();
