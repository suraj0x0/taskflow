import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { CheckSquare, Eye, EyeOff } from "lucide-react";
import { ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorMessage, Field, SubmitButton } from "../components/ui";

function AuthFrame({ children, title, subtitle }: { children: React.ReactNode; title: string; subtitle: string }) {
  return (
    <main className="auth-page">
      <section className="auth-card">
        <Link className="auth-brand brand" to="/">
          <span className="brand-mark"><CheckSquare size={20} strokeWidth={2.4} /></span>
          <span>taskflow</span>
        </Link>
        <div className="auth-heading">
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        {children}
      </section>
      <p className="auth-footnote">Plan clearly. Work together. Keep things moving.</p>
    </main>
  );
}

function fieldErrors(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fieldErrors : {};
}

function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "That email and password combination wasn’t recognized.";
    if (error.status === 409) return "An account with this email already exists.";
    return error.message;
  }
  return "Something went wrong. Please try again.";
}

export function LoginPage() {
  const { login, restoreError } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const registrationMessage = (location.state as { registered?: boolean } | null)?.registered;
  const routeMessage = (location.state as { message?: string } | null)?.message;
  const destination = (location.state as { from?: string } | null)?.from ?? "/";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!email.trim() || !password) {
      setError("Enter your email and password to continue.");
      return;
    }
    setBusy(true);
    try {
      await login(email.trim(), password);
      navigate(destination, { replace: true });
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthFrame title="Welcome back" subtitle="Sign in to pick up where your team left off.">
      {registrationMessage && <div className="notice notice-success">Your account is ready. Sign in to continue.</div>}
      {(restoreError || routeMessage) && <div className="notice notice-error" role="alert">{restoreError || routeMessage}</div>}
      <form className="form-stack" onSubmit={submit} noValidate>
        <Field id="login-email" label="Email address">
          <input id="login-email" type="email" autoComplete="email" maxLength={255} value={email}
            onChange={(event) => setEmail(event.target.value)} placeholder="you@company.com" required />
        </Field>
        <Field id="login-password" label="Password">
          <div className="password-input-wrap">
            <input id="login-password" type={showPassword ? "text" : "password"} autoComplete="current-password"
              value={password} onChange={(event) => setPassword(event.target.value)} required />
            <button type="button" className="password-toggle" aria-label={showPassword ? "Hide password" : "Show password"}
              onClick={() => setShowPassword((visible) => !visible)}>
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
        </Field>
        <ErrorMessage>{error}</ErrorMessage>
        <SubmitButton busy={busy} className="button button-primary button-wide">{busy ? "Signing in..." : "Sign in"}</SubmitButton>
      </form>
      <p className="auth-switch">New to TaskFlow? <Link to="/register">Create an account</Link></p>
    </AuthFrame>
  );
}

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [showPassword, setShowPassword] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setErrors({});
    if (!name.trim() || !email.trim() || password.length < 8) {
      setError("Enter your name, a valid email, and a password of at least 8 characters.");
      return;
    }
    setBusy(true);
    try {
      await register(name.trim(), email.trim(), password);
      navigate("/login", { replace: true, state: { registered: true } });
    } catch (cause) {
      setError(errorText(cause));
      setErrors(fieldErrors(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthFrame title="Create your account" subtitle="Start organizing your work with TaskFlow.">
      <form className="form-stack" onSubmit={submit} noValidate>
        <Field id="register-name" label="Full name" error={errors.name}>
          <input id="register-name" autoComplete="name" maxLength={100} value={name}
            onChange={(event) => setName(event.target.value)} placeholder="Your name" required />
        </Field>
        <Field id="register-email" label="Email address" error={errors.email}>
          <input id="register-email" type="email" autoComplete="email" maxLength={255} value={email}
            onChange={(event) => setEmail(event.target.value)} placeholder="you@company.com" required />
        </Field>
        <Field id="register-password" label="Password" error={errors.password}>
          <div className="password-input-wrap">
            <input id="register-password" type={showPassword ? "text" : "password"} autoComplete="new-password"
              minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} required />
            <button type="button" className="password-toggle" aria-label={showPassword ? "Hide password" : "Show password"}
              onClick={() => setShowPassword((visible) => !visible)}>
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
          <small className="field-hint">Use at least 8 characters.</small>
        </Field>
        <ErrorMessage>{error}</ErrorMessage>
        <SubmitButton busy={busy} className="button button-primary button-wide">{busy ? "Creating account..." : "Create account"}</SubmitButton>
      </form>
      <p className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></p>
    </AuthFrame>
  );
}
