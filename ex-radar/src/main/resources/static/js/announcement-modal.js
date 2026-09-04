(function () {
  // ログイン後お知らせモーダル(fragments/announcement-modal.html)の開閉と
  // 「次回以降表示しない」の記録のみを担当する。モーダルの出し分け(誰に何を見せるか)・
  // 表示回数の記録はすべてサーバー側(NavigationAdvice/AdminAnnouncementService)で
  // 既に完了しており、このスクリプトは「表示済みとしてサーバーに届いたモーダルを
  // 開閉するUI操作」だけを扱う。
  var modal = document.getElementById('announcement-modal');
  if (!modal) return;

  var recipientId = modal.dataset.recipientId;
  var csrfToken = modal.dataset.csrfToken;
  var csrfHeader = modal.dataset.csrfHeader;
  var dismissCheckbox = document.getElementById('announcement-dismiss-permanently');

  function close() {
    var permanently = dismissCheckbox && dismissCheckbox.checked;
    modal.hidden = true;
    document.body.classList.remove('modal-open');
    if (!permanently || !recipientId) return;

    var headers = {};
    if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
    fetch('/mypage/announcements/' + recipientId + '/dismiss', {
      method: 'POST',
      headers: headers,
      keepalive: true
    }).catch(function () {
      // 通信に失敗しても、このブラウザ・このセッションでは既にモーダルを閉じているため
      // 閲覧体験には影響させない(次回ログイン時に再表示される可能性が残るのみ)。
    });
  }

  document.body.classList.add('modal-open');
  modal.querySelectorAll('[data-announcement-close]').forEach(function (el) {
    el.addEventListener('click', close);
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && !modal.hidden) close();
  });
})();
