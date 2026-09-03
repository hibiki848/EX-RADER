(function () {
  // 共通ヘッダーのハンバーガーメニュー開閉、および現在地ナビのハイライト。
  document.querySelectorAll('[data-nav-toggle]').forEach(function (toggle) {
    var header = toggle.closest('header');
    if (!header) return;

    function setOpen(open) {
      header.setAttribute('data-nav-open', open ? 'true' : 'false');
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    toggle.addEventListener('click', function () {
      setOpen(header.getAttribute('data-nav-open') !== 'true');
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && header.getAttribute('data-nav-open') === 'true') {
        setOpen(false);
        toggle.focus();
      }
    });

    document.addEventListener('click', function (event) {
      if (header.getAttribute('data-nav-open') !== 'true') return;
      if (header.contains(event.target)) return;
      setOpen(false);
    });
  });

  // 現在のページに対応するナビリンクをハイライトする(見た目のみ、機能には影響しない)。
  var path = window.location.pathname;
  document.querySelectorAll('.site-nav-links a[href]').forEach(function (link) {
    var href = link.getAttribute('href');
    if (href === '/' ? path === '/' : path.indexOf(href) === 0) {
      link.classList.add('is-active');
    }
  });
})();
