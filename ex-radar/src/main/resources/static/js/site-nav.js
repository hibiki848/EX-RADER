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

  // スマホ用画面下固定ナビのハイライト。/experiences配下は「体験談を探す」「投稿」の
  // どちらか一方だけがアクティブになるよう、各項目ごとに明示的な条件で判定する
  // (単純な前方一致だと/experiences/newで両方光ってしまうため)。
  var bottomNavMatchers = {
    home: function (p) {
      return p === '/';
    },
    experiences: function (p) {
      return (p === '/experiences' || p.indexOf('/experiences/') === 0) && p !== '/experiences/new' && !/\/edit$/.test(p);
    },
    post: function (p) {
      return p === '/experiences/new' || /\/experiences\/[^/]+\/edit$/.test(p);
    },
    notifications: function (p) {
      return p === '/mypage/notifications';
    },
    mypage: function (p) {
      return p.indexOf('/mypage') === 0 && p !== '/mypage/notifications';
    }
  };
  document.querySelectorAll('.bottom-nav a[data-bottom-nav]').forEach(function (link) {
    var matcher = bottomNavMatchers[link.dataset.bottomNav];
    if (matcher && matcher(path)) {
      link.classList.add('is-active');
    }
  });
})();
