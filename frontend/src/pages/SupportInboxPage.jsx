import { useEffect, useMemo, useRef, useState } from 'react';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const STATUS_OPTIONS = ['', 'OPEN', 'IN_PROGRESS', 'RESOLVED'];

const productPrice = (product) => Number(product.websitePrice || product.sellingPrice || 0);

const parsePriceFilter = (query) => {
  const normalized = query.toLowerCase();
  const range = normalized.match(/(?:rs|₹)?\s*(\d{2,7})\s*(?:-|to)\s*(?:rs|₹)?\s*(\d{2,7})/);
  if (range) {
    return { min: Math.min(Number(range[1]), Number(range[2])), max: Math.max(Number(range[1]), Number(range[2])) };
  }
  const max = normalized.match(/(?:under|below|less than|upto|up to|<)\s*(?:rs|₹)?\s*(\d{2,7})/);
  if (max) {
    return { max: Number(max[1]) };
  }
  const min = normalized.match(/(?:above|over|more than|>)\s*(?:rs|₹)?\s*(\d{2,7})/);
  if (min) {
    return { min: Number(min[1]) };
  }
  return {};
};

const stripPriceTerms = (query) => query
  .replace(/(?:rs|₹)?\s*\d{2,7}\s*(?:-|to)\s*(?:rs|₹)?\s*\d{2,7}/gi, ' ')
  .replace(/(?:under|below|less than|upto|up to|above|over|more than|<|>)\s*(?:rs|₹)?\s*\d{2,7}/gi, ' ')
  .replace(/\s+/g, ' ')
  .trim();

const stockLabel = (product) => Number(product.quantity || 0) > 0 ? 'Available now' : 'Out of stock';

export default function SupportInboxPage() {
  const [summary, setSummary] = useState({ openCount: 0, unreadCount: 0 });
  const [conversations, setConversations] = useState([]);
  const [selected, setSelected] = useState(null);
  const [products, setProducts] = useState([]);
  const [filters, setFilters] = useState({ status: '', search: '' });
  const [replyText, setReplyText] = useState('');
  const [showProductPicker, setShowProductPicker] = useState(false);
  const [productSearch, setProductSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [toast, setToast] = useState('');
  const chatEndRef = useRef(null);

  const loadSummary = async () => {
    const next = await retailService.getSupportSummary();
    setSummary((current) => {
      if (Number(next?.unreadCount || 0) > Number(current?.unreadCount || 0)) {
        setToast('New WhatsApp support message');
      }
      return next || { openCount: 0, unreadCount: 0 };
    });
  };

  const loadConversations = async (nextFilters = filters) => {
    const rows = await retailService.getSupportConversations(nextFilters);
    setConversations(rows || []);
  };

  const loadSelected = async (conversationId = selected?.id) => {
    if (!conversationId) {
      return null;
    }
    const detail = await retailService.getSupportConversation(conversationId);
    setSelected(detail);
    return detail;
  };

  useEffect(() => {
    Promise.all([
      loadSummary(),
      loadConversations(),
      retailService.getProducts({ page: 0, size: 250 }).then((page) => setProducts(page.items || []))
    ]).catch((requestError) => setError(getApiErrorMessage(requestError, 'Unable to load support inbox.')));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      Promise.all([loadSummary(), loadConversations(), loadSelected()]).catch(() => {});
    }, 5000);
    return () => window.clearInterval(timer);
  }, [filters, selected?.id]);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timeout = window.setTimeout(() => setToast(''), 4500);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  const filteredProducts = useMemo(() => {
    const priceFilter = parsePriceFilter(productSearch);
    const query = stripPriceTerms(productSearch).toLowerCase();
    return products
      .filter((product) => product.showOnWebsite !== false)
      .filter((product) => {
        const price = productPrice(product);
        const matchesText = !query || [product.name, product.category, product.sku]
          .some((value) => String(value || '').toLowerCase().includes(query));
        const matchesMin = priceFilter.min == null || price >= priceFilter.min;
        const matchesMax = priceFilter.max == null || price <= priceFilter.max;
        return matchesText && matchesMin && matchesMax;
      })
      .slice(0, 30);
  }, [productSearch, products]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ block: 'end' });
  }, [selected?.id, selected?.messages?.length]);

  const updateFilters = async (patch) => {
    const next = { ...filters, ...patch };
    setFilters(next);
    setError('');
    await loadConversations(next);
  };

  const openConversation = async (conversationId) => {
    setError('');
    setSuccess('');
    setSelected(await retailService.getSupportConversation(conversationId));
  };

  const sendReply = async (event) => {
    event.preventDefault();
    if (!selected?.id || !replyText.trim()) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      const detail = await retailService.sendSupportReply(selected.id, { message: replyText.trim() });
      setSelected(detail);
      setReplyText('');
      setSuccess('Reply sent to WhatsApp.');
      await Promise.all([loadSummary(), loadConversations()]);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to send reply.'));
    } finally {
      setLoading(false);
    }
  };

  const sendProduct = async (product) => {
    if (!selected?.id) {
      return;
    }
    if (Number(product.quantity || 0) <= 0) {
      const shouldSend = window.confirm(`${product.name} is out of stock. Send this suggestion anyway?`);
      if (!shouldSend) {
        return;
      }
    }
    setLoading(true);
    setError('');
    try {
      const detail = await retailService.sendSupportProduct(selected.id, { productId: product.id });
      setSelected(detail);
      setShowProductPicker(false);
      setSuccess('Product sent to WhatsApp.');
      await Promise.all([loadSummary(), loadConversations()]);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to send product.'));
    } finally {
      setLoading(false);
    }
  };

  const resolveConversation = async () => {
    if (!selected?.id) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      const detail = await retailService.resolveSupportConversation(selected.id);
      setSelected(detail);
      setSuccess('Conversation marked resolved.');
      await Promise.all([loadSummary(), loadConversations()]);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to resolve conversation.'));
    } finally {
      setLoading(false);
    }
  };

  const handleReplyKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      sendReply(event);
    }
  };

  return (
    <div className="page support-inbox-page">
      <PageHeader
        eyebrow="WhatsApp Support"
        title="Single-agent support inbox"
        description="Handle customer handoffs, reply on WhatsApp, share products from inventory, and close resolved chats."
      />

      {toast ? <div className="support-toast">{toast}</div> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {success ? <p className="success-text">{success}</p> : null}

      <div className="support-summary-row">
        <div><span>Open chats</span><strong>{summary.openCount || 0}</strong></div>
        <div><span>Unread messages</span><strong>{summary.unreadCount || 0}</strong></div>
      </div>

      <div className="support-grid">
        <Panel title="Customer chats" subtitle="Search WhatsApp conversations by phone, name, or latest message.">
          <div className="support-filter-row">
            <input placeholder="Search phone or name" value={filters.search} onChange={(event) => updateFilters({ search: event.target.value })} />
            <select value={filters.status} onChange={(event) => updateFilters({ status: event.target.value })}>
              {STATUS_OPTIONS.map((status) => <option key={status || 'ALL'} value={status}>{status || 'All statuses'}</option>)}
            </select>
          </div>
          <div className="support-conversation-list">
            {conversations.map((conversation) => (
              <button
                key={conversation.id}
                type="button"
                className={`support-conversation-card ${selected?.id === conversation.id ? 'is-active' : ''}`}
                onClick={() => openConversation(conversation.id)}
              >
                <strong>{conversation.customerName}</strong>
                <span>{conversation.phone}</span>
                <p>{conversation.latestMessage || 'No message text yet'}</p>
                <small>{conversation.status} · {formatDate(conversation.updatedAt)}</small>
                {conversation.unreadCount ? <em>{conversation.unreadCount}</em> : null}
              </button>
            ))}
            {!conversations.length ? <p className="page-description">No support conversations found.</p> : null}
          </div>
        </Panel>

        <Panel title={selected ? selected.customerName : 'Live chat'} subtitle={selected ? `${selected.phone} · ${selected.status}` : 'Open a WhatsApp conversation to reply.'}>
          {selected ? (
            <>
              <div className="support-chat-window">
                {(selected.messages || []).map((message) => (
                  <div key={message.id} className={`support-message ${message.direction === 'OUTBOUND' ? 'is-outbound' : 'is-inbound'}`}>
                    <span>{message.messageType}</span>
                    <p>{message.messageText}</p>
                    {message.messageType === 'PRODUCT' ? (
                      <div className={`support-message-status ${message.whatsAppStatus === 'SENT' ? 'is-sent' : 'is-failed'}`}>
                        <strong>{message.productName || 'Product suggestion'}</strong>
                        <small>{message.whatsAppStatus || 'SAVED'} · {message.sentBy || 'support-agent'} · {message.customerMobile || selected.phone}</small>
                      </div>
                    ) : null}
                    <small>{formatDate(message.createdAt)}</small>
                  </div>
                ))}
                <div ref={chatEndRef} />
              </div>
              <form className="support-reply-box" onSubmit={sendReply}>
                <textarea
                  value={replyText}
                  onChange={(event) => setReplyText(event.target.value)}
                  onKeyDown={handleReplyKeyDown}
                  placeholder="Type WhatsApp reply..."
                  disabled={loading}
                />
                <div>
                  <button type="button" className="ghost-btn compact-btn" disabled={loading} onClick={() => setShowProductPicker(true)}>Send Product</button>
                  <button type="button" className="ghost-btn compact-btn" disabled={loading || selected.status === 'RESOLVED'} onClick={resolveConversation}>Mark Resolved</button>
                  <button type="submit" className="primary-btn compact-btn" disabled={loading || !replyText.trim()}>{loading ? 'Sending...' : 'Reply'}</button>
                </div>
              </form>
            </>
          ) : (
            <p className="page-description">New WhatsApp handoffs appear here automatically while this page is open.</p>
          )}
        </Panel>
      </div>

      {showProductPicker ? (
        <div className="support-product-modal" role="dialog" aria-modal="true">
          <div className="support-product-dialog">
            <div className="support-dialog-head">
              <h3>Send Product</h3>
              <button type="button" className="ghost-btn compact-btn" onClick={() => setShowProductPicker(false)}>Close</button>
            </div>
            <input placeholder="Search by name, category, SKU, under 2000, 1000-3000" value={productSearch} onChange={(event) => setProductSearch(event.target.value)} />
            <div className="support-product-grid">
              {filteredProducts.map((product) => (
                <article key={product.id} className="support-product-card">
                  {product.imageDataUrl ? <img src={product.imageDataUrl} alt={product.name} /> : <div className="support-product-placeholder" />}
                  <strong>{product.name}</strong>
                  <span>{product.category} · {product.sku}</span>
                  <p>
                    {product.websitePrice && product.sellingPrice && Number(product.websitePrice) !== Number(product.sellingPrice)
                      ? <><s>{currency(product.sellingPrice)}</s> <b>{currency(product.websitePrice)}</b></>
                      : currency(product.websitePrice || product.sellingPrice)}
                  </p>
                  <p className={Number(product.quantity || 0) > 0 ? 'stock-ok' : 'stock-warning'}>{stockLabel(product)} · Qty {product.quantity ?? 0}</p>
                  <button type="button" className="primary-btn compact-btn" disabled={loading} onClick={() => sendProduct(product)}>Send to WhatsApp</button>
                </article>
              ))}
              {!filteredProducts.length ? <p className="page-description">No inventory products match this search.</p> : null}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
