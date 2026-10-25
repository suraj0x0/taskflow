import { useEffect, useState, type FormEvent } from "react";
import { Pencil, UserPlus, UsersRound, Trash2 } from "lucide-react";
import { ApiError } from "../api/client";
import { usersApi } from "../api/services";
import { EmptyState, ErrorNotice, Field, FormActions, Modal, PageHeader, Spinner } from "../components/ui";
import type { Role, User } from "../types";
import { useAuth } from "../auth/AuthContext";

export function UsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);

  async function load() {
    setBusy(true);
    setError("");
    try { setUsers(await usersApi.list()); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not load users."); }
    finally { setBusy(false); }
  }

  useEffect(() => { void load(); }, []);

  async function changeRole(user: User, role: Role) {
    if (role === user.role) return;
    setError("");
    try {
      const updated = await usersApi.updateRole(user.id, role);
      setUsers((items) => items.map((item) => item.id === updated.id ? updated : item));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not change user role."); }
  }

  async function remove(user: User) {
    if (!window.confirm(`Delete ${user.name} (${user.email})? This cannot be undone.`)) return;
    setError("");
    try {
      await usersApi.remove(user.id);
      setUsers((items) => items.filter((item) => item.id !== user.id));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not delete user."); }
  }

  async function update(person: User, name: string, email: string) {
    setError("");
    const changedEmail = email !== person.email;
    try {
      const updated = await usersApi.update(person.id, { name, email });
      setUsers((items) => items.map((item) => item.id === updated.id ? updated : item));
      setEditing(null);
      setNotice(changedEmail ? `${updated.name}'s email was changed. They will need to sign in again.` : "User details were updated.");
    } catch (cause) {
      throw cause;
    }
  }

  return (
    <>
      <PageHeader eyebrow="WORKSPACE ADMIN" title="People"
        description="Manage workspace accounts, roles, and access."
        action={<button className="button button-primary" onClick={() => setShowCreate(true)}><UserPlus size={17} /> Add person</button>} />
      {error && <ErrorNotice message={error} onRetry={() => void load()} />}
      {notice && <div className="notice notice-success" role="status">{notice}</div>}
      {busy ? <Spinner label="Loading people" /> : users.length ? (
        <div className="surface-card table-card">
          <div className="table-wrap"><table className="data-table">
            <thead><tr><th>Person</th><th>Role</th><th><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>{users.map((person) => <tr key={person.id}>
              <td><div className="user-cell"><span className="avatar avatar-small">{person.name.slice(0, 1).toUpperCase()}</span><span><strong>{person.name}</strong><small>{person.email}</small></span></div></td>
              <td><select aria-label={`Role for ${person.name}`} className="role-select" value={person.role} disabled={person.id === currentUser?.id} onChange={(event) => void changeRole(person, event.target.value as Role)}>
                <option value="ADMIN">Admin</option><option value="MANAGER">Manager</option><option value="WORKER">Worker</option>
              </select></td>
              <td className="table-actions"><div className="button-row">
                {person.id !== currentUser?.id && <button className="icon-button" aria-label={`Edit ${person.name}`} onClick={() => setEditing(person)}><Pencil size={15} /></button>}
                {person.id !== currentUser?.id && <button className="icon-button icon-danger" aria-label={`Delete ${person.name}`} onClick={() => void remove(person)}><Trash2 size={16} /></button>}
              </div></td>
            </tr>)}</tbody>
          </table></div>
          <div className="table-footer">{users.length} workspace member{users.length === 1 ? "" : "s"}</div>
        </div>
      ) : !error && <EmptyState title="No workspace members" description="Add a person to start collaborating." icon={<UsersRound size={24} />} />}
      {showCreate && <CreateUserModal onClose={() => setShowCreate(false)} onCreated={(created) => { setUsers((items) => [...items, created]); setShowCreate(false); }} />}
      {editing && <EditUserModal user={editing} onClose={() => setEditing(null)} onSaved={update} />}
    </>
  );
}

function EditUserModal({ user, onClose, onSaved }: {
  user: User;
  onClose: () => void;
  onSaved: (user: User, name: string, email: string) => Promise<void>;
}) {
  const [name, setName] = useState(user.name);
  const [email, setEmail] = useState(user.email);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim() || !email.trim()) { setError("Name and email are required."); return; }
    setBusy(true);
    setError("");
    try { await onSaved(user, name.trim(), email.trim()); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not update user."); }
    finally { setBusy(false); }
  }

  return <Modal title="Edit person" onClose={onClose}>
    <form className="modal-body form-stack" onSubmit={submit} noValidate>
      <Field id="edit-user-name" label="Full name"><input id="edit-user-name" autoFocus maxLength={100} value={name} onChange={(event) => setName(event.target.value)} /></Field>
      <Field id="edit-user-email" label="Email address"><input id="edit-user-email" type="email" maxLength={255} value={email} onChange={(event) => setEmail(event.target.value)} /></Field>
      <p className="field-hint">If the email changes, the person will need to sign in again.</p>
      {error && <p className="inline-error" role="alert">{error}</p>}
      <FormActions busy={busy} submitLabel="Save changes" onCancel={onClose} />
    </form>
  </Modal>;
}

function CreateUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: (user: User) => void }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("WORKER");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!name.trim() || !email.trim() || !password) { setError("Complete all fields."); return; }
    if (password.length < 8) { setError("Password must be at least 8 characters."); return; }
    setBusy(true);
    try { onCreated(await usersApi.create({ name: name.trim(), email: email.trim(), password, role })); }
    catch (cause) { setError(cause instanceof ApiError ? cause.message : "Could not create account."); }
    finally { setBusy(false); }
  }

  return <Modal title="Add a person" onClose={onClose}>
    <form className="modal-body form-stack" onSubmit={submit} noValidate>
      <Field id="new-user-name" label="Full name"><input id="new-user-name" maxLength={100} autoFocus value={name} onChange={(event) => setName(event.target.value)} /></Field>
      <Field id="new-user-email" label="Email address"><input id="new-user-email" type="email" maxLength={255} value={email} onChange={(event) => setEmail(event.target.value)} /></Field>
      <Field id="new-user-password" label="Initial password"><input id="new-user-password" type="password" minLength={8} autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} /></Field>
      <Field id="new-user-role" label="Workspace role"><select id="new-user-role" value={role} onChange={(event) => setRole(event.target.value as Role)}>
        <option value="WORKER">Worker</option><option value="MANAGER">Manager</option><option value="ADMIN">Admin</option>
      </select></Field>
      {error && <p className="inline-error" role="alert">{error}</p>}
      <FormActions busy={busy} submitLabel="Create account" onCancel={onClose} />
    </form>
  </Modal>;
}
