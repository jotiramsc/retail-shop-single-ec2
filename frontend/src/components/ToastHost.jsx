import { useEffect, useState } from 'react';

let toastId = 0;

export function showToast({ title = 'Krishnai', message = '', tone = 'success' } = {}) {
  if (typeof window === 'undefined') {
    return;
  }
  window.dispatchEvent(new CustomEvent('kps-toast', {
    detail: {
      id: ++toastId,
      title,
      message,
      tone
    }
  }));
}

export default function ToastHost() {
  const [toasts, setToasts] = useState([]);

  useEffect(() => {
    const onToast = (event) => {
      const toast = event.detail || {};
      setToasts((current) => [...current, toast].slice(-4));
      window.setTimeout(() => {
        setToasts((current) => current.filter((item) => item.id !== toast.id));
      }, 4200);
    };
    window.addEventListener('kps-toast', onToast);
    return () => window.removeEventListener('kps-toast', onToast);
  }, []);

  if (!toasts.length) {
    return null;
  }

  return (
    <div className="kps-toast-host" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={`kps-toast is-${toast.tone || 'success'}`}>
          <span className="kps-toast-icon">{toast.tone === 'error' ? '!' : toast.tone === 'warning' ? 'i' : 'OK'}</span>
          <span className="kps-toast-copy">
            <strong>{toast.title}</strong>
            {toast.message ? <small>{toast.message}</small> : null}
          </span>
          <button
            type="button"
            className="kps-toast-close"
            aria-label="Dismiss notification"
            onClick={() => setToasts((current) => current.filter((item) => item.id !== toast.id))}
          >
            x
          </button>
        </div>
      ))}
    </div>
  );
}
