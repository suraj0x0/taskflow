import { useEffect, useState } from "react";
import { Bell, CheckCheck, Check, ExternalLink } from "lucide-react";
import { Link } from "react-router-dom";
import { notificationsApi } from "../api/services";
import { EmptyState, ErrorNotice, PageHeader, Spinner, formatDate } from "../components/ui";
import type { Notification } from "../types";

export function NotificationsPage() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [busy, setBusy] = useState(true);
  const [savingAll, setSavingAll] = useState(false);
  const [error, setError] = useState("");

  async function load() {
    setBusy(true);
    setError("");
    try { setNotifications(await notificationsApi.list()); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not load notifications."); }
    finally { setBusy(false); }
  }

  useEffect(() => { void load(); }, []);

  async function markRead(notification: Notification) {
    if (notification.read) return;
    setError("");
    try {
      const updated = await notificationsApi.markRead(notification.id);
      setNotifications((items) => items.map((item) => item.id === updated.id ? updated : item));
      window.dispatchEvent(new Event("taskflow:notifications-changed"));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not mark notification as read."); }
  }

  async function markAllRead() {
    setSavingAll(true);
    setError("");
    try {
      await notificationsApi.markAllRead();
      setNotifications((items) => items.map((item) => ({ ...item, read: true })));
      window.dispatchEvent(new Event("taskflow:notifications-changed"));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not mark notifications as read."); }
    finally { setSavingAll(false); }
  }

  const unreadCount = notifications.filter((notification) => !notification.read).length;
  return (
    <>
      <PageHeader eyebrow="YOUR INBOX" title="Notifications"
        description={`${unreadCount} unread notification${unreadCount === 1 ? "" : "s"}`}
        action={unreadCount > 0 && <button className="button button-secondary" onClick={() => void markAllRead()} disabled={savingAll}>
          <CheckCheck size={16} /> {savingAll ? "Marking read..." : "Mark all as read"}
        </button>} />
      {error && <ErrorNotice message={error} onRetry={() => void load()} />}
      {busy ? <Spinner label="Loading notifications" /> : notifications.length ? (
        <section className="surface-card notification-list" aria-label="Notifications">
          {notifications.map((notification) => (
            <article className={`notification-item ${notification.read ? "" : "notification-unread"}`} key={notification.id}>
              <div className={`notification-icon ${notification.read ? "" : "notification-icon-unread"}`}><Bell size={18} /></div>
              <div className="notification-copy"><p>{notification.message}</p><time>{formatDate(notification.createdAt)}</time></div>
              <div className="notification-actions">
                {!notification.read && <button className="icon-button" aria-label="Mark as read" title="Mark as read" onClick={() => void markRead(notification)}><Check size={17} /></button>}
                {notification.taskId && <Link className="icon-button" to={`/tasks/${notification.taskId}`} aria-label="Open related task" title="Open related task"><ExternalLink size={16} /></Link>}
              </div>
            </article>
          ))}
        </section>
      ) : !error && <EmptyState title="You're all caught up" description="Notifications about task assignments and status changes will show up here." icon={<Bell size={24} />} />}
    </>
  );
}
