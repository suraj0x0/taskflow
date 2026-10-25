import { useEffect, useState } from "react";
import {
  BrowserRouter,
  Navigate,
  Outlet,
  Route,
  Routes,
  Link,
  useNavigate,
  useLocation,
} from "react-router-dom";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { activitiesApi, notificationsApi, projectsApi, tasksApi, usersApi } from "./api/services";
import { Bell, CheckSquare, ChevronDown, FolderKanban, LayoutDashboard, LogOut, Menu, Search, Settings2, UsersRound, X } from "lucide-react";
import { ErrorNotice, Spinner } from "./components/ui";
import { LoginPage, RegisterPage } from "./pages/AuthPages";
import { DashboardPage } from "./pages/DashboardPage";
import { ProjectsPage } from "./pages/ProjectsPage";
import { ProjectDetailPage } from "./pages/ProjectDetailPage";
import { TasksPage } from "./pages/TasksPage";
import { TaskDetailPage } from "./pages/TaskDetailPage";
import { NotificationsPage } from "./pages/NotificationsPage";
import { ProfilePage } from "./pages/ProfilePage";
import { UsersPage } from "./pages/UsersPage";

export const appServices = {
  activitiesApi,
  notificationsApi,
  projectsApi,
  tasksApi,
  usersApi,
};

function ProtectedRoute() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Spinner label="Restoring your session" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

function AdminRoute() {
  const { user } = useAuth();
  if (user?.role !== "ADMIN") {
    return <div className="access-denied"><span className="eyebrow">ACCESS RESTRICTED</span><h1>Administrator access required</h1><p>Your account does not have permission to manage workspace members.</p><Link className="button button-secondary" to="/">Back to overview</Link></div>;
  }
  return <Outlet />;
}

function AppShell() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [searchText, setSearchText] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    setNotice("");
    notificationsApi.unreadCount()
      .then((count) => active && setUnreadCount(count))
      .catch(() => {
        if (active) setNotice("Notification count is temporarily unavailable.");
      });
    return () => {
      active = false;
    };
  }, [location.pathname]);

  useEffect(() => {
    const refresh = () => {
      void notificationsApi.unreadCount()
        .then(setUnreadCount)
        .catch((cause: unknown) => setNotice(cause instanceof Error ? cause.message : "Notification count is temporarily unavailable."));
    };
    window.addEventListener("taskflow:notifications-changed", refresh);
    return () => window.removeEventListener("taskflow:notifications-changed", refresh);
  }, []);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  function submitSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = searchText.trim();
    navigate(`/tasks${query ? `?q=${encodeURIComponent(query)}` : ""}`);
  }

  const navItems = [
    { to: "/", label: "Overview", icon: LayoutDashboard, exact: true },
    { to: "/projects", label: "Projects", icon: FolderKanban },
    { to: "/tasks", label: "Tasks", icon: CheckSquare },
    { to: "/notifications", label: "Notifications", icon: Bell, count: unreadCount },
  ];

  return (
    <div className="app-frame">
      {mobileOpen && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
      <aside className={`sidebar ${mobileOpen ? "sidebar-open" : ""}`}>
        <div className="brand">
          <div className="brand-mark"><CheckSquare size={20} strokeWidth={2.4} /></div>
          <span>taskflow</span>
          <button className="icon-button sidebar-close" aria-label="Close navigation" onClick={() => setMobileOpen(false)}><X size={18} /></button>
        </div>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="side-nav" aria-label="Main navigation">
          {navItems.map(({ to, label, icon: Icon, exact, count }) => {
            const active = exact ? location.pathname === to : location.pathname.startsWith(to);
            return (
              <Link key={to} className={`nav-link ${active ? "nav-link-active" : ""}`} to={to} aria-current={active ? "page" : undefined}>
                <Icon size={18} aria-hidden="true" />
                <span>{label}</span>
                {count ? <span className="nav-count">{count > 99 ? "99+" : count}</span> : null}
              </Link>
            );
          })}
        </nav>
        {user?.role === "ADMIN" && (
          <>
            <div className="workspace-label nav-spacer">ADMINISTRATION</div>
            <Link className={`nav-link ${location.pathname.startsWith("/users") ? "nav-link-active" : ""}`} to="/users" aria-current={location.pathname.startsWith("/users") ? "page" : undefined}>
              <UsersRound size={18} aria-hidden="true" /><span>People</span>
            </Link>
          </>
        )}
        <div className="sidebar-footer">
          <div className="sidebar-help-icon"><Settings2 size={17} /></div>
          <div><strong>Your workspace</strong><span>Projects and tasks</span></div>
        </div>
      </aside>

      <div className="main-column">
        <header className="topbar">
          <button className="icon-button mobile-menu" aria-label="Open navigation" onClick={() => setMobileOpen(true)}><Menu size={20} /></button>
          <form className="global-search" onSubmit={submitSearch} role="search">
            <Search size={17} aria-hidden="true" />
            <input aria-label="Search tasks" placeholder="Search tasks..." value={searchText} onChange={(event) => setSearchText(event.target.value)} />
            <kbd>↵</kbd>
          </form>
          <div className="topbar-actions">
            <Link className="icon-button notification-button" to="/notifications" aria-label={`Notifications${unreadCount ? `, ${unreadCount} unread` : ""}`}>
              <Bell size={19} />
              {unreadCount > 0 && <span className="notification-dot" />}
            </Link>
            <div className="profile-menu-wrap">
              <button className="profile-trigger" aria-expanded={profileOpen} onClick={() => setProfileOpen((open) => !open)}>
                <span className="avatar">{user?.name.slice(0, 1).toUpperCase()}</span>
                <span className="profile-trigger-text"><strong>{user?.name}</strong><small>{user?.role.toLowerCase()}</small></span>
                <ChevronDown size={16} />
              </button>
              {profileOpen && (
                <div className="profile-menu">
                  <Link to="/profile" onClick={() => setProfileOpen(false)}>Profile and password</Link>
                  <button onClick={() => { setProfileOpen(false); logout(); }}> <LogOut size={15} /> Sign out</button>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className="main-content">
          {notice && <ErrorNotice message={notice} />}
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function NotFoundPage() {
  return (
    <div className="not-found">
      <span className="eyebrow">404 · PAGE NOT FOUND</span>
      <h1>Nothing to see here.</h1>
      <p>The page may have moved or the address may be incorrect.</p>
      <Link className="button button-primary" to="/">Back to overview</Link>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppShell />}>
              <Route index element={<DashboardPage />} />
              <Route path="projects" element={<ProjectsPage />} />
              <Route path="projects/:id" element={<ProjectDetailPage />} />
              <Route path="tasks" element={<TasksPage />} />
              <Route path="tasks/:id" element={<TaskDetailPage />} />
              <Route path="notifications" element={<NotificationsPage />} />
              <Route path="profile" element={<ProfilePage />} />
              <Route element={<AdminRoute />}>
                <Route path="users" element={<UsersPage />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
