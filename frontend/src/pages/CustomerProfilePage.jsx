import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { retailService } from '../services/retailService';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError,
  storeCustomerSession
} from '../utils/auth';
import { getApiErrorMessage } from '../utils/validation';

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

const normalizeMobileDigits = (value) => {
  const digits = String(value || '').replace(/\D/g, '');
  return digits.startsWith('91') && digits.length > 10 ? digits.slice(-10) : digits;
};

const isValidIndianMobile = (value) => normalizeMobileDigits(value).length === 10;

const validateAddressForm = (address) => {
  if (!address.recipientName.trim()) return 'Enter recipient name for delivery.';
  if (!isValidIndianMobile(address.mobile)) return 'Enter a valid 10-digit mobile number for delivery.';
  if (!address.line1.trim()) return 'Enter address line 1.';
  if (!address.city.trim()) return 'Enter delivery city.';
  if (!address.state.trim()) return 'Enter delivery state.';
  if (!address.pincode.trim()) return 'Enter delivery pincode.';
  return '';
};

export default function CustomerProfilePage() {
  const location = useLocation();
  const redirectTo = new URLSearchParams(location.search).get('redirect') || '';
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());
  const [profile, setProfile] = useState(null);
  const [addresses, setAddresses] = useState([]);
  const [profileName, setProfileName] = useState('');
  const [profileEmail, setProfileEmail] = useState('');
  const [profileMobile, setProfileMobile] = useState('');
  const [profileDob, setProfileDob] = useState('');
  const [profileGender, setProfileGender] = useState('');
  const [profileImageUrl, setProfileImageUrl] = useState('');
  const [alternateMobile, setAlternateMobile] = useState('');
  const [mobileOtp, setMobileOtp] = useState('');
  const [mobileOtpSent, setMobileOtpSent] = useState(false);
  const [addressForm, setAddressForm] = useState(initialAddress);
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);
  const [verifyingMobile, setVerifyingMobile] = useState(false);
  const [savingAddress, setSavingAddress] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const savedMobileDigits = normalizeMobileDigits(profile?.mobile);
  const currentMobileDigits = normalizeMobileDigits(profileMobile);
  const mobileChanged = Boolean(savedMobileDigits && currentMobileDigits && savedMobileDigits !== currentMobileDigits);
  const mobileVerificationRequired = Boolean(!profile?.mobileVerified || mobileChanged);

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
        setProfileEmail(profileResponse?.email || '');
        setProfileMobile(profileResponse?.mobile || '');
        setProfileDob(profileResponse?.dateOfBirth || '');
        setProfileGender(profileResponse?.gender || '');
        setProfileImageUrl(profileResponse?.profileImageUrl || '');
        setAlternateMobile(profileResponse?.alternateMobile || '');
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
    if (profileMobile && !isValidIndianMobile(profileMobile)) {
      setError('Enter a valid 10-digit mobile number before saving profile.');
      setSavingProfile(false);
      return;
    }
    try {
      const updated = await retailService.updateCustomerProfile({
        name: profileName,
        email: profileEmail,
        mobile: profileMobile,
        dateOfBirth: profileDob || null,
        gender: profileGender,
        profileImageUrl,
        alternateMobile
      });
      setProfile(updated);
      setProfileName(updated?.name || '');
      setProfileEmail(updated?.email || '');
      setProfileMobile(updated?.mobile || '');
      setProfileDob(updated?.dateOfBirth || '');
      setProfileGender(updated?.gender || '');
      setProfileImageUrl(updated?.profileImageUrl || '');
      setAlternateMobile(updated?.alternateMobile || '');
      setMobileOtpSent(false);
      setMobileOtp('');
      const nextSession = {
        ...customerSession,
        name: updated.name,
        email: updated.email,
        mobile: updated.mobile,
        mobileVerified: updated.mobileVerified,
        profileComplete: updated.profileComplete,
        missingFields: updated.missingFields || []
      };
      storeCustomerSession(nextSession);
      setCustomerSession(nextSession);
      setSuccess(updated.mobileVerified ? 'Profile saved.' : 'Profile saved. Please verify the new mobile number once.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        return;
      }
      setError(getApiErrorMessage(err, 'Unable to save profile. Please check the highlighted details and try again.'));
    } finally {
      setSavingProfile(false);
    }
  };

  const requestMobileOtp = async () => {
    setVerifyingMobile(true);
    setError('');
    setSuccess('');
    try {
      const response = await retailService.sendOtp({ mobile: profileMobile, purpose: 'PROFILE' });
      setMobileOtpSent(true);
      setMobileOtp('');
      setSuccess(response?.message || 'OTP sent for mobile verification.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        return;
      }
      setError(getApiErrorMessage(err, 'Unable to send mobile OTP. Please enter a valid mobile number.'));
    } finally {
      setVerifyingMobile(false);
    }
  };

  const verifyMobileOtp = async () => {
    setVerifyingMobile(true);
    setError('');
    setSuccess('');
    try {
      const sessionUpdate = await retailService.verifyProfileMobileOtp({ mobile: profileMobile, otp: mobileOtp, purpose: 'PROFILE' });
      const updatedProfile = await retailService.getCustomerProfile();
      setProfile(updatedProfile);
      setProfileName(updatedProfile?.name || '');
      setProfileEmail(updatedProfile?.email || '');
      setProfileMobile(updatedProfile?.mobile || '');
      setProfileDob(updatedProfile?.dateOfBirth || '');
      setProfileGender(updatedProfile?.gender || '');
      setProfileImageUrl(updatedProfile?.profileImageUrl || '');
      setAlternateMobile(updatedProfile?.alternateMobile || '');
      setMobileOtpSent(false);
      setMobileOtp('');
      const nextSession = {
        ...customerSession,
        ...sessionUpdate,
        name: updatedProfile.name,
        email: updatedProfile.email,
        mobile: updatedProfile.mobile,
        mobileVerified: updatedProfile.mobileVerified,
        profileComplete: updatedProfile.profileComplete,
        missingFields: updatedProfile.missingFields || []
      };
      storeCustomerSession(nextSession);
      setCustomerSession(nextSession);
      setSuccess('Mobile verified.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        return;
      }
      setError(getApiErrorMessage(err, 'Unable to verify mobile OTP. Please check the OTP and try again.'));
    } finally {
      setVerifyingMobile(false);
    }
  };

  const saveAddress = async (event) => {
    event.preventDefault();
    setSavingAddress(true);
    setError('');
    setSuccess('');
    const validationError = validateAddressForm(addressForm);
    if (validationError) {
      setError(validationError);
      setSavingAddress(false);
      return;
    }
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
      setError(getApiErrorMessage(err, 'Unable to save address. Please check the delivery details.'));
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
      setError(getApiErrorMessage(err, 'Unable to remove address. Please try again.'));
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
            {redirectTo ? (
              <Link className="glow-account-btn glow-account-btn-compact" to={redirectTo}>Continue checkout</Link>
            ) : null}
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

              {mobileVerificationRequired ? (
                <div className="customer-profile-alert">
                  <strong>{mobileChanged ? 'Mobile number changed' : 'Verify mobile number'}</strong>
                  <span>
                    {mobileChanged
                      ? 'Save profile first, then verify this new mobile number once.'
                      : 'Mobile OTP verification is required once for secure checkout.'}
                  </span>
                </div>
              ) : (
                <div className="customer-profile-alert is-complete">
                  <strong>Account ready</strong>
                  <span>Profile details are optional and can be edited anytime.</span>
                </div>
              )}

              <form className="customer-address-form" onSubmit={saveProfile}>
                <div className="customer-form-grid">
                  <input
                    value={profileName}
                    onChange={(event) => setProfileName(event.target.value)}
                    placeholder="Full name"
                  />
                  <input
                    value={profileEmail}
                    onChange={(event) => setProfileEmail(event.target.value)}
                    placeholder="Email address"
                    type="email"
                  />
                  <input
                    value={profileMobile}
                    onChange={(event) => setProfileMobile(event.target.value)}
                    placeholder="Mobile number"
                  />
                  <input
                    value={profileDob}
                    onChange={(event) => setProfileDob(event.target.value)}
                    placeholder="Date of birth"
                    type="date"
                  />
                  <select value={profileGender} onChange={(event) => setProfileGender(event.target.value)}>
                    <option value="">Gender</option>
                    <option value="Female">Female</option>
                    <option value="Male">Male</option>
                    <option value="Other">Other</option>
                    <option value="Prefer not to say">Prefer not to say</option>
                  </select>
                  <input
                    value={alternateMobile}
                    onChange={(event) => setAlternateMobile(event.target.value)}
                    placeholder="Alternate mobile number"
                  />
                  <input
                    value={profileImageUrl}
                    onChange={(event) => setProfileImageUrl(event.target.value)}
                    placeholder="Profile image URL"
                  />
                </div>
                <div className="checkout-actions">
                  <button type="submit" className="primary-btn compact-btn" disabled={savingProfile}>
                    {savingProfile ? 'Saving...' : 'Save profile'}
                  </button>
                </div>
              </form>

              <div className={`customer-mobile-verification ${mobileVerificationRequired ? '' : 'is-verified'}`}>
                <div>
                  <strong>{profile?.mobileVerified ? 'Mobile verified' : 'Mobile OTP verification'}</strong>
                  <span>{profile?.mobileVerified && !mobileChanged ? profile?.mobile : 'Required before checkout and payment.'}</span>
                </div>
                {mobileVerificationRequired && !mobileChanged ? (
                  <div className="customer-mobile-otp-actions">
                    <button type="button" className="ghost-btn compact-btn" onClick={requestMobileOtp} disabled={verifyingMobile || !profileMobile}>
                      {mobileOtpSent ? 'Resend OTP' : 'Send OTP'}
                    </button>
                    {mobileOtpSent ? (
                      <>
                        <input
                          value={mobileOtp}
                          onChange={(event) => setMobileOtp(event.target.value)}
                          placeholder="OTP"
                        />
                        <button type="button" className="primary-btn compact-btn" onClick={verifyMobileOtp} disabled={verifyingMobile || !mobileOtp}>
                          Verify mobile
                        </button>
                      </>
                    ) : null}
                  </div>
                ) : null}
              </div>
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
                <div className="customer-form-grid customer-address-form-grid">
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
