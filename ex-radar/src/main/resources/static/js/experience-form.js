(()=>{const box=document.querySelector('#life-events'),template=document.querySelector('#event-template'),add=document.querySelector('#add-event');if(!box||!template)return;function renumber(){box.querySelectorAll('.life-event-editor').forEach((row,i)=>{row.querySelector('strong').textContent=`出来事 ${i+1}`;row.querySelectorAll('input,textarea').forEach(field=>{const key=field.dataset.name||field.name.match(/\.(\w+)$/)?.[1];if(key)field.name=`lifeEvents[${i}].${key}`;});});add.disabled=box.children.length>=20;}add.addEventListener('click',()=>{if(box.children.length>=20)return;const row=template.content.firstElementChild.cloneNode(true);box.append(row);renumber();row.querySelector('input').focus();});box.addEventListener('click',event=>{if(event.target.classList.contains('remove-event')){event.target.closest('.life-event-editor').remove();renumber();}});renumber();})();
(()=>{const basic=document.querySelector('.experience-form .form-section');if(!basic||basic.querySelector('[name="tagNames"]'))return;const label=document.createElement('label');label.textContent='タグ（10個まで）';const input=document.createElement('input');input.name='tagNames';input.maxLength=300;input.placeholder='転職, IT, 未経験';input.value=new URLSearchParams(location.search).get('tagNames')||'';label.append(input);const help=document.createElement('small');help.textContent='カンマで区切って入力してください';label.append(help);basic.append(label);})();

/*
 * 二重送信防止(JS未対応環境でもsubmissionToken+DBのUNIQUE制約により安全性は
 * 担保されているため、これはあくまでUX向上のための補助)。
 * 送信ボタンを即座に無効化し、文言をローディング表示に変え、Enter連打や
 * 多重クリックによる再送信イベントそのものをブロックする。
 */
(() => {
  const form = document.querySelector('.experience-form');
  if (!form) return;
  let submitted = false;
  form.addEventListener('submit', (event) => {
    if (submitted) {
      event.preventDefault();
      return;
    }
    submitted = true;
    form.querySelectorAll('button[type="submit"]').forEach((button) => {
      const isPublish = button.id === 'publish-submit';
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');
      button.classList.add('is-submitting');
      button.textContent = isPublish ? '投稿中…' : '保存中…';
    });
  });
})();

/*
 * バリデーションエラーで再表示された場合、エラーサマリーへスムーズスクロールし、
 * 最初のエラー項目へフォーカスを移す(スクリーンリーダーにもエラーが伝わるように)。
 */
(() => {
  const summary = document.getElementById('error-summary');
  if (!summary) return;
  summary.scrollIntoView({ behavior: 'smooth', block: 'start' });
  const firstInvalid = document.querySelector('.experience-form .is-invalid');
  window.setTimeout(() => {
    (firstInvalid || summary).focus({ preventScroll: true });
  }, 450);
})();
