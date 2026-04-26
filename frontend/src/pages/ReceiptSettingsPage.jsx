import { useEffect, useState } from 'react';
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
  showGstNumber: false
};

export default function ReceiptSettingsPage() {
  const [form, setForm] = useState(blankSettings);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleUploadError = (message) => {
    setSuccess('');
    setError(message);
  };

  useEffect(() => {
    retailService.getReceiptSettings()
      .then(setForm)
      .catch((requestError) => setError(getApiErrorMessage(requestError, 'Unable to load receipt settings.')));
  }, []);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    try {
      const response = await retailService.updateReceiptSettings(form);
      setForm(response);
      setSuccess('Receipt and customer access branding updated.');
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to update receipt settings.'));
    }
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Admin"
        title="Receipt and brand settings"
        description="Control receipt details plus the customer-access page branding, logo, hero text, trust badges, and images."
      />

      <Panel title="Receipt configuration" subtitle="These values are used by the print receipt action in billing.">
        <form className="form-grid" onSubmit={handleSubmit}>
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

          {error ? <p className="error-text">{error}</p> : null}
          {success ? <p className="success-text">{success}</p> : null}
          <button className="primary-btn" type="submit">Save Settings</button>
        </form>
      </Panel>
    </div>
  );
}
