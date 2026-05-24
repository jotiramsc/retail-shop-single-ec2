import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable';
import Panel from '../components/Panel';
import { showToast } from '../components/ToastHost';
import { retailService } from '../services/retailService';
import { getStoredAuthSession } from '../utils/auth';
import { formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

export default function CustomerReviewsPage() {
  const [rows, setRows] = useState([]);
  const [pagination, setPagination] = useState({ page: 0, size: 10, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const auth = getStoredAuthSession();
  const isAdmin = ['ADMIN', 'OWNER'].includes(auth?.role);

  const loadReviews = async (page = pagination.page || 0) => {
    setLoading(true);
    setError('');
    try {
      const response = await retailService.getAdminReviews({ page, size: pagination.size || 10 });
      setRows(response.items || []);
      setPagination({
        page: response.page || 0,
        size: response.size || 10,
        totalPages: response.totalPages || 0,
        totalItems: response.totalItems || 0,
        hasNext: Boolean(response.hasNext),
        hasPrevious: Boolean(response.hasPrevious)
      });
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load reviews.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReviews(0);
  }, []);

  const updateReview = async (review, approved) => {
    setError('');
    try {
      await retailService.updateReview(review.id, { approved });
      showToast({
        title: approved ? 'Review shown' : 'Review hidden',
        message: approved ? 'Review is visible on the website.' : 'Review hidden from the website.'
      });
      await loadReviews(pagination.page);
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Unable to update review.');
      setError(message);
      showToast({ title: 'Review update failed', message, tone: 'error' });
    }
  };

  const deleteReview = async (review) => {
    setError('');
    try {
      await retailService.deleteReview(review.id);
      showToast({ title: 'Review deleted', message: 'Customer review was removed.' });
      await loadReviews(pagination.page);
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Unable to delete review.');
      setError(message);
      showToast({ title: 'Delete failed', message, tone: 'error' });
    }
  };

  const columns = [
    { key: 'customerName', label: 'Customer', render: (row) => <div><strong>{row.customerName}</strong><span>{row.city || 'No city'}</span></div> },
    { key: 'rating', label: 'Rating', render: (row) => <span className="glow-stars compact-stars">{'★'.repeat(Number(row.rating || 0))}</span> },
    { key: 'comment', label: 'Review', render: (row) => <div><strong>{row.product || 'Storefront'}</strong><span>{row.comment}</span></div> },
    { key: 'approved', label: 'Website', render: (row) => <span className={`status-badge ${row.approved ? 'success' : 'warning'}`}>{row.approved ? 'Shown' : 'Hidden'}</span> },
    { key: 'createdAt', label: 'Created', render: (row) => formatDate(row.createdAt) },
    { key: 'actions', label: 'Actions', render: (row) => (
      <div className="table-action-group">
        <button type="button" className="ghost-btn compact-btn" onClick={() => updateReview(row, !row.approved)}>{row.approved ? 'Hide' : 'Show'}</button>
        {isAdmin ? <button type="button" className="danger-btn compact-btn" onClick={() => deleteReview(row)}>Delete</button> : null}
      </div>
    ) }
  ];

  return (
    <div className="sneat-module-page">
      <section className="sneat-page-title">
        <div>
          <span className="sneat-eyebrow">Customer CRM</span>
          <h1>Reviews & ratings</h1>
          <p>Approve customer-submitted Worn & Loved reviews before they appear on the storefront.</p>
        </div>
      </section>
      <Panel title="Review moderation" subtitle={loading ? 'Loading latest feedback...' : `${pagination.totalItems || 0} total reviews`}>
        {error ? <p className="error-text">{error}</p> : null}
        <DataTable columns={columns} rows={rows} pagination={pagination} onPageChange={loadReviews} emptyMessage="No reviews submitted yet." />
      </Panel>
    </div>
  );
}
