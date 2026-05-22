import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { retailService } from '../services/retailService';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError,
  storeCustomerSession
} from '../utils/auth';
import { currency, formatDate } from '../utils/format';
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
  const [orders, setOrders] = useState([]);
  const [wishlistItems, setWishlistItems] = useState([]);
  const [profileName, setProfileName] = useState('');
  const [profileEmail, setProfileEmail] = useState('');
  const [profileMobile, setProfileMobile] = useState('');
  const [profileDob, setProfileDob] = useState('');
  const [profileGender, setProfileGender] = useState('');
  const [profileImageUrl, setProfileImageUrl] = useState('');
  const [alternateMobile, setAlternateMobile] = useState('');
  const [anniversaryDate, setAnniversaryDate] = useState('');
  const [preferredLanguage, setPreferredLanguage] = useState('Marathi');
  const [instagramId, setInstagramId] = useState('');
  const [customerNotes, setCustomerNotes] = useState('');
  const [favoriteCategory, setFavoriteCategory] = useState('');
  const [preferredTone, setPreferredTone] = useState('Gold');
  const [stylePreference, setStylePreference] = useState('Traditional');
  const [bridalInterest, setBridalInterest] = useState(false);
  const [festivalPreference, setFestivalPreference] = useState('Ganesh Chaturthi, Diwali, wedding gifting');
  const [budgetRange, setBudgetRange] = useState('1000-3000');
  const [customerSearch, setCustomerSearch] = useState('');
  const [activePanel, setActivePanel] = useState('addresses');
  const [mobileSelectorOpen, setMobileSelectorOpen] = useState(false);
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
        const [profileResponse, addressResponse, orderResponse, wishlistResponse] = await Promise.all([
          retailService.getCustomerProfile(),
          retailService.getAddresses(),
          retailService.getOrders().catch(() => []),
          retailService.getWishlist().catch(() => [])
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
        setOrders(orderResponse || []);
        setWishlistItems(wishlistResponse || []);
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

  const latestOrder = orders[0];
  const lastPurchasedProduct = latestOrder?.items?.[0]?.productName || wishlistItems[0]?.name || 'Not captured yet';
  const totalSpent = orders.reduce((sum, order) => sum + Number(order.finalAmount || 0), 0);
  const averageOrderValue = orders.length ? totalSpent / orders.length : 0;
  const membershipLevel = totalSpent > 25000 ? 'Maharani Gold' : totalSpent > 10000 ? 'Pearl Insider' : 'New Collector';
  const rewardPoints = Math.round(totalSpent / 100);
  const activeCity = addresses[0]?.city || 'Pune';
  const activeCustomerType = totalSpent > 25000 ? 'VIP' : orders.length ? 'Regular' : 'New';
  const currentCustomer = {
    id: profile?.customerId || 'current',
    name: profileName || customerSession?.name || 'Krishnai Customer',
    mobile: profileMobile || customerSession?.mobile || 'Mobile pending',
    city: activeCity,
    lastPurchase: latestOrder?.createdAt || latestOrder?.orderDate,
    status: latestOrder?.status || 'Browsing',
    type: activeCustomerType
  };
  const customerCards = [currentCustomer];
  const filteredCustomerCards = customerCards.filter((customer) => [customer.name, customer.mobile, customer.city, customer.type]
    .some((value) => String(value || '').toLowerCase().includes(customerSearch.toLowerCase())));
  const profileCompletion = [
    profileName,
    profileMobile,
    profileEmail,
    profileDob,
    profileGender,
    alternateMobile,
    addresses.length ? 'address' : '',
    favoriteCategory
  ].filter(Boolean).length;
  const completionPercent = Math.round((profileCompletion / 8) * 100);
  const panelTabs = [
    { key: 'addresses', label: 'Saved Addresses' },
    { key: 'orders', label: 'Orders' },
    { key: 'wishlist', label: 'Wishlist' },
    { key: 'support', label: 'Support' },
    { key: 'measurements', label: 'Measurements' },
    { key: 'rewards', label: 'Rewards' }
  ];
  const preferenceChips = [favoriteCategory || 'Pearl jewellery', preferredTone, stylePreference, bridalInterest ? 'Bridal shopping' : 'Occasion gifting', budgetRange].filter(Boolean);

  const customerSelector = (
    <aside className={`account-customer-panel ${mobileSelectorOpen ? 'is-open' : ''}`} aria-label="Customer selector">
      <div className="account-selector-head">
        <div>
          <span>Customers</span>
          <strong>Quick switch</strong>
        </div>
        <button type="button" onClick={() => setMobileSelectorOpen(false)} aria-label="Close customer selector">x</button>
      </div>
      <label className="account-selector-search">
        <span>Search</span>
        <input value={customerSearch} onChange={(event) => setCustomerSearch(event.target.value)} placeholder="Name or mobile" />
      </label>
      <button type="button" className="account-add-customer">Add Customer</button>
      <div className="account-customer-list">
        {filteredCustomerCards.map((customer) => (
          <button key={customer.id} type="button" className="account-mini-customer-card is-active">
            <span className="account-mini-avatar">{String(customer.name || '?').slice(0, 1).toUpperCase()}<i /></span>
            <span>
              <strong>{customer.name}</strong>
              <small>{customer.mobile}</small>
              <small>{customer.city} · {customer.lastPurchase ? formatDate(customer.lastPurchase) : 'No purchase yet'}</small>
            </span>
            <em>{customer.type}</em>
          </button>
        ))}
      </div>
    </aside>
  );

  return (
    <main className="glow-site customer-flow-page account-luxury-page">
      <section className="account-luxury-shell">
        <div className="account-luxury-head">
          <button type="button" className="account-mobile-selector-btn" onClick={() => setMobileSelectorOpen(true)}>Customers</button>
          <div>
            <p className="glow-kicker">KPS Krishnai Pearl Shopee</p>
            <h1>Customer Account</h1>
            <p>Manage jewellery preferences, saved delivery details, rewards, wishlist, support, and orders from one elegant dashboard.</p>
          </div>
          <div className="customer-profile-head-actions">
            {redirectTo ? <Link className="glow-account-btn glow-account-btn-compact" to={redirectTo}>Continue checkout</Link> : null}
            <Link className="ghost-btn compact-btn" to="/products">Shop collection</Link>
            <button type="button" className="ghost-btn compact-btn" onClick={logout}>Logout</button>
          </div>
        </div>

        {loading ? (
          <div className="account-skeleton-grid">
            <span /><span /><span />
          </div>
        ) : null}
        {error ? <p className="error-text">{error}</p> : null}
        {success ? <p className="success-text account-floating-save">{success}</p> : null}

        {!loading ? (
          <div className="account-dashboard-grid">
            {customerSelector}

            <section className="account-profile-panel">
              <div className="account-profile-hero">
                <div className="account-profile-photo">
                  {profileImageUrl ? <img src={profileImageUrl} alt={profileName || 'Customer'} /> : <span>{String(profileName || 'K').slice(0, 1).toUpperCase()}</span>}
                </div>
                <div>
                  <p className="glow-kicker">{membershipLevel}</p>
                  <h2>{profileName || 'Complete your profile'}</h2>
                  <div className="account-badge-row">
                    <span>{profile?.mobileVerified ? 'WhatsApp verified' : 'Verification pending'}</span>
                    <span>{rewardPoints} reward points</span>
                    <span>Member since {profile?.createdAt ? formatDate(profile.createdAt) : 'today'}</span>
                  </div>
                </div>
                <div className="account-completion-ring">
                  <strong>{completionPercent}%</strong>
                  <span>Profile ready</span>
                </div>
              </div>

              {mobileVerificationRequired ? (
                <div className="customer-profile-alert">
                  <strong>{mobileChanged ? 'Mobile number changed' : 'Verify mobile number'}</strong>
                  <span>{mobileChanged ? 'Save profile first, then verify this new mobile number once.' : 'Mobile OTP verification is required once for secure checkout.'}</span>
                </div>
              ) : (
                <div className="customer-profile-alert is-complete">
                  <strong>Account ready</strong>
                  <span>Your KPS profile is ready for faster checkout and curated jewellery recommendations.</span>
                </div>
              )}

              <form className="account-form-card" onSubmit={saveProfile}>
                <div className="account-section-heading">
                  <div>
                    <p className="glow-kicker">Personal Details</p>
                    <h3>Profile information</h3>
                  </div>
                  <span>{savingProfile ? 'Saving...' : 'Auto-save ready'}</span>
                </div>
                <div className="account-form-grid">
                  <label>Full name<input value={profileName} onChange={(event) => setProfileName(event.target.value)} placeholder="Full name" /></label>
                  <label>Email<input value={profileEmail} onChange={(event) => setProfileEmail(event.target.value)} placeholder="Email address" type="email" /></label>
                  <label>Mobile<input value={profileMobile} onChange={(event) => setProfileMobile(event.target.value)} placeholder="Mobile number" /></label>
                  <label>Alternate mobile<input value={alternateMobile} onChange={(event) => setAlternateMobile(event.target.value)} placeholder="Alternate mobile" /></label>
                  <label>Date of birth<input value={profileDob} onChange={(event) => setProfileDob(event.target.value)} type="date" /></label>
                  <label>Anniversary date<input value={anniversaryDate} onChange={(event) => setAnniversaryDate(event.target.value)} type="date" /></label>
                  <label>Gender<select value={profileGender} onChange={(event) => setProfileGender(event.target.value)}><option value="">Select gender</option><option value="Female">Female</option><option value="Male">Male</option><option value="Other">Other</option><option value="Prefer not to say">Prefer not to say</option></select></label>
                  <label>Preferred language<select value={preferredLanguage} onChange={(event) => setPreferredLanguage(event.target.value)}><option>Marathi</option><option>Hindi</option><option>English</option></select></label>
                  <label>Instagram ID<input value={instagramId} onChange={(event) => setInstagramId(event.target.value)} placeholder="@username" /></label>
                  <label>Profile image URL<input value={profileImageUrl} onChange={(event) => setProfileImageUrl(event.target.value)} placeholder="Profile image URL" /></label>
                  <label className="is-wide">Notes/preferences<textarea value={customerNotes} onChange={(event) => setCustomerNotes(event.target.value)} placeholder="Sizing, gifting notes, preferred designs, or delivery context" /></label>
                </div>
                <div className="account-sticky-actions">
                  <button type="submit" className="primary-btn compact-btn" disabled={savingProfile}>{savingProfile ? 'Saving...' : 'Save profile'}</button>
                </div>
              </form>

              <div className="account-form-card">
                <div className="account-section-heading">
                  <div>
                    <p className="glow-kicker">Jewellery Preferences</p>
                    <h3>Shopping intelligence</h3>
                  </div>
                </div>
                <div className="account-form-grid">
                  <label>Favorite category<input value={favoriteCategory} onChange={(event) => setFavoriteCategory(event.target.value)} placeholder="Pearl sets, bangles, earrings" /></label>
                  <label>Preferred color tone<select value={preferredTone} onChange={(event) => setPreferredTone(event.target.value)}><option>Gold</option><option>Rose gold</option><option>Silver</option><option>Pearl white</option></select></label>
                  <label>Style<select value={stylePreference} onChange={(event) => setStylePreference(event.target.value)}><option>Traditional</option><option>Modern</option><option>Bridal</option><option>Daily wear</option></select></label>
                  <label>Budget range<input value={budgetRange} onChange={(event) => setBudgetRange(event.target.value)} placeholder="1000-3000" /></label>
                  <label className="account-checkbox-row"><input type="checkbox" checked={bridalInterest} onChange={(event) => setBridalInterest(event.target.checked)} /> Bridal shopping interest</label>
                  <label className="is-wide">Festival preferences<textarea value={festivalPreference} onChange={(event) => setFestivalPreference(event.target.value)} /></label>
                </div>
                <div className="account-chip-row">{preferenceChips.map((chip) => <span key={chip}>{chip}</span>)}</div>
              </div>

              <div className="account-insight-grid">
                <div><span>Average order value</span><strong>{currency(averageOrderValue)}</strong></div>
                <div><span>Last purchased</span><strong>{lastPurchasedProduct}</strong></div>
                <div><span>Frequent category</span><strong>{favoriteCategory || wishlistItems[0]?.category || 'Pearl jewellery'}</strong></div>
              </div>

              <div className={`customer-mobile-verification ${mobileVerificationRequired ? '' : 'is-verified'}`}>
                <div>
                  <strong>{profile?.mobileVerified ? 'Mobile verified' : 'Mobile OTP verification'}</strong>
                  <span>{profile?.mobileVerified && !mobileChanged ? profile?.mobile : 'Required before checkout and payment.'}</span>
                </div>
                {mobileVerificationRequired && !mobileChanged ? (
                  <div className="customer-mobile-otp-actions">
                    <button type="button" className="ghost-btn compact-btn" onClick={requestMobileOtp} disabled={verifyingMobile || !profileMobile}>{mobileOtpSent ? 'Resend OTP' : 'Send OTP'}</button>
                    {mobileOtpSent ? (
                      <>
                        <input value={mobileOtp} onChange={(event) => setMobileOtp(event.target.value)} placeholder="OTP" />
                        <button type="button" className="primary-btn compact-btn" onClick={verifyMobileOtp} disabled={verifyingMobile || !mobileOtp}>Verify mobile</button>
                      </>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </section>

            <aside className="account-module-panel">
              <div className="account-module-tabs">
                {panelTabs.map((tab) => <button key={tab.key} type="button" className={activePanel === tab.key ? 'is-active' : ''} onClick={() => setActivePanel(tab.key)}>{tab.label}</button>)}
              </div>

              {activePanel === 'addresses' ? (
                <div className="account-module-stack">
                  <div className="account-section-heading"><div><p className="glow-kicker">Saved Addresses</p><h3>{addresses.length} address{addresses.length === 1 ? '' : 'es'}</h3></div></div>
                  {addresses.map((address, index) => (
                    <article key={address.id} className="account-address-card">
                      <div><strong>{address.label || address.recipientName}</strong>{index === 0 ? <span>Default address</span> : null}</div>
                      <p>{address.recipientName} · {address.mobile}</p>
                      <p>{address.line1}{address.line2 ? `, ${address.line2}` : ''}, {address.landmark ? `${address.landmark}, ` : ''}{address.city}, {address.state} {address.pincode}</p>
                      <small>Delivery instructions can be added before checkout.</small>
                      <div><button type="button">Edit</button><button type="button" onClick={() => deleteAddress(address.id)}>Delete</button><button type="button">Set default</button><button type="button">Deliver here</button></div>
                    </article>
                  ))}
                  {!addresses.length ? <div className="account-empty-module">No saved addresses yet.</div> : null}
                  <form className="account-address-form" onSubmit={saveAddress}>
                    <input value={addressForm.label} onChange={(event) => setAddressForm((current) => ({ ...current, label: event.target.value }))} placeholder="Address label" />
                    <input value={addressForm.recipientName} onChange={(event) => setAddressForm((current) => ({ ...current, recipientName: event.target.value }))} placeholder="Recipient name" />
                    <input value={addressForm.mobile} onChange={(event) => setAddressForm((current) => ({ ...current, mobile: event.target.value }))} placeholder="Mobile / WhatsApp number" />
                    <input value={addressForm.line1} onChange={(event) => setAddressForm((current) => ({ ...current, line1: event.target.value }))} placeholder="House / flat" />
                    <input value={addressForm.line2} onChange={(event) => setAddressForm((current) => ({ ...current, line2: event.target.value }))} placeholder="Area / street" />
                    <input value={addressForm.landmark} onChange={(event) => setAddressForm((current) => ({ ...current, landmark: event.target.value }))} placeholder="Landmark" />
                    <input value={addressForm.city} onChange={(event) => setAddressForm((current) => ({ ...current, city: event.target.value }))} placeholder="City" />
                    <input value={addressForm.state} onChange={(event) => setAddressForm((current) => ({ ...current, state: event.target.value }))} placeholder="State" />
                    <input value={addressForm.pincode} onChange={(event) => setAddressForm((current) => ({ ...current, pincode: event.target.value }))} placeholder="Pincode" />
                    <button type="submit" className="primary-btn compact-btn" disabled={savingAddress}>{savingAddress ? 'Saving...' : 'Save address'}</button>
                  </form>
                </div>
              ) : null}

              {activePanel === 'orders' ? <div className="account-module-stack">{orders.slice(0, 4).map((order) => <article key={order.id} className="account-order-card"><strong>{order.orderNumber}</strong><span>{order.status} · {order.paymentStatus}</span><b>{currency(order.finalAmount)}</b></article>)}{!orders.length ? <div className="account-empty-module">No orders yet.</div> : null}</div> : null}
              {activePanel === 'wishlist' ? <div className="account-module-stack">{wishlistItems.slice(0, 5).map((item) => <article key={item.productId} className="account-order-card"><strong>{item.name}</strong><span>{item.category || 'Wishlist item'}</span><b>{currency(item.offerPrice || item.websitePrice || item.sellingPrice)}</b></article>)}{!wishlistItems.length ? <div className="account-empty-module">Wishlist items will appear here.</div> : null}</div> : null}
              {activePanel === 'support' ? <div className="account-support-card"><strong>Need assistance?</strong><p>Raise a support request, continue WhatsApp support, ask about returns, or track an order with KPS staff.</p><div><Link to="/orders">Track order</Link><a href="https://wa.me/918830461523">WhatsApp support</a><button type="button">Raise ticket</button></div></div> : null}
              {activePanel === 'measurements' ? <div className="account-support-card"><strong>Measurements</strong><p>Save ring size, bangle size, necklace length, and styling notes for future jewellery recommendations.</p><div><button type="button">Add ring size</button><button type="button">Add bangle size</button></div></div> : null}
              {activePanel === 'rewards' ? <div className="account-support-card"><strong>{membershipLevel}</strong><p>{rewardPoints} reward points, referral rewards, coupon history, and festive member offers will be collected here.</p><div><button type="button">View coupons</button><button type="button">Refer friend</button></div></div> : null}
            </aside>
          </div>
        ) : null}
      </section>
    </main>
  );
}
