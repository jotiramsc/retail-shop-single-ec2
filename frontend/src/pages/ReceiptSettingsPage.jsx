import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { getApiErrorMessage } from '../utils/validation';

function LabeledField({ label, note, children }) {
  return (
    <label className="settings-field">
      <span className="input-label">{label}</span>
      {note ? <span className="settings-field-note">{note}</span> : null}
      {children}
    </label>
  );
}

function ImageUploadField({ id, label, note, value, category, onChange, onRemove, onError }) {
  const [uploading, setUploading] = useState(false);

  const handleFileChange = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    if (!file.type.startsWith('image/')) {
      onError?.('Please choose a valid image file.');
      return;
    }

    setUploading(true);
    try {
      const upload = await retailService.uploadImage({ file, category });
      onChange(upload.cloudfrontUrl);
    } catch (requestError) {
      onError?.(getApiErrorMessage(requestError, 'Unable to upload image.'));
    } finally {
      setUploading(false);
      event.target.value = '';
    }
  };

  return (
    <div className="image-picker">
      <label className="image-picker-label" htmlFor={id}>{label}</label>
      {note ? <p className="field-note">{note}</p> : null}
      <input
        id={id}
        type="file"
        accept="image/*"
        onChange={handleFileChange}
        disabled={uploading}
      />
      {uploading ? <p className="field-note">Uploading image to S3...</p> : null}
      {value ? (
        <div className="image-preview-card">
          <img src={value} alt={`${label} preview`} className="image-preview" />
          <button type="button" className="ghost-btn" onClick={onRemove}>
            Remove image
          </button>
        </div>
      ) : null}
    </div>
  );
}

const blankSettings = {
  shopName: '',
  headerLine: '',
  logoUrl: '',
  loginKicker: '',
  homepageTitle: '',
  homepageSubtitle: '',
  heroPrimaryImageUrl: '',
  heroSecondaryImageUrl: '',
  trustBadgeOne: '',
  trustBadgeTwo: '',
  trustBadgeThree: '',
  trustBadgeFour: '',
  address: '',
  phoneNumber: '',
  gstNumber: '',
  footerNote: '',
  showAddress: true,
  showPhoneNumber: true,
  showGstNumber: false,
  taxEnabled: false,
  cgstPercent: 0,
  sgstPercent: 0,
  deliveryFeeEnabled: false,
  deliveryFee: 0,
  freeDeliveryThreshold: 0,
  facebookCatalogEnabled: false,
  metaPixelId: '',
  facebookFeedToken: '',
  facebookFeedLastGeneratedAt: ''
};

export default function ReceiptSettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const [form, setForm] = useState(blankSettings);
  const [activeTab, setActiveTab] = useState('brand');
  const [feedPreview, setFeedPreview] = useState(null);
  const [feedLoading, setFeedLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleUploadError = (message) => {
    setSuccess('');
    setError(message);
  };

  useEffect(() => {
    if (['brand', 'theme', 'social', 'facebook'].includes(requestedTab) && requestedTab !== activeTab) {
      setActiveTab(requestedTab);
    }
  }, [requestedTab, activeTab]);

  const selectSettingsTab = (tab) => {
    setActiveTab(tab);
    setSearchParams(tab === 'brand' ? {} : { tab });
  };

  useEffect(() => {
    retailService.getReceiptSettings()
      .then(setForm)
      .catch((requestError) => setError(getApiErrorMessage(requestError, 'Unable to load brand configuration.')));
    loadFeedPreview();
  }, []);

  const loadFeedPreview = async () => {
    setFeedLoading(true);
    try {
      setFeedPreview(await retailService.getFacebookFeedPreview());
    } catch {
      setFeedPreview(null);
    } finally {
      setFeedLoading(false);
    }
  };

  const facebookToken = form.facebookFeedToken || '';
  const xmlFeedUrl = `https://kpskrishnai.com/api/meta/catalog-feed.xml?token=${encodeURIComponent(facebookToken)}`;
  const csvFeedUrl = `https://kpskrishnai.com/api/meta/catalog-feed.csv?token=${encodeURIComponent(facebookToken)}`;

  const copyText = async (value, label) => {
    if (!value) {
      return;
    }
    await navigator.clipboard?.writeText(value);
    setSuccess(`${label} copied.`);
  };

  const handleGenerateFacebookToken = async () => {
    setError('');
    setSuccess('');
    try {
      const response = await retailService.generateFacebookFeedToken();
      setForm((current) => ({ ...current, facebookFeedToken: response.token || '' }));
      setSuccess('Facebook feed token generated.');
      await loadFeedPreview();
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to generate Facebook feed token.'));
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    try {
      const response = await retailService.updateReceiptSettings({
        ...form,
        cgstPercent: Number(form.cgstPercent || 0),
        sgstPercent: Number(form.sgstPercent || 0),
        deliveryFee: Number(form.deliveryFee || 0),
        freeDeliveryThreshold: Number(form.freeDeliveryThreshold || 0),
        facebookCatalogEnabled: Boolean(form.facebookCatalogEnabled),
        metaPixelId: form.metaPixelId || null,
        facebookFeedToken: form.facebookFeedToken || null
      });
      setForm(response);
      setSuccess('Brand configuration updated.');
      await loadFeedPreview();
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to update brand configuration.'));
    }
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Admin"
        title="Brand configuration"
        description="Control the store identity, receipt details, customer access branding, logo, hero text, trust badges, and images."
      />

      <Panel title="Business and receipt details" subtitle="These values are used across the website, customer access screens, printed receipts, and Facebook catalog feed.">
        <div className="admin-tab-row settings-tabs" role="tablist" aria-label="Brand configuration sections">
          {[
            ['brand', 'Brand Details'],
            ['theme', 'Theme / Logo'],
            ['social', 'Social Links'],
            ['facebook', 'Facebook Catalog']
          ].map(([value, label]) => (
            <button
              key={value}
              type="button"
              className={activeTab === value ? 'is-active' : ''}
              onClick={() => selectSettingsTab(value)}
            >
              {label}
            </button>
          ))}
        </div>
        <form className="form-grid" onSubmit={handleSubmit}>
          {activeTab === 'brand' ? (
            <>
              <div className="settings-two-column">
                <LabeledField label="Shop name" note="Shown as the main brand title on the customer access card and receipt header.">
                  <input value={form.shopName} onChange={(e) => setForm({ ...form, shopName: e.target.value })} required />
                </LabeledField>
                <LabeledField label="Header line" note="Small supporting line under the brand title.">
                  <input value={form.headerLine || ''} onChange={(e) => setForm({ ...form, headerLine: e.target.value })} />
                </LabeledField>
              </div>

              <LabeledField label="Address" note="Shown in the contact section and optionally on the receipt.">
                <textarea rows="3" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} required />
              </LabeledField>

              <div className="settings-two-column">
                <LabeledField label="Phone number" note="Used for customer contact and optionally on receipts.">
                  <input value={form.phoneNumber || ''} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} />
                </LabeledField>
                <LabeledField label="GST number" note="Shown only when the receipt GST toggle is enabled.">
                  <input value={form.gstNumber || ''} onChange={(e) => setForm({ ...form, gstNumber: e.target.value })} />
                </LabeledField>
              </div>

              <LabeledField label="Footer note" note="Printed at the end of the bill.">
                <textarea rows="3" value={form.footerNote || ''} onChange={(e) => setForm({ ...form, footerNote: e.target.value })} />
              </LabeledField>
            </>
          ) : null}

          {activeTab === 'theme' ? (
            <>
              <h4 className="subsection-title">Customer access page</h4>
              <div className="settings-two-column">
                <ImageUploadField
                  id="receipt-logo-image"
                  label="Logo image"
                  note="Upload the square logo shown at the top-left of the login card. The image is stored in S3 and served by CloudFront."
                  value={form.logoUrl || ''}
                  category="branding"
                  onChange={(value) => setForm({ ...form, logoUrl: value })}
                  onRemove={() => setForm({ ...form, logoUrl: '' })}
                  onError={handleUploadError}
                />
                <LabeledField label="Access label" note="Small uppercase line above the brand name on the customer access page.">
                  <input value={form.loginKicker || ''} onChange={(e) => setForm({ ...form, loginKicker: e.target.value })} />
                </LabeledField>
              </div>

              <LabeledField label="Hero title" note="Main bold heading inside the dark showcase panel.">
                <input value={form.homepageTitle || ''} onChange={(e) => setForm({ ...form, homepageTitle: e.target.value })} />
              </LabeledField>

              <LabeledField label="Hero subtitle" note="Support paragraph under the hero title.">
                <textarea rows="3" value={form.homepageSubtitle || ''} onChange={(e) => setForm({ ...form, homepageSubtitle: e.target.value })} />
              </LabeledField>

              <div className="settings-two-column">
                <ImageUploadField
                  id="receipt-hero-primary-image"
                  label="Hero image 1"
                  note="Upload the top image in the showcase panel. It is stored in S3 and served by CloudFront."
                  value={form.heroPrimaryImageUrl || ''}
                  category="branding"
                  onChange={(value) => setForm({ ...form, heroPrimaryImageUrl: value })}
                  onRemove={() => setForm({ ...form, heroPrimaryImageUrl: '' })}
                  onError={handleUploadError}
                />
                <ImageUploadField
                  id="receipt-hero-secondary-image"
                  label="Hero image 2"
                  note="Upload the bottom image in the showcase panel. It is stored in S3 and served by CloudFront."
                  value={form.heroSecondaryImageUrl || ''}
                  category="branding"
                  onChange={(value) => setForm({ ...form, heroSecondaryImageUrl: value })}
                  onRemove={() => setForm({ ...form, heroSecondaryImageUrl: '' })}
                  onError={handleUploadError}
                />
              </div>

              <div className="settings-two-column">
                <LabeledField label="Trust badge 1" note="Small rounded badge shown below the showcase text.">
                  <input value={form.trustBadgeOne || ''} onChange={(e) => setForm({ ...form, trustBadgeOne: e.target.value })} />
                </LabeledField>
                <LabeledField label="Trust badge 2" note="Second showcase badge.">
                  <input value={form.trustBadgeTwo || ''} onChange={(e) => setForm({ ...form, trustBadgeTwo: e.target.value })} />
                </LabeledField>
                <LabeledField label="Trust badge 3" note="Third showcase badge.">
                  <input value={form.trustBadgeThree || ''} onChange={(e) => setForm({ ...form, trustBadgeThree: e.target.value })} />
                </LabeledField>
                <LabeledField label="Trust badge 4" note="Fourth showcase badge.">
                  <input value={form.trustBadgeFour || ''} onChange={(e) => setForm({ ...form, trustBadgeFour: e.target.value })} />
                </LabeledField>
              </div>
            </>
          ) : null}

          <div className="settings-preview-card">
            <p className="settings-preview-kicker">{form.loginKicker || 'Access label preview'}</p>
            <div className="settings-preview-brand">
              {form.logoUrl ? <img className="settings-preview-logo" src={form.logoUrl} alt="Brand logo preview" /> : null}
              <div>
                <h4>{form.shopName || 'Your Brand Name'}</h4>
                <p>{form.headerLine || 'Your subtitle or category line appears here.'}</p>
              </div>
            </div>
            <div className="settings-preview-hero">
              <div className="settings-preview-copy">
                <strong>{form.homepageTitle || 'Hero title preview'}</strong>
                <span>{form.homepageSubtitle || 'Hero subtitle preview for the customer access page.'}</span>
                <div className="trust-chip-row">
                  {[form.trustBadgeOne, form.trustBadgeTwo, form.trustBadgeThree, form.trustBadgeFour].filter(Boolean).map((badge) => (
                    <span key={badge} className="trust-chip">{badge}</span>
                  ))}
                </div>
              </div>
              <div className="settings-preview-images">
                {form.heroPrimaryImageUrl ? <img src={form.heroPrimaryImageUrl} alt="Hero preview primary" /> : <div className="settings-preview-image-placeholder">Image 1</div>}
                {form.heroSecondaryImageUrl ? <img src={form.heroSecondaryImageUrl} alt="Hero preview secondary" /> : <div className="settings-preview-image-placeholder">Image 2</div>}
              </div>
            </div>
          </div>

          {activeTab === 'brand' ? (
            <>
              <label className="toggle-row">
                <input type="checkbox" checked={form.showAddress} onChange={(e) => setForm({ ...form, showAddress: e.target.checked })} />
                <span>Show address on receipt</span>
              </label>
              <label className="toggle-row">
                <input type="checkbox" checked={form.showPhoneNumber} onChange={(e) => setForm({ ...form, showPhoneNumber: e.target.checked })} />
                <span>Show phone number on receipt</span>
              </label>
              <label className="toggle-row">
                <input type="checkbox" checked={form.showGstNumber} onChange={(e) => setForm({ ...form, showGstNumber: e.target.checked })} />
                <span>Show GST number on receipt</span>
              </label>

              <h4 className="subsection-title">Billing configuration</h4>
              <div className="settings-billing-box">
                <label className="toggle-row">
                  <input type="checkbox" checked={Boolean(form.taxEnabled)} onChange={(e) => setForm({ ...form, taxEnabled: e.target.checked })} />
                  <span>Enable CGST/SGST tax calculation</span>
                </label>
                <div className="settings-two-column">
                  <LabeledField label="CGST %" note="Applied to website checkout and shop billing when tax is enabled.">
                    <input type="number" min="0" step="0.01" value={form.cgstPercent ?? 0} onChange={(e) => setForm({ ...form, cgstPercent: e.target.value })} />
                  </LabeledField>
                  <LabeledField label="SGST %" note="Applied to website checkout and shop billing when tax is enabled.">
                    <input type="number" min="0" step="0.01" value={form.sgstPercent ?? 0} onChange={(e) => setForm({ ...form, sgstPercent: e.target.value })} />
                  </LabeledField>
                </div>
                <label className="toggle-row">
                  <input type="checkbox" checked={Boolean(form.deliveryFeeEnabled)} onChange={(e) => setForm({ ...form, deliveryFeeEnabled: e.target.checked })} />
                  <span>Enable website delivery fee</span>
                </label>
                <div className="settings-two-column">
                  <LabeledField label="Website delivery fee" note="Applies only to ecommerce checkout, never shop billing.">
                    <input type="number" min="0" step="0.01" value={form.deliveryFee ?? 0} onChange={(e) => setForm({ ...form, deliveryFee: e.target.value })} />
                  </LabeledField>
                  <LabeledField label="Free delivery above" note="Website orders at or above this discounted subtotal get free delivery.">
                    <input type="number" min="0" step="0.01" value={form.freeDeliveryThreshold ?? 0} onChange={(e) => setForm({ ...form, freeDeliveryThreshold: e.target.value })} />
                  </LabeledField>
                </div>
              </div>
            </>
          ) : null}

          {activeTab === 'social' ? (
            <div className="settings-billing-box">
              <h4 className="subsection-title">Social and contact links</h4>
              <p className="field-note">Customer contact uses the configured phone number today. Meta Pixel tracking is configured in the Facebook Catalog tab.</p>
              <LabeledField label="Primary WhatsApp / phone number" note="Used by customer-facing contact actions.">
                <input value={form.phoneNumber || ''} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} />
              </LabeledField>
            </div>
          ) : null}

          {activeTab === 'facebook' ? (
            <div className="facebook-catalog-panel">
              <h4 className="subsection-title">Catalog Sync Settings</h4>
              <label className="toggle-row">
                <input type="checkbox" checked={Boolean(form.facebookCatalogEnabled)} onChange={(e) => setForm({ ...form, facebookCatalogEnabled: e.target.checked })} />
                <span>Enable Facebook Catalog Sync</span>
              </label>
              <div className="settings-two-column">
                <LabeledField label="Meta Pixel ID" note="Example: 123456789012345">
                  <input value={form.metaPixelId || ''} onChange={(e) => setForm({ ...form, metaPixelId: e.target.value })} placeholder="123456789012345" />
                </LabeledField>
                <LabeledField label="Feed Security Token" note="Required when configured. The public feed does not require admin login.">
                  <input value={facebookToken} onChange={(e) => setForm({ ...form, facebookFeedToken: e.target.value })} placeholder="kps_meta_2026_secure" />
                </LabeledField>
              </div>
              <div className="table-action-group">
                <button type="button" className="ghost-btn compact-btn" onClick={handleGenerateFacebookToken}>Generate Token</button>
              </div>

              <h4 className="subsection-title">Feed URLs</h4>
              <div className="feed-url-grid">
                <LabeledField label="XML Feed URL">
                  <input value={xmlFeedUrl} readOnly />
                </LabeledField>
                <div className="table-action-group">
                  <button type="button" className="ghost-btn compact-btn" onClick={() => copyText(xmlFeedUrl, 'XML feed URL')}>Copy XML URL</button>
                  <a className="ghost-btn compact-btn" href={xmlFeedUrl} target="_blank" rel="noreferrer">Open XML Feed</a>
                </div>
                <LabeledField label="CSV Feed URL">
                  <input value={csvFeedUrl} readOnly />
                </LabeledField>
                <div className="table-action-group">
                  <button type="button" className="ghost-btn compact-btn" onClick={() => copyText(csvFeedUrl, 'CSV feed URL')}>Copy CSV URL</button>
                  <a className="ghost-btn compact-btn" href={csvFeedUrl} target="_blank" rel="noreferrer">Open CSV Feed</a>
                </div>
              </div>

              <h4 className="subsection-title">Feed Status</h4>
              <div className="settings-status-grid">
                <div><strong>{feedPreview?.facebookCatalogEnabled ? 'Enabled' : 'Disabled'}</strong><span>Facebook Sync</span></div>
                <div><strong>{feedPreview?.pixelConfigured ? 'Configured' : 'Not configured'}</strong><span>Pixel</span></div>
                <div><strong>{feedPreview?.syncedCategories ?? 0}</strong><span>Synced Categories</span></div>
                <div><strong>{feedPreview?.syncedProducts ?? 0}</strong><span>Synced Products</span></div>
                <div><strong>{feedPreview?.lastFeedGeneratedAt ? new Date(feedPreview.lastFeedGeneratedAt).toLocaleString() : 'Never'}</strong><span>Last Feed Generated</span></div>
              </div>

              <h4 className="subsection-title">Preview Feed</h4>
              {feedLoading ? <p className="field-note">Loading feed preview...</p> : null}
              <div className="feed-preview-table">
                <table>
                  <thead>
                    <tr><th>Product ID</th><th>Product Name</th><th>Category</th><th>Price</th><th>Sale Price</th><th>Status</th><th>Issue</th></tr>
                  </thead>
                  <tbody>
                    {(feedPreview?.items || []).map((item) => (
                      <tr key={`${item.productId}-${item.productName}`}>
                        <td>{item.productId}</td>
                        <td>{item.productName}</td>
                        <td>{item.category || '—'}</td>
                        <td>{item.price || '—'}</td>
                        <td>{item.salePrice || '—'}</td>
                        <td>{item.status}</td>
                        <td>{item.issue || 'Ready'}</td>
                      </tr>
                    ))}
                    {!(feedPreview?.items || []).length ? (
                      <tr><td colSpan="7">No feed preview rows yet.</td></tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          {error ? <p className="error-text">{error}</p> : null}
          {success ? <p className="success-text">{success}</p> : null}
          <button className="primary-btn" type="submit">Save configuration</button>
        </form>
      </Panel>
    </div>
  );
}
