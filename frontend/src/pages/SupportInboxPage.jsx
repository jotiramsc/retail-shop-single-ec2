import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const STATUS_OPTIONS = ['', 'OPEN', 'IN_PROGRESS', 'RESOLVED'];

const productPrice = (product) => Number(product.offerPrice || product.websitePrice || product.sellingPrice || 0);

const hasDeal = (product) => Number(product?.youSave || 0) > 0;

function SupportProductPrice({ product }) {
  if (hasDeal(product)) {
    return (
      <div className="support-product-price-stack">
        <span><s>{currency(product.originalPrice || product.websitePrice || product.sellingPrice)}</s> <b>{currency(product.offerPrice)}</b></span>
        <em>{Number(product.discountPercent || 0)}% OFF · You Save {currency(product.youSave)}</em>
        <small>{product.couponCode ? `Coupon: ${product.couponCode}` : product.offerName || 'Best Deal'}</small>
      </div>
    );
  }
  return <p>{currency(product.offerPrice || product.websitePrice || product.sellingPrice)}</p>;
}

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

const initialsFor = (name, fallback = '?') => String(name || fallback || '?')
  .trim()
  .split(/\s+/)
  .slice(0, 2)
  .map((part) => part.charAt(0).toUpperCase())
  .join('') || '?';

const statusLabel = (status) => String(status || 'ACTIVE').replace(/_/g, ' ');

const isRecentlyActive = (value) => {
  if (!value) {
    return false;
  }
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) && Date.now() - timestamp < 1000 * 60 * 15;
};

function SupportIcon({ name }) {
  const paths = {
    call: 'M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.12.9.33 1.77.63 2.61a2 2 0 0 1-.45 2.11L8 9.73a16 16 0 0 0 6.27 6.27l1.29-1.29a2 2 0 0 1 2.11-.45c.84.3 1.71.51 2.61.63A2 2 0 0 1 22 16.92Z',
    video: 'M23 7 16 12l7 5V7ZM14 5H3a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2Z',
    search: 'm21 21-4.35-4.35M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z',
    menu: 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2ZM19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2ZM5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z',
    attach: 'M21.44 11.05 12.2 20.29a6 6 0 0 1-8.49-8.49l9.24-9.24a4 4 0 0 1 5.66 5.66l-9.24 9.24a2 2 0 1 1-2.83-2.83l8.49-8.48',
    emoji: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20ZM8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01',
    mic: 'M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3ZM19 10v2a7 7 0 0 1-14 0v-2M12 19v4M8 23h8',
    send: 'm22 2-7 20-4-9-9-4 20-7ZM22 2 11 13'
  };
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d={paths[name]} />
    </svg>
  );
}

export default function SupportInboxPage({
  initialTab = 'ACTIVE',
  hidePageHeader = false,
  hideTabs = false
}) {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const [summary, setSummary] = useState({ openCount: 0, unreadCount: 0 });
  const [conversations, setConversations] = useState([]);
  const [selected, setSelected] = useState(null);
  const [products, setProducts] = useState([]);
  const [filters, setFilters] = useState({ status: '', search: '' });
  const [activeTab, setActiveTab] = useState(initialTab === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE');
  const [archiveFilters, setArchiveFilters] = useState({ fromDate: '', toDate: '' });
  const [replyText, setReplyText] = useState('');
  const [showProductPicker, setShowProductPicker] = useState(false);
  const [productSearch, setProductSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [toast, setToast] = useState('');
  const chatEndRef = useRef(null);

  useEffect(() => {
    if (hideTabs) return;
    const nextTab = requestedTab === 'archived' ? 'ARCHIVED' : requestedTab === 'active' ? 'ACTIVE' : '';
    if (nextTab && nextTab !== activeTab) {
      switchTab(nextTab).catch((requestError) => setError(getApiErrorMessage(requestError, 'Unable to switch support inbox.')));
    }
  }, [requestedTab, hideTabs]);

  useEffect(() => {
    if (!hideTabs) return;
    const nextTab = initialTab === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE';
    if (nextTab !== activeTab) {
      switchTab(nextTab).catch((requestError) => setError(getApiErrorMessage(requestError, 'Unable to switch support inbox.')));
    }
  }, [hideTabs, initialTab]);

  const loadSummary = async () => {
    const next = await retailService.getSupportSummary();
    setSummary((current) => {
      if (Number(next?.unreadCount || 0) > Number(current?.unreadCount || 0)) {
        setToast('New WhatsApp support message');
      }
      return next || { openCount: 0, unreadCount: 0 };
    });
  };

  const activeConversationFilters = (nextFilters = filters, nextTab = activeTab, nextArchiveFilters = archiveFilters) => ({
    ...nextFilters,
    ...(nextTab === 'ARCHIVED' ? { status: 'RESOLVED', ...nextArchiveFilters } : { status: nextFilters.status || 'ACTIVE' })
  });

  const loadConversations = async (nextFilters = filters, nextTab = activeTab, nextArchiveFilters = archiveFilters) => {
    const rows = await retailService.getSupportConversations(activeConversationFilters(nextFilters, nextTab, nextArchiveFilters));
    setConversations(Array.isArray(rows) ? rows : []);
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
      loadConversations(filters, activeTab, archiveFilters),
      retailService.getProducts({ page: 0, size: 250 }).then((page) => setProducts(page.items || []))
    ]).catch((requestError) => setError(getApiErrorMessage(requestError, 'Unable to load support inbox.')));
  }, []);

  useEffect(() => {
    const conversationId = new URLSearchParams(location.search).get('conversationId');
    if (!conversationId) {
      return;
    }
    setActiveTab('ACTIVE');
    loadSelected(conversationId).catch((requestError) => setError(getApiErrorMessage(requestError, 'Unable to open support conversation.')));
  }, [location.search]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      Promise.all([loadSummary(), loadConversations(filters, activeTab, archiveFilters), loadSelected()]).catch(() => {});
    }, 5000);
    return () => window.clearInterval(timer);
  }, [filters, activeTab, archiveFilters, selected?.id]);

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
    await loadConversations(next, activeTab, archiveFilters);
  };

  const updateArchiveFilters = async (patch) => {
    const next = { ...archiveFilters, ...patch };
    setArchiveFilters(next);
    setError('');
    await loadConversations(filters, activeTab, next);
  };

  const switchTab = async (tab) => {
    setActiveTab(tab);
    if (!hideTabs) {
      setSearchParams(tab === 'ACTIVE' ? {} : { tab: 'archived' });
    }
    setSelected(null);
    const nextFilters = tab === 'ARCHIVED' ? { ...filters, status: 'RESOLVED' } : { ...filters, status: '' };
    setFilters(nextFilters);
    await loadConversations(nextFilters, tab, archiveFilters);
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
      await Promise.all([loadSummary(), loadConversations(filters, activeTab, archiveFilters)]);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to resolve conversation.'));
    } finally {
      setLoading(false);
    }
  };

  const reopenConversation = async () => {
    if (!selected?.id) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      const detail = await retailService.reopenSupportConversation(selected.id);
      setSelected(detail);
      setSuccess('Conversation reopened.');
      await Promise.all([loadSummary(), loadConversations(filters, activeTab, archiveFilters)]);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to reopen conversation.'));
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
      {hidePageHeader ? null : (
        <PageHeader
          eyebrow="WhatsApp Support"
          title="Single-agent support inbox"
          description="Handle customer handoffs, reply on WhatsApp, share products from inventory, and close resolved chats."
        />
      )}

      {toast ? <div className="support-toast">{toast}</div> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {success ? <p className="success-text">{success}</p> : null}

      <section className="support-shell" aria-label="Support chat workspace">
        <aside className="support-sidebar" aria-label="Conversations">
          <div className="support-sidebar-head">
            <div>
              <span>Inbox</span>
              <strong>{activeTab === 'ARCHIVED' ? 'Archived chats' : 'Live support'}</strong>
            </div>
            <div className="support-agent-status"><i /> Online</div>
          </div>

          <div className="support-summary-row">
            <div><span>Open</span><strong>{summary.openCount || 0}</strong></div>
            <div><span>Unread</span><strong>{summary.unreadCount || 0}</strong></div>
          </div>

          {hideTabs ? null : <div className="support-tabs">
            <button type="button" className={activeTab === 'ACTIVE' ? 'is-active' : ''} onClick={() => switchTab('ACTIVE')}>Active</button>
            <button type="button" className={activeTab === 'ARCHIVED' ? 'is-active' : ''} onClick={() => switchTab('ARCHIVED')}>Archived</button>
          </div>}

          <div className="support-filter-row">
            <label className="support-search-field">
              <SupportIcon name="search" />
              <input placeholder="Search conversations" value={filters.search} onChange={(event) => updateFilters({ search: event.target.value })} />
            </label>
            {activeTab === 'ARCHIVED' ? (
              <div className="support-date-filters">
                <input type="date" value={archiveFilters.fromDate} onChange={(event) => updateArchiveFilters({ fromDate: event.target.value })} />
                <input type="date" value={archiveFilters.toDate} onChange={(event) => updateArchiveFilters({ toDate: event.target.value })} />
              </div>
            ) : (
              <select value={filters.status} onChange={(event) => updateFilters({ status: event.target.value })}>
                {STATUS_OPTIONS.filter((status) => status !== 'RESOLVED').map((status) => <option key={status || 'ALL'} value={status}>{status ? statusLabel(status) : 'All active statuses'}</option>)}
              </select>
            )}
          </div>

          <div className="support-conversation-list">
            {conversations.map((conversation) => {
              const active = selected?.id === conversation.id;
              const online = isRecentlyActive(conversation.updatedAt);
              return (
                <button
                  key={conversation.id}
                  type="button"
                  className={`support-conversation-card ${active ? 'is-active' : ''}`}
                  onClick={() => openConversation(conversation.id)}
                >
                  <span className="support-avatar" data-online={online}>
                    {initialsFor(conversation.customerName, conversation.phone)}
                  </span>
                  <span className="support-conversation-main">
                    <strong>{conversation.customerName || 'Customer'}</strong>
                    <small>{conversation.phone} · {formatDate(conversation.updatedAt)}</small>
                    <p>{conversation.latestMessage || 'No message text yet'}</p>
                    {conversation.resolvedAt ? <small>Resolved {formatDate(conversation.resolvedAt)}{conversation.resolvedBy ? ` by ${conversation.resolvedBy}` : ''}</small> : null}
                  </span>
                  <span className="support-conversation-meta">
                    <i>{statusLabel(conversation.status)}</i>
                    {conversation.unreadCount ? <em>{conversation.unreadCount}</em> : null}
                  </span>
                </button>
              );
            })}
            {!conversations.length ? (
              <div className="support-empty-state">
                <span />
                <strong>No conversations found</strong>
                <p>New WhatsApp handoffs and matching search results will appear here automatically.</p>
              </div>
            ) : null}
          </div>
        </aside>

        <main className="support-chat-panel" aria-label="Selected conversation">
          {selected ? (
            <>
              <header className="support-chat-header">
                <div className="support-chat-identity">
                  <span className="support-avatar is-large" data-online={isRecentlyActive(selected.updatedAt)}>
                    {initialsFor(selected.customerName, selected.phone)}
                  </span>
                  <div>
                    <strong>{selected.customerName || 'Customer'}</strong>
                    <span>{selected.phone} · {statusLabel(selected.status)}{selected.resolvedAt ? ` · Resolved ${formatDate(selected.resolvedAt)}` : ''}</span>
                  </div>
                </div>
                <div className="support-chat-actions">
                  <button type="button" aria-label="Call customer"><SupportIcon name="call" /></button>
                  <button type="button" aria-label="Start video call"><SupportIcon name="video" /></button>
                  <button type="button" aria-label="Search conversation"><SupportIcon name="search" /></button>
                  <button type="button" aria-label="More actions"><SupportIcon name="menu" /></button>
                </div>
              </header>

              <div className="support-chat-window">
                {(selected.messages || []).map((message) => {
                  const outbound = message.direction === 'OUTBOUND';
                  return (
                    <article key={message.id} className={`support-message ${outbound ? 'is-outbound' : 'is-inbound'}`}>
                      {!outbound ? <span className="support-avatar is-message" data-online="true">{initialsFor(selected.customerName, selected.phone)}</span> : null}
                      <div className="support-message-bubble">
                        <span>{message.messageType}</span>
                        <p>{message.messageText}</p>
                        {message.messageType === 'PRODUCT' ? (
                          <div className={`support-message-status ${message.whatsAppStatus === 'SENT' ? 'is-sent' : 'is-failed'}`}>
                            <strong>{message.productName || 'Product suggestion'}</strong>
                            <small>{message.whatsAppStatus || 'SAVED'} · {message.sentBy || 'support-agent'} · {message.customerMobile || selected.phone}</small>
                          </div>
                        ) : null}
                        <small>{formatDate(message.createdAt)}{outbound ? ` · ${message.whatsAppStatus || 'SAVED'}` : ''}</small>
                      </div>
                    </article>
                  );
                })}
                {loading ? (
                  <div className="support-typing-indicator" aria-live="polite">
                    <i /><i /><i />
                    <span>Sending reply</span>
                  </div>
                ) : null}
                <div ref={chatEndRef} />
              </div>

              <form className="support-reply-box" onSubmit={sendReply}>
                <div className="support-composer">
                  <div className="support-composer-tools">
                    <button type="button" aria-label="Attach file" disabled={loading}><SupportIcon name="attach" /></button>
                    <button type="button" aria-label="Choose emoji" disabled={loading}><SupportIcon name="emoji" /></button>
                  </div>
                  <textarea
                    value={replyText}
                    onChange={(event) => setReplyText(event.target.value)}
                    onKeyDown={handleReplyKeyDown}
                    placeholder="Type a helpful WhatsApp reply..."
                    disabled={loading}
                  />
                  <div className="support-composer-actions">
                    <button type="button" aria-label="Record voice message" disabled={loading}><SupportIcon name="mic" /></button>
                    <button type="submit" className="support-send-btn" aria-label="Send reply" disabled={loading || !replyText.trim()}>
                      <SupportIcon name="send" />
                    </button>
                  </div>
                </div>
                <div className="support-conversation-tools">
                  <button type="button" className="ghost-btn compact-btn" disabled={loading} onClick={() => setShowProductPicker(true)}>Send Product</button>
                  <button type="button" className="ghost-btn compact-btn" disabled={loading || selected.status === 'RESOLVED'} onClick={resolveConversation}>Mark Resolved</button>
                  <button type="button" className="ghost-btn compact-btn" disabled={loading || selected.status !== 'RESOLVED'} onClick={reopenConversation}>Reopen</button>
                </div>
              </form>
            </>
          ) : (
            <div className="support-empty-chat">
              <span />
              <strong>Select a conversation</strong>
              <p>Open a WhatsApp handoff to reply, share products, review status, and close the conversation from one calm workspace.</p>
            </div>
          )}
        </main>
      </section>

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
                  <SupportProductPrice product={product} />
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
