const stage = document.querySelector('#stage');
function setView(view) {
  stage.classList.toggle('mobile', view === 'mobile');
  document.querySelectorAll('[data-view]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.view === view)));
  document.querySelector('#hint').textContent = view === 'mobile' ? '触控布局 · 左右滑动切换推荐' : '方向键移动 · Enter 确认 · Esc 返回';
  history.replaceState(null, '', view === 'mobile' ? '?view=mobile' : '/');
}
document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => setView(button.dataset.view)));
setView(new URLSearchParams(location.search).get('view') === 'mobile' ? 'mobile' : 'tv');
