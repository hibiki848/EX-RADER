(function () {
  // 体験談詳細ページの本文末尾付近(#read-progress-sentinel)がビューポートに入ったら、
  // ログイン中のユーザーについてのみ既読として1回だけサーバーへ記録する。
  // 開いただけ(sentinelまでスクロールしていない)では送信されない。
  var sentinel = document.getElementById('read-progress-sentinel');
  if (!sentinel || !('IntersectionObserver' in window)) return;
  var postId = sentinel.dataset.postId;
  if (!postId) return;

  var sent = false;

  function send() {
    if (sent) return;
    sent = true;
    observer.disconnect();

    var csrfToken = document.querySelector('meta[name="_csrf"]');
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    var headers = {};
    if (csrfToken && csrfHeader) headers[csrfHeader.content] = csrfToken.content;

    fetch('/experiences/' + postId + '/read', { method: 'POST', headers: headers, keepalive: true }).catch(
      function () {
        // 既読記録の失敗は閲覧体験に影響させない(サイレントに諦める)。
      }
    );
  }

  var observer = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) send();
      });
    },
    { threshold: 0.1 }
  );
  observer.observe(sentinel);
})();
