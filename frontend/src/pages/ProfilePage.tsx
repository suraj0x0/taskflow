import { useState, type FormEvent } from "react";
import { KeyRound, Save, UserRound } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { usersApi } from "../api/services";
import { useAuth } from "../auth/AuthContext";
import { Field, PageHeader } from "../components/ui";

export function ProfilePage() {
  const { user, refreshUser, logout } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState(user?.name ?? "");
  const [email, setEmail] = useState(user?.email ?? "");
  const [profileBusy, setProfileBusy] = useState(false);
  const [profileError, setProfileError] = useState("");
  const [profileSuccess, setProfileSuccess] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [passwordError, setPasswordError] = useState("");
  const [passwordSuccess, setPasswordSuccess] = useState("");

  async function updateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setProfileError("");
    setProfileSuccess("");
    if (!name.trim() || !email.trim()) { setProfileError("Name and email are required."); return; }
    setProfileBusy(true);
    try {
      const changedEmail = email.trim() !== user?.email;
      await usersApi.updateMe({ name: name.trim(), email: email.trim() });
      if (changedEmail) {
        logout();
        navigate("/login", { replace: true, state: { message: "Your email changed. Sign in again with your new email address." } });
        return;
      }
      const updated = await refreshUser();
      setName(updated.name);
      setEmail(updated.email);
      setProfileSuccess("Your profile has been updated.");
    } catch (cause) {
      setProfileError(cause instanceof ApiError ? cause.message : "Could not update your profile.");
    } finally { setProfileBusy(false); }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordError("");
    setPasswordSuccess("");
    if (!currentPassword || !newPassword) { setPasswordError("Complete all password fields."); return; }
    if (newPassword.length < 8) { setPasswordError("New password must be at least 8 characters."); return; }
    if (newPassword !== confirmPassword) { setPasswordError("The new passwords do not match."); return; }
    setPasswordBusy(true);
    try {
      await usersApi.changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setPasswordSuccess("Your password has been changed.");
    } catch (cause) {
      setPasswordError(cause instanceof ApiError ? cause.message : "Could not change your password.");
    } finally { setPasswordBusy(false); }
  }

  return (
    <>
      <PageHeader eyebrow="ACCOUNT SETTINGS" title="Profile and password" description="Manage your personal information and sign-in credentials." />
      <div className="settings-grid">
        <section className="surface-card settings-card">
          <div className="settings-card-heading"><span className="settings-icon"><UserRound size={18} /></span><div><h2>Personal information</h2><p>Update how your name appears in TaskFlow.</p></div></div>
          <form className="form-stack" onSubmit={updateProfile} noValidate>
            <Field id="profile-name" label="Full name"><input id="profile-name" value={name} maxLength={100} onChange={(event) => setName(event.target.value)} required /></Field>
            <Field id="profile-email" label="Email address"><input id="profile-email" type="email" value={email} maxLength={255} onChange={(event) => setEmail(event.target.value)} required /></Field>
            <div className="profile-role"><span>Workspace role</span><span className={`role-chip role-${user?.role.toLowerCase()}`}>{user?.role}</span></div>
            {profileError && <p className="inline-error" role="alert">{profileError}</p>}
            {profileSuccess && <p className="inline-success" role="status">{profileSuccess}</p>}
            <button className="button button-primary settings-submit" disabled={profileBusy}><Save size={16} /> {profileBusy ? "Saving..." : "Save profile"}</button>
          </form>
        </section>
        <section className="surface-card settings-card">
          <div className="settings-card-heading"><span className="settings-icon"><KeyRound size={18} /></span><div><h2>Change password</h2><p>Choose a new password with at least 8 characters.</p></div></div>
          <form className="form-stack" onSubmit={changePassword} noValidate>
            <Field id="current-password" label="Current password"><input id="current-password" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required /></Field>
            <Field id="new-password" label="New password"><input id="new-password" type="password" autoComplete="new-password" minLength={8} value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required /></Field>
            <Field id="confirm-password" label="Confirm new password"><input id="confirm-password" type="password" autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required /></Field>
            {passwordError && <p className="inline-error" role="alert">{passwordError}</p>}
            {passwordSuccess && <p className="inline-success" role="status">{passwordSuccess}</p>}
            <button className="button button-primary settings-submit" disabled={passwordBusy}><KeyRound size={16} /> {passwordBusy ? "Updating..." : "Change password"}</button>
          </form>
        </section>
      </div>
    </>
  );
}
