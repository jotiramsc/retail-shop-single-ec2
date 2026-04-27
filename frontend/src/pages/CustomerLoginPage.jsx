import { useEffect, useMemo, useState } from 'react';
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
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());
  const [otpChallenge, setOtpChallenge] = useState(() => readStoredOtpChallenge());
  const [mobile, setMobile] = useState(() => readStoredOtpChallenge()?.mobile || '');
  const [otp, setOtp] = useState('');
  const [devOtp, setDevOtp] = useState(() => readStoredOtpChallenge()?.devOtp || '');
  const [message, setMessage] = useState(() => readStoredOtpChallenge()?.message || '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [clock, setClock] = useState(Date.now());

  useEffect(() => {
    if (!otpChallenge) {
      return undefined;
    }

    const timer = window.setInterval(() => setClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
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

  const updateOtpChallenge = (response, nextMobile) => {
    const resendCooldownSeconds = Number(response?.resendCooldownSeconds || 30);
    const expiresInSeconds = Number(response?.expiresInSeconds || 300);
    const nextMessage = response?.message || 'OTP sent on WhatsApp.';
    const nextChallenge = {
      mobile: nextMobile,
      maskedMobile: response?.maskedMobile || nextMobile,
      resendAvailableAt: Date.now() + (resendCooldownSeconds * 1000),
      expiresAt: Date.now() + (expiresInSeconds * 1000),
      message: nextMessage,
      devOtp: response?.devOtp || ''
    };

    setOtpChallenge(nextChallenge);
    setDevOtp(nextChallenge.devOtp);
    setMessage(nextChallenge.message);
    setOtp('');
    storeOtpChallenge(nextChallenge);
  };

  const resetOtpFlow = () => {
    clearOtpChallenge();
    setOtpChallenge(null);
    setOtp('');
    setDevOtp('');
    setMessage('');
    setError('');
  };

  const logoutAndUseAnotherNumber = () => {
    clearCustomerSession();
    resetOtpFlow();
    setCustomerSession(null);
    setMobile('');
  };

  const requestOtp = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const nextMobile = mobile.trim();
      const response = await retailService.sendOtp({ mobile: nextMobile });
      updateOtpChallenge(response, nextMobile);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to send OTP');
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
    try {
      const response = await retailService.sendOtp({ mobile: otpChallenge.mobile });
      updateOtpChallenge(response, otpChallenge.mobile);
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
      const session = await retailService.verifyOtp({ mobile: otpChallenge?.mobile || mobile, otp });
      storeCustomerSession(session);
      clearOtpChallenge();
      setCustomerSession(session);
      const guestItems = getGuestCartItems();
      if (guestItems.length) {
        await retailService.mergeCart({ items: guestItems });
        clearGuestCart();
      }
      navigate(redirectTo);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to verify OTP');
    } finally {
      setSubmitting(false);
    }
  };

  if (customerSession?.token) {
    return (
      <main className="glow-site customer-flow-page">
        <section className="customer-card customer-auth-card">
          <p className="glow-kicker">Customer Access</p>
          <h1>You are already signed in</h1>
          <div className="customer-session-card">
            <strong>{customerSession.name || 'Customer'}</strong>
            <span>{customerSession.mobile}</span>
          </div>
          <div className="customer-form-actions">
            <Link className="glow-account-btn" to={redirectTo}>Continue</Link>
            <Link className="ghost-btn compact-btn" to="/account">My account</Link>
            <button type="button" className="ghost-btn compact-btn" onClick={logoutAndUseAnotherNumber}>
              Use a different number
            </button>
          </div>
          <div className="customer-auth-footer">
            <span>Need store access?</span>
            <Link to="/login">Staff Login</Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="glow-site customer-flow-page">
      <section className="customer-card customer-auth-card">
        <p className="glow-kicker">Customer Access</p>
        <h1>{otpSent ? 'Verify your WhatsApp OTP' : 'Login with WhatsApp OTP'}</h1>
        <form onSubmit={otpSent ? verifyOtp : requestOtp} className="customer-form">
          <label>
            Mobile number
            <input
              value={mobile}
              onChange={(event) => setMobile(event.target.value)}
              placeholder="+91 98765 43210"
              disabled={otpSent}
            />
          </label>
          {otpSent ? (
            <>
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
                <input value={otp} onChange={(event) => setOtp(event.target.value)} placeholder="6 digit OTP" />
              </label>
            </>
          ) : null}
          {devOtp ? (
            <div className="customer-dev-otp">
              <strong>OTP on screen</strong>
              <span>{devOtp}</span>
            </div>
          ) : null}
          {message ? <p className="success-text">{message}</p> : null}
          {error ? <p className="error-text">{error}</p> : null}
          <div className="customer-form-actions">
            <button className="glow-account-btn" type="submit" disabled={submitting}>
              {submitting ? 'Please wait...' : (otpSent ? 'Verify & Continue' : 'Send OTP')}
            </button>
            {otpSent ? (
              <>
                <button
                  type="button"
                  className="ghost-btn compact-btn"
                  onClick={resendOtp}
                  disabled={submitting || resendRemaining > 0}
                >
                  {resendRemaining > 0 ? `Resend in ${formatCountdown(resendRemaining)}` : 'Resend OTP'}
                </button>
                <button type="button" className="ghost-btn compact-btn" onClick={resetOtpFlow} disabled={submitting}>
                  Change number
                </button>
              </>
            ) : null}
          </div>
        </form>
        <div className="customer-auth-footer">
          <span>Store staff?</span>
          <Link to="/login">Staff Login</Link>
        </div>
      </section>
    </main>
  );
}
