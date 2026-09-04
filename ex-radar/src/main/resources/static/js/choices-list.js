(function () {
  // 教訓まとめ(/choices)の簡易表示/詳細表示切り替え。ページ再読み込みなしで即時切り替え、
  // URLのview パラメータをhistory.replaceStateで更新することで、戻る/進む・共有・再読み込みの
  // いずれでも同じ表示状態を再現できるようにする(4-6: サーバー側検索(category/tag/q/page)
  // との整合性を保つため、永続化はlocalStorageではなくURLパラメータを優先する)。
  var grid = document.querySelector('.lesson-card-grid');
  var toggleButtons = document.querySelectorAll('[data-lesson-view]');
  if (!grid || !toggleButtons.length) return;

  function applyView(view, updateUrl) {
    grid.setAttribute('data-view', view);
    toggleButtons.forEach(function (button) {
      var active = button.dataset.lessonView === view;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
    if (updateUrl) {
      var url = new URL(window.location.href);
      url.searchParams.set('view', view);
      window.history.replaceState(null, '', url);
      var hiddenViewField = document.querySelector('.lessons-search-form input[name="view"]');
      if (hiddenViewField) hiddenViewField.value = view;
    }
  }

  toggleButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      applyView(button.dataset.lessonView, true);
    });
  });
})();
