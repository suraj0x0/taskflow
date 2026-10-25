import { useEffect, useState, type FormEvent } from "react";
import { FolderKanban, MoreHorizontal, Plus, SquareArrowOutUpRight } from "lucide-react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/client";
import { projectsApi } from "../api/services";
import { useAuth } from "../auth/AuthContext";
import { EmptyState, ErrorNotice, Field, FormActions, Modal, PageHeader, Spinner, formatDate } from "../components/ui";
import type { Project } from "../types";

export function ProjectsPage() {
  const { user } = useAuth();
  const canManage = user?.role === "ADMIN" || user?.role === "MANAGER";
  const [projects, setProjects] = useState<Project[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [modal, setModal] = useState<null | { kind: "create" } | { kind: "edit"; project: Project }>(null);

  async function load() {
    setBusy(true);
    setError("");
    try { setProjects(await projectsApi.list()); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not load projects."); }
    finally { setBusy(false); }
  }

  useEffect(() => { void load(); }, []);

  async function remove(project: Project) {
    if (!window.confirm(`Delete “${project.name}”? Projects with tasks cannot be deleted.`)) return;
    try {
      await projectsApi.remove(project.id);
      setProjects((items) => items.filter((item) => item.id !== project.id));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not delete project.");
    }
  }

  return (
    <>
      <PageHeader eyebrow="WORKSPACE" title="Projects" description="Keep related tasks and team work organized."
        action={canManage && <button className="button button-primary" onClick={() => setModal({ kind: "create" })}><Plus size={17} /> New project</button>} />
      {error && <ErrorNotice message={error} onRetry={() => void load()} />}
      {busy ? <Spinner label="Loading projects" /> : projects.length ? (
        <section className="project-grid">
          {projects.map((project) => (
            <article className="project-card" key={project.id}>
              <div className="project-card-top">
                <span className="project-icon"><FolderKanban size={20} /></span>
                {canManage && <button className="icon-button project-menu" aria-label={`Project actions for ${project.name}`}
                  onClick={() => setModal({ kind: "edit", project })}><MoreHorizontal size={20} /></button>}
              </div>
              <Link to={`/projects/${project.id}`} className="project-card-title">{project.name}</Link>
              <p className="project-card-description">{project.description || "No description has been added."}</p>
              <div className="project-card-meta">
                <span>Created by {project.createdByName}</span>
                <span>{formatDate(project.createdAt)}</span>
              </div>
              <div className="project-card-footer">
                <Link className="text-link" to={`/projects/${project.id}`}>Open project <SquareArrowOutUpRight size={14} /></Link>
                {canManage && <button className="text-button danger-text" onClick={() => void remove(project)}>Delete</button>}
              </div>
            </article>
          ))}
        </section>
      ) : (
        <EmptyState title="No projects yet" description="Create the first project to give your team a place to plan and track work."
          icon={<FolderKanban size={24} />}
          action={canManage && <button className="button button-primary" onClick={() => setModal({ kind: "create" })}><Plus size={16} /> Create project</button>} />
      )}
      {modal && <ProjectFormModal mode={modal.kind} project={modal.kind === "edit" ? modal.project : undefined}
        onClose={() => setModal(null)} onSaved={(saved) => {
          setProjects((items) => modal.kind === "edit" ? items.map((item) => item.id === saved.id ? saved : item) : [saved, ...items]);
          setModal(null);
        }} />}
    </>
  );
}

export function ProjectFormModal({
  mode,
  project,
  onClose,
  onSaved,
}: {
  mode: "create" | "edit";
  project?: Project;
  onClose: () => void;
  onSaved: (project: Project) => void;
}) {
  const [name, setName] = useState(project?.name ?? "");
  const [description, setDescription] = useState(project?.description ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim()) { setError("Project name is required."); return; }
    if (name.trim().length > 150 || description.length > 1000) { setError("Check the project name and description length."); return; }
    if (mode === "edit" && project?.description && !description.trim()) {
      setError("The backend does not support clearing a saved description through this update endpoint.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const body = { name: name.trim(), description: description.trim() || null };
      const saved = mode === "create"
        ? await projectsApi.create(body)
        : await projectsApi.update(project!.id, { name: body.name, ...(body.description !== null ? { description: body.description } : {}) });
      onSaved(saved);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "Could not save project.");
    } finally { setBusy(false); }
  }

  return (
    <Modal title={mode === "create" ? "Create a project" : "Edit project"} onClose={onClose}>
      <form className="modal-body form-stack" onSubmit={submit} noValidate>
        <Field id="project-name" label="Project name">
          <input id="project-name" autoFocus maxLength={150} value={name} onChange={(event) => setName(event.target.value)} required />
        </Field>
        <Field id="project-description" label="Description">
          <textarea id="project-description" rows={4} maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)} />
          <small className="field-hint">{description.length}/1000 characters</small>
        </Field>
        {error && <p className="inline-error" role="alert">{error}</p>}
        <FormActions busy={busy} submitLabel={mode === "create" ? "Create project" : "Save changes"} onCancel={onClose} />
      </form>
    </Modal>
  );
}
