const tabs = [...document.querySelectorAll('[data-guide]')];
function selectGuide(name, focus = false) {
  tabs.forEach(tab => {
    const active = tab.dataset.guide === name;
    tab.setAttribute('aria-selected', String(active));
    tab.tabIndex = active ? 0 : -1;
    document.getElementById(tab.getAttribute('aria-controls')).hidden = !active;
    if (active && focus) tab.focus();
  });
}
tabs.forEach((tab, index) => {
  tab.addEventListener('click', () => selectGuide(tab.dataset.guide));
  tab.addEventListener('keydown', event => {
    let next;
    if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
    if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = tabs.length - 1;
    if (next !== undefined) { event.preventDefault(); selectGuide(tabs[next].dataset.guide, true); }
  });
});
function guideFromHash() {
  const name = location.hash.replace('#guide-', '');
  if (tabs.some(tab => tab.dataset.guide === name)) {
    selectGuide(name);
    requestAnimationFrame(() => document.getElementById('install').scrollIntoView({block:'start'}));
  }
}
guideFromHash();
window.addEventListener('hashchange', guideFromHash);
document.querySelectorAll('[data-guide-link]').forEach(link => link.addEventListener('click', () => selectGuide(link.dataset.guideLink)));
document.querySelectorAll('[data-copy]').forEach(button => {
  button.addEventListener('click', async () => {
    const code = button.previousElementSibling.querySelector('code').textContent;
    const status = document.getElementById('copy-status');
    try {
      await navigator.clipboard.writeText(code);
      const original = button.textContent;
      button.textContent = 'Copied'; status.textContent = 'Commands copied. Replace the uppercase placeholders before running.';
      setTimeout(() => { button.textContent = original; }, 2000);
    } catch {
      status.textContent = 'Copy is unavailable. Select the commands above and copy them manually.';
      button.textContent = 'Select the commands to copy';
    }
  });
});
