import { useEffect, useState, type FormEvent } from "react";
import { Activity as ActivityIcon, ArrowLeft, MessageCircle, Pencil, Send, Trash2 } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { activitiesApi, commentsApi, tasksApi, usersApi } from "../api/services";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import { EmptyState, ErrorNotice, Field, FormActions, Modal, PageHeader, PriorityBadge, Spinner, StatusBadge, formatDate } from "../components/ui";
import type { Activity, Comment, Task, TaskPriority, TaskStatus, User } from "../types";

const statuses: TaskStatus[] = ["TODO", "IN_PROGRESS", "DONE"];

export function TaskDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === "ADMIN" || user?.role === "MANAGER";
  const [task, setTask] = useState<Task | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [workers, setWorkers] = useState<User[]>([]);
  const [workerError, setWorkerError] = useState("");
  const [activityError, setActivityError] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [showEdit, setShowEdit] = useState(false);
  const [commentText, setCommentText] = useState("");
  const [commentBusy, setCommentBusy] = useState(false);
  const [commentError, setCommentError] = useState("");

  async function load() {
    setBusy(true);
    setError("");
    try {
      const [taskData, commentData] = await Promise.all([
        tasksApi.get(id),
        commentsApi.list(id),
      ]);
      setTask(taskData);
      setComments(commentData);
      await refreshActivities(id);
      if (canManage) {
        try {
          setWorkers((await usersApi.list()).filter((candidate) => candidate.role === "WORKER"));
          setWorkerError("");
        } catch (cause) {
          setWorkerError(cause instanceof Error ? cause.message : "Could not load the worker list.");
        }
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load this task.");
    } finally { setBusy(false); }
  }

  async function refreshActivities(taskId: string) {
    try {
      setActivities(await activitiesApi.list(taskId));
      setActivityError("");
    } catch (cause) {
      setActivityError(cause instanceof Error ? cause.message : "Could not load task activity.");
    }
  }

  useEffect(() => { void load(); }, [id, canManage]);

  async function changeStatus(status: TaskStatus) {
    if (!task || !user) return;
    setError("");
    try {
      const saved = canManage
        ? await tasksApi.update(task.id, { status })
        : await tasksApi.updateStatus(task.id, status);
      setTask(saved);
      await refreshActivities(task.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not change task status.");
    }
  }

  async function assign(workerId: string) {
    if (!task || !workerId) return;
    setError("");
    try {
      setTask(await tasksApi.assign(task.id, workerId));
      await refreshActivities(task.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not assign this task.");
    }
  }

  async function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!commentText.trim()) { setCommentError("Comment cannot be blank."); return; }
    setCommentBusy(true);
    setCommentError("");
    try {
      const saved = await commentsApi.create(id, commentText.trim());
      setComments((items) => [...items, saved]);
      setCommentText("");
      await refreshActivities(id);
    } catch (cause) {
      setCommentError(cause instanceof Error ? cause.message : "Could not post comment.");
    } finally { setCommentBusy(false); }
  }

  async function deleteTask() {
    if (!task || !window.confirm(`Delete “${task.title}”? This cannot be undone.`)) return;
    try {
      await tasksApi.remove(task.id);
      navigate(`/projects/${task.projectId}`, { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not delete task.");
    }
  }

  async function saveComment(comment: Comment, content: string) {
    const updated = await commentsApi.update(comment.id, content);
    setComments((items) => items.map((item) => item.id === updated.id ? updated : item));
    await refreshActivities(id);
  }

  async function removeComment(comment: Comment) {
    if (!window.confirm("Delete this comment?")) return;
    try {
      await commentsApi.remove(comment.id);
      setComments((items) => items.filter((item) => item.id !== comment.id));
      await refreshActivities(id);
    } catch (cause) {
      setCommentError(cause instanceof Error ? cause.message : "Could not delete comment.");
    }
  }

  if (busy && !task) return <Spinner label="Loading task" />;
  if (error && !task) return <ErrorNotice message={error} onRetry={() => void load()} />;
  if (!task) return null;
  const isAssignedWorker = user?.role === "WORKER" && task.assigneeId === user.id;

  return (
    <>
      <Link className="back-link" to={`/projects/${task.projectId}`}><ArrowLeft size={16} /> {task.projectName}</Link>
      <PageHeader eyebrow="TASK DETAILS" title={task.title}
        action={canManage && <div className="button-row">
          <button className="button button-secondary" onClick={() => setShowEdit(true)}><Pencil size={16} /> Edit task</button>
          <button className="button button-danger-outline" onClick={() => void deleteTask()}>Delete</button>
        </div>} />
      {error && <ErrorNotice message={error} />}
      <div className="task-detail-layout">
        <div className="task-detail-main">
          <section className="surface-card task-overview">
            <div className="task-overview-badges"><StatusBadge status={task.status} /><PriorityBadge priority={task.priority} /></div>
            <p className="task-description">{task.description || "No description has been added."}</p>
            <div className="task-metadata">
              <div><span>Project</span><Link to={`/projects/${task.projectId}`}>{task.projectName}</Link></div>
              <div><span>Created by</span><strong>{task.createdByName}</strong></div>
              <div><span>Assignee</span><strong>{task.assigneeName || "Unassigned"}</strong></div>
              <div><span>Last updated</span><strong>{formatDate(task.updatedAt)}</strong></div>
            </div>
            {(canManage || isAssignedWorker) && (
              <div className="task-controls">
                <Field id="task-status-change" label="Status">
                  <select id="task-status-change" value={task.status} onChange={(event) => void changeStatus(event.target.value as TaskStatus)}>
                    {statuses.filter((status) => canManage || status === task.status
                      || (task.status === "TODO" && status === "IN_PROGRESS")
                      || (task.status === "IN_PROGRESS" && status === "DONE"))
                      .map((status) => <option value={status} key={status}>{status.replace("_", " ")}</option>)}
                  </select>
                </Field>
                {canManage && <Field id="task-assignee-change" label="Assign to">
                  <select id="task-assignee-change" value={task.assigneeId ?? ""} onChange={(event) => void assign(event.target.value)}>
                    <option value="">Choose a worker</option>
                    {workers.map((worker) => <option value={worker.id} key={worker.id}>{worker.name} · {worker.email}</option>)}
                  </select>
                </Field>}
              </div>
            )}
            {workerError && <p className="inline-error" role="alert">{workerError}</p>}
          </section>

          <section className="surface-card detail-section">
            <div className="section-heading compact-heading"><div><span className="eyebrow">DISCUSSION</span><h2><MessageCircle size={19} /> Comments <span className="count-pill">{comments.length}</span></h2></div></div>
            <form className="comment-composer" onSubmit={addComment}>
              <label className="sr-only" htmlFor="comment-content">Write a comment</label>
              <textarea id="comment-content" rows={3} maxLength={2000} placeholder="Share an update or ask a question..." value={commentText} onChange={(event) => setCommentText(event.target.value)} />
              <div className="composer-footer"><small>{commentText.length}/2000</small><button className="button button-primary button-small" disabled={commentBusy}><Send size={15} /> {commentBusy ? "Posting..." : "Post comment"}</button></div>
              {commentError && <p className="inline-error" role="alert">{commentError}</p>}
            </form>
            {comments.length ? <div className="comment-list">
              {comments.map((comment) => <CommentItem key={comment.id} comment={comment}
                canEdit={comment.authorId === user?.id}
                onSave={(content) => saveComment(comment, content)}
                onDelete={() => void removeComment(comment)} />)}
            </div> : <p className="muted-copy">No comments yet. Be the first to add one.</p>}
          </section>
        </div>

        <aside className="surface-card activity-panel">
          <div className="section-heading compact-heading"><div><span className="eyebrow">TASK HISTORY</span><h2><ActivityIcon size={19} /> Activity</h2></div></div>
          {activityError && <p className="inline-error" role="alert">{activityError}</p>}
          {activities.length ? (
            <ol className="activity-timeline">
              {activities.map((activity) => <li key={activity.id}>
                <span className="timeline-dot" />
                <div><strong>{activity.description}</strong><p>{activity.actorName} · {formatDate(activity.createdAt)}</p></div>
              </li>)}
            </ol>
          ) : <EmptyState title="No activity yet" description="Changes to this task will appear here." icon={<ActivityIcon size={22} />} />}
        </aside>
      </div>
      {showEdit && <TaskEditModal task={task} onClose={() => setShowEdit(false)}
        onSaved={(saved) => { setTask(saved); setShowEdit(false); void refreshActivities(id); }} />}
    </>
  );
}

function CommentItem({ comment, canEdit, onSave, onDelete }: {
  comment: Comment;
  canEdit: boolean;
  onSave: (content: string) => Promise<void>;
  onDelete: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(comment.content);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!content.trim()) { setError("Comment cannot be blank."); return; }
    setBusy(true);
    setError("");
    try { await onSave(content.trim()); setEditing(false); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not update comment."); }
    finally { setBusy(false); }
  }

  return (
    <article className="comment-item">
      <span className="avatar avatar-small">{comment.authorName.slice(0, 1).toUpperCase()}</span>
      <div className="comment-body">
        <div className="comment-author"><strong>{comment.authorName}</strong><time>{formatDate(comment.createdAt)}</time>
          {canEdit && <div className="comment-actions">
            <button className="text-button" onClick={() => { setContent(comment.content); setEditing((value) => !value); }}>{editing ? "Cancel" : "Edit"}</button>
            <button className="icon-button icon-danger" aria-label="Delete comment" onClick={onDelete}><Trash2 size={15} /></button>
          </div>}
        </div>
        {editing ? <form className="form-stack comment-edit-form" onSubmit={submit}>
          <textarea value={content} maxLength={2000} onChange={(event) => setContent(event.target.value)} aria-label="Edit comment" />
          {error && <p className="inline-error">{error}</p>}
          <button className="button button-primary button-small" disabled={busy}>{busy ? "Saving..." : "Save comment"}</button>
        </form> : <p>{comment.content}</p>}
      </div>
    </article>
  );
}

function TaskEditModal({ task, onClose, onSaved }: {
  task: Task;
  onClose: () => void;
  onSaved: (task: Task) => void;
}) {
  const [title, setTitle] = useState(task.title);
  const [description, setDescription] = useState(task.description ?? "");
  const [priority, setPriority] = useState<TaskPriority>(task.priority);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!title.trim()) { setError("Task title is required."); return; }
    const body: { title: string; priority: TaskPriority; description?: string } = { title: title.trim(), priority };
    if (description.trim()) body.description = description.trim();
    else if (task.description) {
      setError("The backend does not support clearing a saved description through this update endpoint.");
      return;
    }
    setBusy(true);
    setError("");
    try { onSaved(await tasksApi.update(task.id, body)); }
    catch (cause) { setError(cause instanceof ApiError ? cause.message : "Could not save task."); }
    finally { setBusy(false); }
  }

  return (
    <Modal title="Edit task" onClose={onClose}>
      <form className="modal-body form-stack" onSubmit={submit} noValidate>
        <Field id="edit-task-title" label="Task title">
          <input id="edit-task-title" autoFocus maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} />
        </Field>
        <Field id="edit-task-description" label="Description">
          <textarea id="edit-task-description" rows={4} maxLength={2000} value={description} onChange={(event) => setDescription(event.target.value)} />
        </Field>
        <Field id="edit-task-priority" label="Priority">
          <select id="edit-task-priority" value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}>
            <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option>
          </select>
        </Field>
        {error && <p className="inline-error" role="alert">{error}</p>}
        <FormActions busy={busy} submitLabel="Save changes" onCancel={onClose} />
      </form>
    </Modal>
  );
}
