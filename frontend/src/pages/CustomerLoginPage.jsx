import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { retailService } from '../services/retailService';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  storeCustomerSession
} from '../utils/auth';
import { clearGuestCart, getGuestCartItems } from '../utils/cart';

const OTP_CHALLENGE_KEY = 'retail_shop_customer_otp_challenge';

const readStoredOtpChallenge = () => {
  if (typeof window === 'undefined') {
    return null;
  }

  try {
    const rawValue = window.sessionStorage.getItem(OTP_CHALLENGE_KEY);
    if (!rawValue) {
      return null;
    }

    const parsed = JSON.parse(rawValue);
    if (!parsed?.mobile || !parsed?.expiresAt || Date.now() >= Number(parsed.expiresAt)) {
      window.sessionStorage.removeItem(OTP_CHALLENGE_KEY);
      return null;
    }

    return parsed;
  } catch {
    window.sessionStorage.removeItem(OTP_CHALLENGE_KEY);
    return null;
  }
};

const storeOtpChallenge = (challenge) => {
  if (typeof window === 'undefined') {
    return;
  }
  window.sessionStorage.setItem(OTP_CHALLENGE_KEY, JSON.stringify(challenge));
};

const clearOtpChallenge = () => {
  if (typeof window === 'undefined') {
    return;
  }
  window.sessionStorage.removeItem(OTP_CHALLENGE_KEY);
};

const formatCountdown = (seconds) => {
  const safeSeconds = Math.max(0, Number(seconds || 0));
  const minutes = Math.floor(safeSeconds / 60);
  const remainingSeconds = safeSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`;
};

export default function CustomerLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = new URLSearchParams(location.search).get('redirect') || '/cart';
  const googleClientId = window.__APP_CONFIG__?.GOOGLE_CLIENT_ID || import.meta.env.VITE_GOOGLE_CLIENT_ID || '';
  const googleButtonRef = useRef(null);
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());
  const [otpChallenge, setOtpChallenge] = useState(() => readStoredOtpChallenge());
  const [pendingGoogle, setPendingGoogle] = useState(null);
  const [mobile, setMobile] = useState(() => readStoredOtpChallenge()?.mobile || '');
  const [otp, setOtp] = useState('');
  const [message, setMessage] = useState(() => readStoredOtpChallenge()?.message || '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [authMode, setAuthMode] = useState('login');
  const [authMethod, setAuthMethod] = useState('mobile');
  const [clock, setClock] = useState(Date.now());

  useEffect(() => {
    if (!otpChallenge) {
      return undefined;
    }

    const timer = window.setInterval(() => setClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [otpChallenge]);

  useEffect(() => {
    if (!otpChallenge || typeof window === 'undefined' || !('OTPCredential' in window) || !navigator.credentials) {
      return undefined;
    }

    const controller = new AbortController();
    navigator.credentials.get({
      otp: { transport: ['sms'] },
      signal: controller.signal
    }).then((credential) => {
      if (credential?.code) {
        setOtp(credential.code);
      }
    }).catch(() => {});

    return () => controller.abort();
  }, [otpChallenge]);

  const resendRemaining = useMemo(() => {
    if (!otpChallenge?.resendAvailableAt) {
      return 0;
    }
    return Math.max(0, Math.ceil((Number(otpChallenge.resendAvailableAt) - clock) / 1000));
  }, [clock, otpChallenge]);

  const expiryRemaining = useMemo(() => {
    if (!otpChallenge?.expiresAt) {
      return 0;
    }
    return Math.max(0, Math.ceil((Number(otpChallenge.expiresAt) - clock) / 1000));
  }, [clock, otpChallenge]);

  const otpSent = Boolean(otpChallenge);
  const activePurpose = otpChallenge?.purpose || (authMode === 'signup' ? 'SIGNUP' : 'LOGIN');

  const finishCustomerLogin = useCallback(async (session) => {
    if (!session?.token) {
      throw new Error('Login session could not be created. Please try again.');
    }
    storeCustomerSession(session);
    clearOtpChallenge();
    setCustomerSession(session);
    const guestItems = getGuestCartItems();
    if (guestItems.length) {
      await retailService.mergeCart({ items: guestItems });
      clearGuestCart();
    }
    navigate(redirectTo);
  }, [navigate, redirectTo]);

  const updateOtpChallenge = (response, nextMobile, metadata = {}) => {
    const resendCooldownSeconds = Number(response?.resendCooldownSeconds || 30);
    const expiresInSeconds = Number(response?.expiresInSeconds || 300);
    const nextMessage = response?.message || 'OTP sent on WhatsApp.';
    const nextChallenge = {
      mobile: nextMobile,
      maskedMobile: response?.maskedMobile || nextMobile,
      resendAvailableAt: Date.now() + (resendCooldownSeconds * 1000),
      expiresAt: Date.now() + (expiresInSeconds * 1000),
      message: nextMessage,
      purpose: metadata.purpose || activePurpose,
      customerId: metadata.customerId || ''
    };

    setOtpChallenge(nextChallenge);
    setMessage(nextChallenge.message);
    setOtp('');
    storeOtpChallenge(nextChallenge);
  };

  const resetOtpFlow = () => {
    clearOtpChallenge();
    setOtpChallenge(null);
    setOtp('');
    setMessage('');
    setError('');
  };

  const logoutAndUseAnotherNumber = () => {
    clearCustomerSession();
    resetOtpFlow();
    setCustomerSession(null);
    setMobile('');
    setPendingGoogle(null);
  };

  const sendOtpFor = async ({ nextMobile, purpose, customerId = '' }) => {
    const response = await retailService.sendOtp({ mobile: nextMobile, purpose, customerId });
    if (response?.otpRequired === false) {
      setMessage(response.message || 'Choose the correct account flow to continue.');
      setError('');
      if (response.nextStep === 'SIGNUP_REQUIRED') {
        setAuthMode('signup');
      }
      if (response.nextStep === 'LOGIN_REQUIRED') {
        setAuthMode('login');
      }
      return false;
    }
    updateOtpChallenge(response, nextMobile, { purpose, customerId });
    return true;
  };

  const requestMobileOtp = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    setMessage('');
    try {
      const nextMobile = mobile.trim();
      const purpose = authMode === 'signup' ? 'SIGNUP' : 'LOGIN';
      await sendOtpFor({ nextMobile, purpose });
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to send OTP');
    } finally {
      setSubmitting(false);
    }
  };

  const requestGoogleOtp = async (event) => {
    event?.preventDefault?.();
    setSubmitting(true);
    setError('');
    setMessage('');
    try {
      if (!pendingGoogle?.customerId) {
        throw new Error('Google sign-in session expired. Please sign in again.');
      }
      await sendOtpFor({
        nextMobile: mobile.trim(),
        purpose: 'GOOGLE',
        customerId: pendingGoogle.customerId
      });
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Unable to send OTP');
    } finally {
      setSubmitting(false);
    }
  };

  const resendOtp = async () => {
    if (!otpSent || resendRemaining > 0) {
      return;
    }

    setSubmitting(true);
    setError('');
    setMessage('');
    try {
      await sendOtpFor({
        nextMobile: otpChallenge.mobile,
        purpose: otpChallenge.purpose || activePurpose,
        customerId: otpChallenge.customerId || ''
      });
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to send OTP');
    } finally {
      setSubmitting(false);
    }
  };

  const verifyOtp = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const payload = {
        mobile: otpChallenge?.mobile || mobile,
        otp,
        purpose: otpChallenge?.purpose || activePurpose,
        customerId: otpChallenge?.customerId || ''
      };
      const session = payload.purpose === 'GOOGLE'
        ? await retailService.verifyGoogleMobileOtp(payload)
        : await retailService.verifyOtp(payload);
      await finishCustomerLogin(session);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Unable to verify OTP');
    } finally {
      setSubmitting(false);
    }
  };

  const handleGoogleCredential = useCallback(async (response) => {
    if (!response?.credential) {
      return;
    }
    setSubmitting(true);
    setError('');
    setMessage('');
    try {
      const session = await retailService.loginWithGoogle({ credential: response.credential });
      if (session?.requiresMobileOtp || !session?.token) {
        setPendingGoogle(session);
        setAuthMode('login');
        setAuthMethod('google');
        setMobile(session?.mobile || '');
        resetOtpFlow();
        setMessage(session?.mobile
          ? 'Google sign-in completed. Verify OTP on your registered mobile to continue.'
          : 'Google sign-in completed. Enter your mobile number and verify OTP to continue.');
        return;
      }
      await finishCustomerLogin(session);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to sign in with Google');
    } finally {
      setSubmitting(false);
    }
  }, [finishCustomerLogin]);

  useEffect(() => {
    if (authMethod !== 'google' || pendingGoogle || !googleClientId || customerSession?.token || !googleButtonRef.current) {
      return undefined;
    }

    let cancelled = false;
    const initializeGoogleButton = () => {
      if (cancelled || !window.google?.accounts?.id || !googleButtonRef.current) {
        return;
      }
      googleButtonRef.current.innerHTML = '';
      window.google.accounts.id.initialize({
        client_id: googleClientId,
        callback: handleGoogleCredential
      });
      window.google.accounts.id.renderButton(googleButtonRef.current, {
        theme: 'outline',
        size: 'large',
        width: 340,
        text: 'continue_with',
        shape: 'pill'
      });
    };

    const existingScript = document.querySelector('script[data-google-identity="true"]');
    if (existingScript) {
      initializeGoogleButton();
      existingScript.addEventListener('load', initializeGoogleButton, { once: true });
      return () => {
        cancelled = true;
        existingScript.removeEventListener('load', initializeGoogleButton);
      };
    }

    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.dataset.googleIdentity = 'true';
    script.onload = initializeGoogleButton;
    script.onerror = () => {
      if (!cancelled) {
        setError('Unable to load Google Sign-In right now.');
      }
    };
    document.head.appendChild(script);

    return () => {
      cancelled = true;
    };
  }, [authMethod, customerSession?.token, googleClientId, handleGoogleCredential, pendingGoogle]);

  const selectAuthMode = (nextMode) => {
    resetOtpFlow();
    setPendingGoogle(null);
    setAuthMode(nextMode);
    setAuthMethod('mobile');
    setMessage('');
  };

  const selectAuthMethod = (nextMethod) => {
    resetOtpFlow();
    setPendingGoogle(null);
    setAuthMethod(nextMethod);
    setMessage('');
  };

  if (customerSession?.token) {
    return (
      <main className="glow-site customer-flow-page customer-auth-modern-page">
        <section className="customer-card customer-auth-card customer-auth-signed-in">
          <p className="glow-kicker">Customer Access</p>
          <h1>You are already signed in</h1>
          <div className="customer-session-card">
            <strong>{customerSession.name || 'Customer'}</strong>
            <span>{customerSession.mobile || customerSession.email || 'Account ready'}</span>
          </div>
          <div className="customer-form-actions">
            <Link className="glow-account-btn" to={redirectTo}>Continue</Link>
            <Link className="ghost-btn compact-btn" to="/account">My account</Link>
            <button type="button" className="ghost-btn compact-btn" onClick={logoutAndUseAnotherNumber}>
              Use a different number
            </button>
          </div>
        </section>
      </main>
    );
  }

  const title = otpSent
    ? 'Enter verification code'
    : (authMode === 'signup' ? 'Create your account' : 'Login to your account');
  const mobileButtonLabel = authMode === 'signup' ? 'Send signup OTP' : 'Send login OTP';
  const primaryActionLabel = otpSent
    ? (activePurpose === 'SIGNUP' ? 'Create account' : 'Verify and continue')
    : (authMethod === 'google' || pendingGoogle
      ? 'Continue with Google'
      : mobileButtonLabel);

  return (
    <main className="glow-site customer-flow-page customer-auth-modern-page">
      <section className="customer-auth-phone-shell">
        <div className="customer-auth-forest">
          <Link to="/" className="customer-auth-back">Back to store</Link>
          <p className="glow-kicker">Customer Access</p>
          <h1>{authMode === 'signup' ? 'Create account' : 'Welcome back'}</h1>
          <span>
            {authMode === 'signup'
              ? 'Register with mobile OTP and start shopping immediately.'
              : 'Login with Google or WhatsApp OTP. Mobile verification keeps checkout secure.'}
          </span>
        </div>

        <div className="customer-auth-sheet">
          <div className="customer-auth-tabs customer-login-mode" role="tablist" aria-label="Customer access mode">
            <button type="button" className={authMode === 'login' ? 'is-active' : ''} onClick={() => selectAuthMode('login')}>
              Login
            </button>
            <button type="button" className={authMode === 'signup' ? 'is-active' : ''} onClick={() => selectAuthMode('signup')}>
              Sign up
            </button>
          </div>

          <div className="customer-auth-title-row">
            <h2>{title}</h2>
            <span>{otpSent ? 'Step 2 of 2' : 'Step 1 of 2'}</span>
          </div>

          {!otpSent && !pendingGoogle ? (
            <div className="customer-auth-method-list" role="tablist" aria-label="Choose login method">
              <button
                type="button"
                className={`customer-auth-method-card ${authMethod === 'google' ? 'is-active' : ''}`}
                onClick={() => selectAuthMethod('google')}
              >
                <span className="customer-auth-method-icon google-dot">G</span>
                <span>
                  <strong>Continue with Google</strong>
                  <small>Use Google, then verify mobile</small>
                </span>
                <em>{authMethod === 'google' ? '✓' : ''}</em>
              </button>
              <button
                type="button"
                className={`customer-auth-method-card ${authMethod === 'mobile' ? 'is-active' : ''}`}
                onClick={() => selectAuthMethod('mobile')}
              >
                <span className="customer-auth-method-icon">•••</span>
                <span>
                  <strong>Mobile OTP</strong>
                  <small>{authMode === 'signup' ? 'Verify and create account' : 'Get a WhatsApp OTP securely'}</small>
                </span>
                <em>{authMethod === 'mobile' ? '✓' : ''}</em>
              </button>
            </div>
          ) : null}

          {authMethod === 'google' && !otpSent && !pendingGoogle ? (
            <div className="customer-google-panel customer-google-panel-modern">
              {googleClientId ? <div ref={googleButtonRef} className="customer-google-button" /> : null}
              {!googleClientId ? <p className="customer-helper-copy">Google Sign-In is not configured.</p> : null}
            </div>
          ) : null}

          {authMethod === 'mobile' && !otpSent ? (
            <form onSubmit={requestMobileOtp} className="customer-form customer-auth-action-form">
              <label>
                Mobile number
                <input
                  value={mobile}
                  onChange={(event) => setMobile(event.target.value)}
                  placeholder="+91 98765 43210"
                  inputMode="tel"
                  autoComplete="tel"
                />
              </label>
              <button className="glow-account-btn customer-auth-primary-action" type="submit" disabled={submitting}>
                {submitting ? 'Please wait...' : `${primaryActionLabel} →`}
              </button>
            </form>
          ) : null}

          {pendingGoogle && !otpSent ? (
            <form onSubmit={requestGoogleOtp} className="customer-form customer-auth-action-form">
              <div className="customer-auth-account-pill">
                <span>{pendingGoogle?.name?.charAt(0) || 'G'}</span>
                <div>
                  <strong>Continue as {pendingGoogle?.name || 'Google customer'}</strong>
                  <small>{pendingGoogle?.email || 'Google account verified'}</small>
                </div>
                <em>G</em>
              </div>
              <label>
                Mobile number
                <input
                  value={mobile}
                  onChange={(event) => setMobile(event.target.value)}
                  placeholder="+91 98765 43210"
                  inputMode="tel"
                  autoComplete="tel"
                />
              </label>
              <button className="glow-account-btn customer-auth-primary-action" type="submit" disabled={submitting}>
                {submitting ? 'Please wait...' : 'Send mobile OTP →'}
              </button>
              <button type="button" className="customer-auth-text-btn" onClick={() => setPendingGoogle(null)} disabled={submitting}>
                Use another method
              </button>
            </form>
          ) : null}

          {otpSent ? (
            <form onSubmit={verifyOtp} className="customer-form customer-auth-action-form">
              <div className="customer-session-card customer-otp-banner">
                <div>
                  <strong>{otpChallenge?.maskedMobile || mobile}</strong>
                  <span>OTP expires in {formatCountdown(expiryRemaining)}</span>
                </div>
                <div className="customer-otp-banner-meta">
                  <span>{resendRemaining > 0 ? `Resend in ${formatCountdown(resendRemaining)}` : 'You can resend now'}</span>
                </div>
              </div>
              <label>
                OTP
                <input
                  value={otp}
                  onChange={(event) => setOtp(event.target.value)}
                  placeholder="6 digit OTP"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                />
              </label>
              <button className="glow-account-btn customer-auth-primary-action" type="submit" disabled={submitting || !otp}>
                {submitting ? 'Please wait...' : `${primaryActionLabel} →`}
              </button>
              <div className="customer-auth-inline-actions">
                <button
                  type="button"
                  className="customer-auth-text-btn"
                  onClick={resendOtp}
                  disabled={submitting || resendRemaining > 0}
                >
                  {resendRemaining > 0 ? `Resend in ${formatCountdown(resendRemaining)}` : 'Resend OTP'}
                </button>
                <button type="button" className="customer-auth-text-btn" onClick={resetOtpFlow} disabled={submitting}>
                  Change number
                </button>
              </div>
            </form>
          ) : null}

          {message ? <p className="success-text">{message}</p> : null}
          {error ? <p className="error-text">{error}</p> : null}

          <div className="customer-auth-footer">
            <Link to="/privacy-policy">Privacy Policy</Link>
            <span>Store staff?</span>
            <Link to="/login">Staff Login</Link>
          </div>
        </div>
      </section>
    </main>
  );
}
