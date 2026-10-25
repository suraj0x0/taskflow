import { useEffect, type FormEvent, type PropsWithChildren, type ReactNode } from "react";
import { AlertCircle, Check, LoaderCircle, X } from "lucide-react";
import type { TaskPriority, TaskStatus } from "../types";

export function Spinner({ label = "Loading" }: { label?: string }) {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <LoaderCircle className="spin" size={20} aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorNotice({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="notice notice-error" role="alert">
      <AlertCircle size={18} aria-hidden="true" />
      <span>{message}</span>
      {onRetry && <button className="button button-quiet button-small" onClick={onRetry}>Retry</button>}
    </div>
  );
}

export function SuccessNotice({ children }: PropsWithChildren) {
  return (
    <div className="notice notice-success" role="status">
      <Check size={18} aria-hidden="true" />
      <span>{children}</span>
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
  icon,
}: {
  title: string;
  description: string;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <div className="empty-icon">{icon}</div>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-header">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {action && <div className="page-header-action">{action}</div>}
    </div>
  );
}

export function StatusBadge({ status }: { status: TaskStatus }) {
  const label = status.replace("_", " ").toLowerCase();
  return <span className={`badge badge-status status-${status.toLowerCase()}`}>{label}</span>;
}

export function PriorityBadge({ priority }: { priority: TaskPriority }) {
  return <span className={`badge badge-priority priority-${priority.toLowerCase()}`}>{priority.toLowerCase()}</span>;
}

export function Modal({
  title,
  onClose,
  children,
  size = "normal",
}: PropsWithChildren<{ title: string; onClose: () => void; size?: "normal" | "large" }>) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section
        className={`modal-panel ${size === "large" ? "modal-large" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
      >
        <header className="modal-header">
          <h2 id="modal-title">{title}</h2>
          <button className="icon-button" aria-label="Close dialog" onClick={onClose}>
            <X size={18} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

export function SubmitButton({
  busy,
  children,
  className = "button button-primary",
}: PropsWithChildren<{ busy: boolean; className?: string }>) {
  return (
    <button className={className} type="submit" disabled={busy}>
      {busy && <LoaderCircle size={16} className="spin" aria-hidden="true" />}
      {children}
    </button>
  );
}

export function Field({
  label,
  id,
  error,
  children,
}: PropsWithChildren<{ label: string; id: string; error?: string }>) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error && <span className="field-error" role="alert">{error}</span>}
    </div>
  );
}

export function FormActions({
  onCancel,
  busy,
  submitLabel,
}: {
  onCancel?: () => void;
  busy: boolean;
  submitLabel: string;
}) {
  return (
    <div className="form-actions">
      {onCancel && <button type="button" className="button button-secondary" onClick={onCancel}>Cancel</button>}
      <SubmitButton busy={busy}>{submitLabel}</SubmitButton>
    </div>
  );
}

export function Form({
  onSubmit,
  children,
  className,
}: PropsWithChildren<{ onSubmit: (event: FormEvent<HTMLFormElement>) => void; className?: string }>) {
  return <form className={className ?? "form-stack"} onSubmit={onSubmit} noValidate>{children}</form>;
}

export function ErrorMessage({ children }: PropsWithChildren) {
  return children ? <p className="inline-error" role="alert">{children}</p> : null;
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" })
    .format(new Date(value));
}
