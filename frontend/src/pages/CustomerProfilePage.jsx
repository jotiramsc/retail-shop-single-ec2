import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { retailService } from '../services/retailService';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError,
  storeCustomerSession
} from '../utils/auth';

const initialAddress = {
  label: 'Home',
  recipientName: '',
  mobile: '',
  line1: '',
  line2: '',
  landmark: '',
  city: 'Pune',
  state: 'Maharashtra',
  pincode: '',
  latitude: '',
  longitude: ''
};

export default function CustomerProfilePage() {
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());
  const [profile, setProfile] = useState(null);
  const [addresses, setAddresses] = useState([]);
  const [profileName, setProfileName] = useState('');
  const [addressForm, setAddressForm] = useState(initialAddress);
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingAddress, setSavingAddress] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    const loadProfile = async () => {
      setLoading(true);
      setError('');
      try {
        const [profileResponse, addressResponse] = await Promise.all([
          retailService.getCustomerProfile(),
          retailService.getAddresses()
        ]);
        setProfile(profileResponse);
        setProfileName(profileResponse?.name || '');
        setAddresses(addressResponse || []);
      } catch (err) {
        if (isCustomerAuthError(err)) {
          clearCustomerSession();
          setCustomerSession(null);
          return;
        }
        setError(err.response?.data?.message || 'Unable to load customer profile right now.');
      } finally {
        setLoading(false);
      }
    };

    loadProfile();
  }, []);

  if (!customerSession?.token) {
    return <Navigate to="/customer-login?redirect=/account" replace />;
  }

  const saveProfile = async (event) => {
    event.preventDefault();
    setSavingProfile(true);
    setError('');
    setSuccess('');
    try {
      const updated = await retailService.updateCustomerProfile({ name: profileName });
      setProfile(updated);
      const nextSession = { ...customerSession, name: updated.name };
      storeCustomerSession(nextSession);
      setCustomerSession(nextSession);
      setSuccess('Profile updated.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        return;
      }
      setError(err.response?.data?.message || 'Unable to save profile.');
    } finally {
      setSavingProfile(false);
    }
  };

  const saveAddress = async (event) => {
    event.preventDefault();
    setSavingAddress(true);
    setError('');
    setSuccess('');
    try {
      const saved = await retailService.addAddress(addressForm);
      setAddresses((current) => [saved, ...current]);
      setAddressForm(initialAddress);
      setSuccess('Address saved.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        return;
      }
      setError(err.response?.data?.message || 'Unable to save address.');
    } finally {
      setSavingAddress(false);
    }
  };

  const deleteAddress = async (id) => {
    setError('');
    setSuccess('');
    try {
      await retailService.deleteAddress(id);
      setAddresses((current) => current.filter((address) => address.id !== id));
      setSuccess('Address removed.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        return;
      }
      setError(err.response?.data?.message || 'Unable to remove address.');
    }
  };

  const logout = () => {
    clearCustomerSession();
    setCustomerSession(null);
  };

  return (
    <main className="glow-site customer-flow-page">
      <section className="customer-flow-shell">
        <div className="customer-flow-head">
          <div>
            <p className="glow-kicker">Customer Account</p>
            <h1>Profile and addresses</h1>
            <p>Maintain your customer profile, saved Pune delivery addresses, and account access in one place.</p>
          </div>
          <div className="customer-profile-head-actions">
            <Link className="ghost-btn compact-btn" to="/orders">My orders</Link>
            <button type="button" className="ghost-btn compact-btn" onClick={logout}>Logout</button>
          </div>
        </div>

        {loading ? <p>Loading account…</p> : null}
        {error ? <p className="error-text">{error}</p> : null}
        {success ? <p className="success-text">{success}</p> : null}

        {!loading ? (
          <div className="customer-checkout-layout">
            <section className="customer-flow-panel">
              <div className="customer-section-title">
                <div>
                  <p className="glow-kicker">Profile</p>
                  <h2>Customer details</h2>
                </div>
              </div>

              <form className="customer-address-form" onSubmit={saveProfile}>
                <div className="customer-form-grid">
                  <input
                    value={profileName}
                    onChange={(event) => setProfileName(event.target.value)}
                    placeholder="Full name"
                  />
                  <input value={profile?.mobile || ''} readOnly placeholder="Mobile number" />
                </div>
                <div className="checkout-actions">
                  <button type="submit" className="primary-btn compact-btn" disabled={savingProfile}>
                    {savingProfile ? 'Saving...' : 'Save profile'}
                  </button>
                </div>
              </form>
            </section>

            <section className="customer-flow-panel">
              <div className="customer-section-title">
                <div>
                  <p className="glow-kicker">Saved addresses</p>
                  <h2>{addresses.length} address{addresses.length === 1 ? '' : 'es'} on file</h2>
                </div>
              </div>

              <div className="customer-address-list">
                {addresses.map((address) => (
                  <div key={address.id} className="customer-address-card customer-address-card-static">
                    <div>
                      <strong>{address.label || address.recipientName}</strong>
                      <p>{address.line1}, {address.city}, {address.state} {address.pincode}</p>
                      <span>{address.mobile}</span>
                    </div>
                    <button type="button" className="ghost-btn compact-btn" onClick={() => deleteAddress(address.id)}>
                      Remove
                    </button>
                  </div>
                ))}
              </div>

              <form className="customer-address-form" onSubmit={saveAddress}>
                <div className="customer-form-grid">
                  <input value={addressForm.label} onChange={(event) => setAddressForm((current) => ({ ...current, label: event.target.value }))} placeholder="Label" />
                  <input value={addressForm.recipientName} onChange={(event) => setAddressForm((current) => ({ ...current, recipientName: event.target.value }))} placeholder="Recipient name" />
                  <input value={addressForm.mobile} onChange={(event) => setAddressForm((current) => ({ ...current, mobile: event.target.value }))} placeholder="Mobile number" />
                  <input value={addressForm.line1} onChange={(event) => setAddressForm((current) => ({ ...current, line1: event.target.value }))} placeholder="Address line 1" />
                  <input value={addressForm.line2} onChange={(event) => setAddressForm((current) => ({ ...current, line2: event.target.value }))} placeholder="Address line 2" />
                  <input value={addressForm.landmark} onChange={(event) => setAddressForm((current) => ({ ...current, landmark: event.target.value }))} placeholder="Landmark" />
                  <input value={addressForm.city} onChange={(event) => setAddressForm((current) => ({ ...current, city: event.target.value }))} placeholder="City" />
                  <input value={addressForm.state} onChange={(event) => setAddressForm((current) => ({ ...current, state: event.target.value }))} placeholder="State" />
                  <input value={addressForm.pincode} onChange={(event) => setAddressForm((current) => ({ ...current, pincode: event.target.value }))} placeholder="Pincode" />
                  <input value={addressForm.latitude} onChange={(event) => setAddressForm((current) => ({ ...current, latitude: event.target.value }))} placeholder="Latitude" />
                  <input value={addressForm.longitude} onChange={(event) => setAddressForm((current) => ({ ...current, longitude: event.target.value }))} placeholder="Longitude" />
                </div>
                <div className="checkout-actions">
                  <button type="submit" className="primary-btn compact-btn" disabled={savingAddress}>
                    {savingAddress ? 'Saving...' : 'Save address'}
                  </button>
                </div>
              </form>
            </section>
          </div>
        ) : null}
      </section>
    </main>
  );
}
