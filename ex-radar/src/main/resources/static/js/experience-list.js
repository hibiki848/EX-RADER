(function () {
  var grid = document.getElementById('experience-results');
  var template = document.getElementById('experience-card-template');
  if (!grid || !template) return;

  var numberFormat = new Intl.NumberFormat('ja-JP');

  function formatDate(iso) {
    return iso ? iso.slice(0, 10).replace(/-/g, '.') : '';
  }

  /** JSONの1件分から、experience-cardフラグメントと同じ構造のカードDOMを組み立てる。
   *  追加読み込み分には「簡易表示」モーダルを持たせない(詳細ページのみ)。 */
  function buildCard(item, wisdomUnlocked) {
    var node = template.content.firstElementChild.cloneNode(true);
    node.dataset.category = item.category;
    node.querySelector('[data-slot="category"]').textContent = item.category;
    node.querySelector('[data-slot="date"]').textContent = formatDate(item.createdAt);
    node.querySelector('[data-slot="title"]').textContent = item.title;

    var readBadge = node.querySelector('[data-slot="read-badge"]');
    if (item.read) readBadge.hidden = false;
    else readBadge.remove();

    var excerptEl = node.querySelector('[data-slot="excerpt"]');
    if (item.situationBefore) excerptEl.textContent = item.situationBefore;
    else excerptEl.remove();

    node.querySelector('[data-slot="meta"]').textContent =
      item.author + 'さん ・ 満足度 ' + item.satisfaction + '/10';
    var tagsEl = node.querySelector('[data-slot="tags"]');
    (item.tags || []).forEach(function (tag) {
      var span = document.createElement('span');
      span.textContent = '#' + tag;
      tagsEl.appendChild(span);
    });

    var lessonText = node.querySelector('[data-slot="lesson-text"]');
    var lessonLocked = node.querySelector('[data-slot="lesson-locked"]');
    if (wisdomUnlocked) {
      lessonText.textContent = item.learned || item.lesson || 'この体験談にはまだ教訓が記載されていません';
      lessonLocked.remove();
    } else {
      lessonText.remove();
    }

    node.querySelector('[data-slot="detail-link"]').setAttribute('href', '/experiences/' + item.id);
    return node;
  }

  var loadMoreButton = document.getElementById('load-more-button');
  var resultsCount = document.getElementById('results-shown-count');
  var wisdomUnlocked = grid.dataset.wisdomUnlocked === 'true';
  var currentPage = Number(grid.dataset.currentPage || '0');
  var loading = false;
  var observer;

  function stopObserving() {
    if (observer) observer.disconnect();
  }

  function loadMore() {
    if (loading) return;
    loading = true;
    if (loadMoreButton) {
      loadMoreButton.disabled = true;
      loadMoreButton.textContent = '読み込み中…';
    }
    var params = new URLSearchParams(window.location.search);
    params.set('page', String(currentPage + 1));
    fetch('/experiences?' + params.toString(), { headers: { Accept: 'application/json' } })
      .then(function (response) {
        if (!response.ok) throw new Error('request failed: ' + response.status);
        return response.json();
      })
      .then(function (data) {
        currentPage = data.number;
        data.content.forEach(function (item) {
          grid.appendChild(buildCard(item, wisdomUnlocked));
        });
        if (resultsCount) {
          resultsCount.textContent =
            grid.querySelectorAll('.experience-card').length +
            ' / ' +
            numberFormat.format(data.totalElements) +
            '件を表示中';
        }
        if (data.hasNext) {
          if (loadMoreButton) {
            loadMoreButton.disabled = false;
            loadMoreButton.textContent = 'もっと見る';
          }
        } else {
          if (loadMoreButton) loadMoreButton.hidden = true;
          stopObserving();
        }
      })
      .catch(function () {
        if (loadMoreButton) {
          loadMoreButton.disabled = false;
          loadMoreButton.textContent = 'もっと見る(読み込みに失敗しました。再度お試しください)';
        }
      })
      .finally(function () {
        loading = false;
      });
  }

  if (loadMoreButton) loadMoreButton.addEventListener('click', loadMore);

  var sentinel = document.getElementById('load-more-sentinel');
  if (sentinel && 'IntersectionObserver' in window) {
    observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) loadMore();
        });
      },
      { rootMargin: '400px' }
    );
    observer.observe(sentinel);
  }

  // 表示形式(コンパクト/詳細)の切り替え。再訪時にも維持するためlocalStorageへ保存する。
  var VIEW_STORAGE_KEY = 'exradar_experience_list_view';
  var toggleButtons = document.querySelectorAll('[data-list-view]');

  function applyView(view) {
    grid.dataset.view = view;
    toggleButtons.forEach(function (button) {
      var active = button.dataset.listView === view;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
    try {
      localStorage.setItem(VIEW_STORAGE_KEY, view);
    } catch (e) {
      // localStorageが使えない環境(プライベートブラウズ等)では単に保存しないだけにする。
    }
  }

  toggleButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      applyView(button.dataset.listView);
    });
  });

  try {
    var savedView = localStorage.getItem(VIEW_STORAGE_KEY);
    if (savedView === 'compact' || savedView === 'detailed') applyView(savedView);
  } catch (e) {
    // 読み込みに失敗しても既定のcompact表示のまま続行する。
  }

  // 絞り込みパネル(<details class="search-panel-wrap">)の開閉状態をaria-expandedへ
  // 明示的に反映する(<summary>はネイティブに開閉可能・JS不要でも壊れないが、
  // 支援技術向けにopen状態を明示するため)。
  var searchPanelWrap = document.querySelector('.search-panel-wrap');
  if (searchPanelWrap) {
    var summary = searchPanelWrap.querySelector(':scope > summary');
    var syncAriaExpanded = function () {
      if (summary) summary.setAttribute('aria-expanded', searchPanelWrap.open ? 'true' : 'false');
    };
    syncAriaExpanded();
    searchPanelWrap.addEventListener('toggle', syncAriaExpanded);
  }

  // 簡易表示モーダルの開閉。イベント委譲にすることで、追加読み込みで後から
  // 挿入されたカード(モーダルは持たないため詳細ページ導線のみ)にも影響なく動作する。
  document.addEventListener('click', function (event) {
    var opener = event.target.closest('[data-summary-target]');
    if (opener) {
      var modal = document.getElementById(opener.dataset.summaryTarget);
      if (modal) {
        modal.hidden = false;
        document.body.classList.add('modal-open');
      }
      return;
    }
    if (event.target.closest('[data-close-summary]')) {
      var openModal = event.target.closest('.summary-modal');
      if (openModal) {
        openModal.hidden = true;
        document.body.classList.remove('modal-open');
      }
    }
  });
})();
