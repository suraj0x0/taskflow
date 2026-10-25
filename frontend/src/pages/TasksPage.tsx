import { useEffect, useState, type FormEvent } from "react";
import { CheckSquare, Search, SlidersHorizontal } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { tasksApi } from "../api/services";
import { EmptyState, ErrorNotice, PageHeader, PriorityBadge, Spinner, StatusBadge, formatDate } from "../components/ui";
import type { Task, TaskPage, TaskPriority, TaskStatus } from "../types";

const blankPage: TaskPage = { content: [], number: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true };
const taskStatuses: TaskStatus[] = ["TODO", "IN_PROGRESS", "DONE"];
const taskPriorities: TaskPriority[] = ["LOW", "MEDIUM", "HIGH"];

function parseFilter<T extends string>(options: readonly T[], value: string | null): T | "" {
  return options.find((option) => option === value) ?? "";
}

function initialPageNumber(value: string | null): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
}

export function TasksPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [pageData, setPageData] = useState<TaskPage>(blankPage);
  const [query, setQuery] = useState(searchParams.get("q") ?? "");
  const [appliedQuery, setAppliedQuery] = useState(searchParams.get("q") ?? "");
  const [status, setStatus] = useState<TaskStatus | "">(parseFilter(taskStatuses, searchParams.get("status")));
  const [priority, setPriority] = useState<TaskPriority | "">(parseFilter(taskPriorities, searchParams.get("priority")));
  const [page, setPage] = useState(initialPageNumber(searchParams.get("page")));
  const [reload, setReload] = useState(0);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const urlQuery = searchParams.get("q") ?? "";
    if (urlQuery !== appliedQuery) {
      setQuery(urlQuery);
      setAppliedQuery(urlQuery);
      setPage(0);
    }
  }, [searchParams, appliedQuery]);

  useEffect(() => {
    let active = true;
    setBusy(true);
    setError("");
    void tasksApi.search({
        q: appliedQuery.trim() || undefined,
        status: status || undefined,
        priority: priority || undefined,
        page,
        size: 10,
        sortBy: "updatedAt",
        direction: "desc",
      })
      .then((results) => {
        if (!active) return;
        setPageData(results);
        const params = new URLSearchParams();
        if (appliedQuery.trim()) params.set("q", appliedQuery.trim());
        if (status) params.set("status", status);
        if (priority) params.set("priority", priority);
        if (page > 0) params.set("page", String(page));
        setSearchParams(params, { replace: true });
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : "Could not load tasks.");
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => { active = false; };
  }, [appliedQuery, status, priority, page, reload, setSearchParams]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (page !== 0) setPage(0);
    setAppliedQuery(query.trim());
  }

  const totalPages = pageData.totalPages;
  return (
    <>
      <PageHeader eyebrow="WORKSPACE" title="Tasks" description="Search and track work across your projects." />
      <form className="filter-bar" onSubmit={submit}>
        <label className="filter-search">
          <Search size={17} />
          <input aria-label="Search tasks" placeholder="Search task titles and descriptions" value={query} onChange={(event) => setQuery(event.target.value)} />
        </label>
        <label className="filter-select">
          <SlidersHorizontal size={15} />
          <select aria-label="Filter by status" value={status} onChange={(event) => { setStatus(parseFilter(taskStatuses, event.target.value)); setPage(0); }}>
            <option value="">All statuses</option><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="DONE">Done</option>
          </select>
        </label>
        <label className="filter-select">
          <select aria-label="Filter by priority" value={priority} onChange={(event) => { setPriority(parseFilter(taskPriorities, event.target.value)); setPage(0); }}>
            <option value="">All priorities</option><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option>
          </select>
        </label>
        <button className="button button-secondary" type="submit">Search</button>
      </form>
      {error && <ErrorNotice message={error} onRetry={() => setReload((value) => value + 1)} />}
      <div className="list-summary">{pageData.totalElements.toLocaleString()} {pageData.totalElements === 1 ? "task" : "tasks"}</div>
      {busy ? <Spinner label="Loading tasks" /> : pageData.content.length ? (
        <>
          <div className="surface-card task-table-card">
            <div className="task-table">
              <div className="task-table-header"><span>Task</span><span>Status</span><span>Priority</span><span>Assignee</span></div>
              {pageData.content.map((task) => <TaskListRow key={task.id} task={task} />)}
            </div>
          </div>
          <div className="pagination">
            <span>Page {pageData.number + 1} of {Math.max(totalPages, 1)}</span>
            <div className="button-row">
              <button className="button button-secondary button-small" disabled={pageData.first || busy} onClick={() => setPage((value) => Math.max(0, value - 1))}>Previous</button>
              <button className="button button-secondary button-small" disabled={pageData.last || busy} onClick={() => setPage((value) => value + 1)}>Next</button>
            </div>
          </div>
        </>
      ) : (
        <EmptyState title="No matching tasks" description="Try changing your filters, or start by creating a task from a project."
          icon={<CheckSquare size={23} />} action={<Link className="button button-secondary" to="/projects">Browse projects</Link>} />
      )}
    </>
  );
}

function TaskListRow({ task }: { task: Task }) {
  return (
    <Link className="task-table-row" to={`/tasks/${task.id}`}>
      <span className="table-task-cell">
        <span className="table-task-title"><CheckSquare size={16} />{task.title}</span>
        <small>{task.projectName} · Updated {formatDate(task.updatedAt)}</small>
      </span>
      <StatusBadge status={task.status} />
      <PriorityBadge priority={task.priority} />
      <span className="table-assignee">{task.assigneeName || "Unassigned"}</span>
    </Link>
  );
}
