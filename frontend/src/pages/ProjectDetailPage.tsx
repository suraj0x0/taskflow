import { useEffect, useState, type FormEvent } from "react";
import { ArrowLeft, CheckSquare, Pencil, Plus } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/client";
import { projectsApi, tasksApi } from "../api/services";
import { useAuth } from "../auth/AuthContext";
import { EmptyState, ErrorNotice, Field, FormActions, Modal, PageHeader, PriorityBadge, Spinner, StatusBadge, formatDate } from "../components/ui";
import type { Project, Task, TaskPriority } from "../types";
import { ProjectFormModal } from "./ProjectsPage";

export function ProjectDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === "ADMIN" || user?.role === "MANAGER";
  const [project, setProject] = useState<Project | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [showTaskForm, setShowTaskForm] = useState(false);
  const [editingProject, setEditingProject] = useState(false);

  async function load() {
    setBusy(true);
    setError("");
    try {
      const [details, projectTasks] = await Promise.all([projectsApi.get(id), tasksApi.listByProject(id)]);
      setProject(details);
      setTasks(projectTasks);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load this project.");
    } finally { setBusy(false); }
  }

  useEffect(() => { void load(); }, [id]);

  async function deleteProject() {
    if (!project || !window.confirm(`Delete “${project.name}”? Projects with tasks cannot be deleted.`)) return;
    try {
      await projectsApi.remove(project.id);
      navigate("/projects", { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not delete project.");
    }
  }

  if (busy && !project) return <Spinner label="Loading project" />;
  if (error && !project) return <ErrorNotice message={error} onRetry={() => void load()} />;
  if (!project) return null;

  return (
    <>
      <Link className="back-link" to="/projects"><ArrowLeft size={16} /> All projects</Link>
      <PageHeader eyebrow="PROJECT" title={project.name} description={project.description || "No description has been added."}
        action={canManage && <div className="button-row">
          <button className="button button-secondary" onClick={() => setEditingProject(true)}><Pencil size={16} /> Edit</button>
          <button className="button button-danger-outline" onClick={() => void deleteProject()}>Delete</button>
        </div>} />
      {error && <ErrorNotice message={error} onRetry={() => void load()} />}
      <section className="detail-meta">
        <span>Created by <strong>{project.createdByName}</strong></span>
        <span>Created {formatDate(project.createdAt)}</span>
        <span>{tasks.length} {tasks.length === 1 ? "task" : "tasks"}</span>
      </section>
      <div className="section-heading task-section-heading">
        <div><span className="eyebrow">PROJECT WORK</span><h2>Tasks</h2></div>
        {canManage && <button className="button button-primary" onClick={() => setShowTaskForm(true)}><Plus size={17} /> Add task</button>}
      </div>
      {tasks.length ? (
        <div className="surface-card task-table-card">
          <div className="task-table">
            <div className="task-table-header"><span>Task</span><span>Status</span><span>Priority</span><span>Assignee</span></div>
            {tasks.map((task) => <Link className="task-table-row" to={`/tasks/${task.id}`} key={task.id}>
              <span className="table-task-title"><CheckSquare size={16} />{task.title}</span>
              <StatusBadge status={task.status} />
              <PriorityBadge priority={task.priority} />
              <span className="table-assignee">{task.assigneeName || "Unassigned"}</span>
            </Link>)}
          </div>
        </div>
      ) : (
        <EmptyState title="No tasks in this project" description={canManage ? "Add a task to start tracking work in this project." : "Tasks added to this project will appear here."}
          icon={<CheckSquare size={23} />}
          action={canManage && <button className="button button-primary" onClick={() => setShowTaskForm(true)}><Plus size={16} /> Add task</button>} />
      )}
      {showTaskForm && <TaskCreateModal project={project} onClose={() => setShowTaskForm(false)}
        onSaved={(task) => { setTasks((items) => [task, ...items]); setShowTaskForm(false); }} />}
      {editingProject && <ProjectFormModal mode="edit" project={project} onClose={() => setEditingProject(false)}
        onSaved={(saved) => { setProject(saved); setEditingProject(false); }} />}
    </>
  );
}

function TaskCreateModal({ project, onClose, onSaved }: {
  project: Project;
  onClose: () => void;
  onSaved: (task: Task) => void;
}) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<TaskPriority>("MEDIUM");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!title.trim()) { setError("Task title is required."); return; }
    setBusy(true);
    setError("");
    try {
      const saved = await tasksApi.create(project.id, {
        title: title.trim(),
        description: description.trim() || null,
        priority,
      });
      onSaved(saved);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "Could not create task.");
    } finally { setBusy(false); }
  }

  return (
    <Modal title="Add a task" onClose={onClose}>
      <form className="modal-body form-stack" onSubmit={submit} noValidate>
        <p className="modal-context">Adding to <strong>{project.name}</strong></p>
        <Field id="new-task-title" label="Task title">
          <input id="new-task-title" autoFocus maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} required />
        </Field>
        <Field id="new-task-description" label="Description">
          <textarea id="new-task-description" rows={4} maxLength={2000} value={description} onChange={(event) => setDescription(event.target.value)} />
        </Field>
        <Field id="new-task-priority" label="Priority">
          <select id="new-task-priority" value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}>
            <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option>
          </select>
        </Field>
        {error && <p className="inline-error" role="alert">{error}</p>}
        <FormActions busy={busy} submitLabel="Create task" onCancel={onClose} />
      </form>
    </Modal>
  );
}
