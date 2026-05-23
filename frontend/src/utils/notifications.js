const canUseDom = () => typeof window !== 'undefined' && typeof document !== 'undefined';

function ensureToastHost() {
  let host = document.querySelector('.kps-toast-host');
  if (!host) {
    host = document.createElement('div');
    host.className = 'kps-toast-host';
    host.setAttribute('aria-live', 'polite');
    host.setAttribute('aria-atomic', 'true');
    document.body.appendChild(host);
  }
  return host;
}

export function showToast({ title = '', message = '', tone = 'success', duration = 3600 } = {}) {
  if (!canUseDom()) return;
  const host = ensureToastHost();
  const toast = document.createElement('div');
  toast.className = `kps-toast is-${tone}`;
  toast.innerHTML = `
    <span class="kps-toast-icon"><i class="bx ${tone === 'error' ? 'bx-error-circle' : tone === 'warning' ? 'bx-error' : 'bx-check-circle'}"></i></span>
    <span class="kps-toast-copy">
      ${title ? `<strong>${escapeHtml(title)}</strong>` : ''}
      ${message ? `<small>${escapeHtml(message)}</small>` : ''}
    </span>
    <button type="button" class="kps-toast-close" aria-label="Dismiss notification"><i class="bx bx-x"></i></button>
  `;
  host.appendChild(toast);
  const close = () => {
    toast.classList.add('is-leaving');
    window.setTimeout(() => toast.remove(), 180);
  };
  toast.querySelector('.kps-toast-close')?.addEventListener('click', close);
  window.setTimeout(close, duration);
}

export function showSuccess(message, title = 'Done') {
  showToast({ title, message, tone: 'success' });
}

export function showError(message, title = 'Error') {
  showToast({ title, message, tone: 'error', duration: 5600 });
}

export function showWarning(message, title = 'Warning') {
  showToast({ title, message, tone: 'warning', duration: 4800 });
}

export function confirmAction({
  title = 'Are you sure?',
  message = '',
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  tone = 'warning'
} = {}) {
  if (!canUseDom()) return Promise.resolve(false);
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.className = 'kps-modal-overlay';
    overlay.innerHTML = `
      <section class="kps-swal is-${tone}" role="dialog" aria-modal="true" aria-labelledby="kps-swal-title">
        <span class="kps-swal-icon"><i class="bx ${tone === 'danger' ? 'bx-trash' : tone === 'error' ? 'bx-error-circle' : 'bx-info-circle'}"></i></span>
        <h3 id="kps-swal-title">${escapeHtml(title)}</h3>
        ${message ? `<p>${escapeHtml(message)}</p>` : ''}
        <div class="kps-swal-actions">
          <button type="button" class="ghost-btn compact-btn" data-action="cancel">${escapeHtml(cancelText)}</button>
          <button type="button" class="primary-btn compact-btn ${tone === 'danger' ? 'is-danger' : ''}" data-action="confirm">${escapeHtml(confirmText)}</button>
        </div>
      </section>
    `;
    document.body.appendChild(overlay);
    const cleanup = (result) => {
      overlay.classList.add('is-leaving');
      window.setTimeout(() => overlay.remove(), 160);
      resolve(result);
    };
    overlay.addEventListener('click', (event) => {
      if (event.target === overlay || event.target?.dataset?.action === 'cancel') cleanup(false);
      if (event.target?.dataset?.action === 'confirm') cleanup(true);
    });
    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        document.removeEventListener('keydown', onKeyDown);
        cleanup(false);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    overlay.querySelector('[data-action="confirm"]')?.focus();
  });
}

export function promptAction({
  title = 'Add details',
  message = '',
  placeholder = '',
  confirmText = 'Save',
  cancelText = 'Cancel',
  required = true
} = {}) {
  if (!canUseDom()) return Promise.resolve('');
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.className = 'kps-modal-overlay';
    overlay.innerHTML = `
      <section class="kps-swal is-info" role="dialog" aria-modal="true" aria-labelledby="kps-prompt-title">
        <span class="kps-swal-icon"><i class="bx bx-message-square-edit"></i></span>
        <h3 id="kps-prompt-title">${escapeHtml(title)}</h3>
        ${message ? `<p>${escapeHtml(message)}</p>` : ''}
        <textarea class="kps-swal-input" rows="4" placeholder="${escapeHtml(placeholder)}"></textarea>
        <div class="kps-swal-actions">
          <button type="button" class="ghost-btn compact-btn" data-action="cancel">${escapeHtml(cancelText)}</button>
          <button type="button" class="primary-btn compact-btn" data-action="confirm">${escapeHtml(confirmText)}</button>
        </div>
      </section>
    `;
    document.body.appendChild(overlay);
    const input = overlay.querySelector('.kps-swal-input');
    input?.focus();
    const cleanup = (result) => {
      overlay.classList.add('is-leaving');
      window.setTimeout(() => overlay.remove(), 160);
      resolve(result);
    };
    overlay.addEventListener('click', (event) => {
      if (event.target === overlay || event.target?.dataset?.action === 'cancel') cleanup('');
      if (event.target?.dataset?.action === 'confirm') {
        const value = input?.value?.trim() || '';
        if (required && !value) {
          input?.classList.add('is-invalid');
          return;
        }
        cleanup(value);
      }
    });
  });
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
