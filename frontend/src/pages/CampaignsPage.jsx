import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const CHANNELS = ['WHATSAPP', 'INSTAGRAM', 'FACEBOOK'];

const blankCampaign = {
  title: '',
  offerProduct: '',
  content: '',
  mediaUrl: '',
  hashtags: '',
  linkUrl: '',
  channels: ['WHATSAPP']
};

export default function CampaignsPage() {
  const [historyPage, setHistoryPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [form, setForm] = useState(blankCampaign);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [uploadingMedia, setUploadingMedia] = useState(false);

  const loadHistory = async (page = 0) => {
    setHistoryPage(await retailService.getCampaignHistory({ page, size: 10 }));
  };

  useEffect(() => {
    loadHistory();
  }, []);

  const previewCaption = useMemo(() => {
    return [form.content, form.hashtags, form.linkUrl].filter(Boolean).join('\n\n');
  }, [form.content, form.hashtags, form.linkUrl]);

  const updateForm = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const toggleChannel = (channel) => {
    setForm((current) => {
      const nextChannels = current.channels.includes(channel)
        ? current.channels.filter((entry) => entry !== channel)
        : [...current.channels, channel];
      return {
        ...current,
        channels: nextChannels.length ? nextChannels : [channel]
      };
    });
  };

  const submit = async (publishNow) => {
    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      await retailService.createCampaign({
        ...form,
        publishNow
      });
      setForm(blankCampaign);
      setSuccess(publishNow ? 'Campaign published.' : 'Campaign saved as draft.');
      loadHistory();
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to save campaign.'));
    } finally {
      setSubmitting(false);
    }
  };

  const uploadMedia = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setUploadingMedia(true);
    setError('');
    try {
      const response = await retailService.uploadImage({ file, category: 'campaigns' });
      updateForm('mediaUrl', response?.url || response?.cloudfrontUrl || response?.imageUrl || '');
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to upload campaign media.'));
    } finally {
      setUploadingMedia(false);
      event.target.value = '';
    }
  };

  const publishDraft = async (campaignId) => {
    setError('');
    setSuccess('');
    try {
      await retailService.publishCampaign(campaignId);
      setSuccess('Draft published.');
      loadHistory(historyPage.page || 0);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to publish draft.'));
    }
  };

  const retryLog = async (campaignLogId) => {
    setError('');
    setSuccess('');
    try {
      await retailService.retryCampaignLog(campaignLogId);
      setSuccess('Publish retried.');
      loadHistory(historyPage.page || 0);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to retry publish.'));
    }
  };

  return (
    <div className="page marketing-admin-page">
      <PageHeader
        eyebrow="Marketing"
        title="Marketing Admin"
        description="Compose campaigns once, preview them, and publish or retry across WhatsApp, Instagram, and Facebook."
      />

      <div className="marketing-admin-grid">
        <Panel title="Campaign composer" subtitle="Choose channels, media, and publish mode from one place.">
          <form className="form-grid marketing-admin-form" onSubmit={(event) => event.preventDefault()}>
            <input placeholder="Campaign title" value={form.title} onChange={(event) => updateForm('title', event.target.value)} />
            <input placeholder="Offer or product" value={form.offerProduct} onChange={(event) => updateForm('offerProduct', event.target.value)} />
            <textarea placeholder="Caption or message" rows="6" value={form.content} onChange={(event) => updateForm('content', event.target.value)} required />
            <input placeholder="#hashtags" value={form.hashtags} onChange={(event) => updateForm('hashtags', event.target.value)} />
            <input placeholder="Destination link" value={form.linkUrl} onChange={(event) => updateForm('linkUrl', event.target.value)} />
            <input placeholder="Media URL" value={form.mediaUrl} onChange={(event) => updateForm('mediaUrl', event.target.value)} />

            <label className="marketing-upload-field">
              <span>Upload media</span>
              <input type="file" accept="image/*" onChange={uploadMedia} />
              <strong>{uploadingMedia ? 'Uploading...' : 'Choose image'}</strong>
            </label>

            <div className="marketing-channel-pills">
              {CHANNELS.map((channel) => (
                <button
                  key={channel}
                  type="button"
                  className={`ghost-btn compact-btn ${form.channels.includes(channel) ? 'marketing-channel-pill-active' : ''}`}
                  onClick={() => toggleChannel(channel)}
                >
                  {channel}
                </button>
              ))}
            </div>

            {error ? <p className="error-text">{error}</p> : null}
            {success ? <p className="success-text">{success}</p> : null}

            <div className="checkout-actions">
              <button className="ghost-btn compact-btn" type="button" disabled={submitting} onClick={() => submit(false)}>
                {submitting ? 'Saving...' : 'Save draft'}
              </button>
              <button className="primary-btn compact-btn" type="button" disabled={submitting} onClick={() => submit(true)}>
                {submitting ? 'Publishing...' : 'Publish now'}
              </button>
            </div>
          </form>
        </Panel>

        <Panel title="Preview" subtitle="Quick visual check before the campaign goes live.">
          <div className="marketing-preview-card">
            {form.mediaUrl ? (
              <img src={form.mediaUrl} alt={form.title || 'Campaign preview'} />
            ) : (
              <div className="marketing-preview-placeholder">Media preview</div>
            )}
            <div className="marketing-preview-copy">
              <div className="marketing-preview-channels">
                {form.channels.map((channel) => (
                  <span key={channel} className="customer-offer-status">{channel}</span>
                ))}
              </div>
              <h3>{form.title || 'Untitled campaign'}</h3>
              {form.offerProduct ? <strong>{form.offerProduct}</strong> : null}
              <p>{previewCaption || 'Caption, hashtags, and links will show here.'}</p>
            </div>
          </div>
        </Panel>
      </div>

      <Panel title="Publish history" subtitle="Track draft, published, and failed channel activity with retry controls.">
        <DataTable
          columns={[
            { key: 'campaignName', label: 'Campaign' },
            { key: 'channel', label: 'Channel' },
            { key: 'status', label: 'Status' },
            {
              key: 'publishedBy',
              label: 'Published By',
              render: (row) => row.publishedBy || 'Draft'
            },
            {
              key: 'publishedAt',
              label: 'Published',
              render: (row) => row.publishedAt ? formatDate(row.publishedAt) : 'Not yet'
            },
            {
              key: 'errorMessage',
              label: 'Error',
              render: (row) => row.errorMessage || '—'
            },
            {
              key: 'actions',
              label: 'Actions',
              render: (row) => {
                if (row.status === 'FAILED') {
                  return (
                    <button type="button" className="ghost-btn compact-btn" onClick={() => retryLog(row.id)}>
                      Retry
                    </button>
                  );
                }
                if (row.status === 'DRAFT') {
                  return (
                    <button type="button" className="ghost-btn compact-btn" onClick={() => publishDraft(row.campaignId)}>
                      Publish
                    </button>
                  );
                }
                return '—';
              }
            }
          ]}
          rows={historyPage.items || []}
          pagination={historyPage}
          onPageChange={loadHistory}
        />
      </Panel>
    </div>
  );
}
