import { useEffect, useState } from "react";
import { ArrowRight, CheckCircle2, CircleDashed, Clock3, FolderKanban, ListTodo, Plus } from "lucide-react";
import { Link } from "react-router-dom";
import { projectsApi, tasksApi } from "../api/services";
import { useAuth } from "../auth/AuthContext";
import { EmptyState, ErrorNotice, PageHeader, PriorityBadge, Spinner, StatusBadge, formatDate } from "../components/ui";
import type { Project, Task, TaskStatus } from "../types";

interface DashboardData {
  projects: Project[];
  tasks: Task[];
  counts: Record<TaskStatus, number>;
}

export function DashboardPage() {
  const { user } = useAuth();
  const canManage = user?.role === "ADMIN" || user?.role === "MANAGER";
  const [data, setData] = useState<DashboardData | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  async function load() {
    setBusy(true);
    setError("");
    try {
      const [projects, recent, todo, inProgress, done] = await Promise.all([
        projectsApi.list(),
        tasksApi.search({ page: 0, size: 5, sortBy: "updatedAt", direction: "desc" }),
        tasksApi.search({ page: 0, size: 1, status: "TODO" }),
        tasksApi.search({ page: 0, size: 1, status: "IN_PROGRESS" }),
        tasksApi.search({ page: 0, size: 1, status: "DONE" }),
      ]);
      setData({
        projects,
        tasks: recent.content,
        counts: { TODO: todo.totalElements, IN_PROGRESS: inProgress.totalElements, DONE: done.totalElements },
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load the dashboard.");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void load(); }, []);

  const totalTasks = data ? Object.values(data.counts).reduce((sum, count) => sum + count, 0) : 0;
  if (busy && !data) return <Spinner label="Loading your workspace" />;
  if (error && !data) return <ErrorNotice message={error} onRetry={() => void load()} />;

  return (
    <>
      <PageHeader eyebrow="YOUR WORKSPACE" title="Good work starts here." description="A clear view of your team’s projects and tasks."
        action={canManage && <Link className="button button-primary" to="/projects"><Plus size={17} /> New project</Link>} />
      {error && <ErrorNotice message={error} onRetry={() => void load()} />}

      <section className="summary-grid" aria-label="Task summary">
        <SummaryCard label="All tasks" value={totalTasks} icon={<ListTodo size={19} />} tone="teal" />
        <SummaryCard label="To do" value={data?.counts.TODO ?? 0} icon={<CircleDashed size={19} />} tone="blue" />
        <SummaryCard label="In progress" value={data?.counts.IN_PROGRESS ?? 0} icon={<Clock3 size={19} />} tone="amber" />
        <SummaryCard label="Completed" value={data?.counts.DONE ?? 0} icon={<CheckCircle2 size={19} />} tone="green" />
      </section>

      <section className="dashboard-columns">
        <div className="surface-card">
          <div className="section-heading">
            <div><span className="eyebrow">KEEP THINGS MOVING</span><h2>Recent tasks</h2></div>
            <Link className="text-link" to="/tasks">View all <ArrowRight size={15} /></Link>
          </div>
          {data?.tasks.length ? (
            <div className="task-list">
              {data.tasks.map((task) => <TaskRow key={task.id} task={task} />)}
            </div>
          ) : (
            <EmptyState title="No tasks yet" description="Tasks from all your projects will appear here."
              icon={<CheckCircle2 size={23} />} action={<Link className="button button-secondary" to="/projects">Browse projects</Link>} />
          )}
        </div>

        <div className="surface-card projects-preview">
          <div className="section-heading">
            <div><span className="eyebrow">TEAM WORK</span><h2>Projects</h2></div>
            <Link className="text-link" to="/projects">View all <ArrowRight size={15} /></Link>
          </div>
          {data?.projects.length ? (
            <div className="project-preview-list">
              {data.projects.slice(0, 4).map((project) => (
                <Link className="project-preview" to={`/projects/${project.id}`} key={project.id}>
                  <span className="project-mini-icon"><FolderKanban size={18} /></span>
                  <span className="project-preview-copy"><strong>{project.name}</strong><small>{project.description || "No description"}</small></span>
                  <ArrowRight size={15} className="muted-icon" />
                </Link>
              ))}
            </div>
          ) : (
            <EmptyState title="Start with a project" description="Create a project to bring related work together."
              icon={<FolderKanban size={23} />} action={canManage && <Link className="button button-primary" to="/projects">Create project</Link>} />
          )}
        </div>
      </section>
    </>
  );
}

function SummaryCard({ label, value, icon, tone }: { label: string; value: number; icon: React.ReactNode; tone: string }) {
  return (
    <div className="summary-card">
      <div className={`summary-icon tone-${tone}`}>{icon}</div>
      <span className="summary-label">{label}</span>
      <strong className="summary-value">{value.toLocaleString()}</strong>
      <span className="summary-caption">from live task data</span>
    </div>
  );
}

function TaskRow({ task }: { task: Task }) {
  return (
    <Link className="task-row" to={`/tasks/${task.id}`}>
      <span className="task-row-main">
        <strong>{task.title}</strong>
        <small>{task.projectName} · Updated {formatDate(task.updatedAt)}</small>
      </span>
      <span className="task-row-badges"><StatusBadge status={task.status} /><PriorityBadge priority={task.priority} /></span>
      <span className="assignee-cell">{task.assigneeName || "Unassigned"}</span>
    </Link>
  );
}
