import { apiRequest } from "./client";
import type {
  Activity,
  Comment,
  LoginResponse,
  Notification,
  Project,
  ProjectInput,
  RegisterResponse,
  Role,
  Task,
  TaskInput,
  TaskPage,
  TaskSearch,
  TaskStatus,
  User,
} from "../types";

function queryString(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  const query = params.toString();
  return query ? `?${query}` : "";
}

export const authApi = {
  login: (email: string, password: string) =>
    apiRequest<LoginResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  register: (name: string, email: string, password: string) =>
    apiRequest<RegisterResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ name, email, password }),
    }),
  me: () => apiRequest<User>("/api/auth/me"),
};

export const usersApi = {
  me: () => apiRequest<User>("/api/users/me"),
  updateMe: (body: { name?: string; email?: string }) =>
    apiRequest<User>("/api/users/me", { method: "PATCH", body: JSON.stringify(body) }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<User>("/api/users/me/password", {
      method: "PATCH",
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
  list: () => apiRequest<User[]>("/api/users"),
  create: (body: { name: string; email: string; password: string; role: Role }) =>
    apiRequest<User>("/api/users", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: { name?: string; email?: string }) =>
    apiRequest<User>(`/api/users/${id}`, { method: "PATCH", body: JSON.stringify(body) }),
  updateRole: (id: string, role: Role) =>
    apiRequest<User>(`/api/users/${id}/role`, {
      method: "PATCH",
      body: JSON.stringify({ role }),
    }),
  remove: (id: string) => apiRequest<void>(`/api/users/${id}`, { method: "DELETE" }),
};

export const projectsApi = {
  list: () => apiRequest<Project[]>("/api/projects"),
  get: (id: string) => apiRequest<Project>(`/api/projects/${id}`),
  create: (body: ProjectInput) =>
    apiRequest<Project>("/api/projects", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: Partial<ProjectInput>) =>
    apiRequest<Project>(`/api/projects/${id}`, { method: "PATCH", body: JSON.stringify(body) }),
  remove: (id: string) => apiRequest<void>(`/api/projects/${id}`, { method: "DELETE" }),
};

export const tasksApi = {
  listByProject: (projectId: string) =>
    apiRequest<Task[]>(`/api/projects/${projectId}/tasks`),
  get: (id: string) => apiRequest<Task>(`/api/tasks/${id}`),
  search: (filters: TaskSearch) =>
    apiRequest<TaskPage>(`/api/tasks/search${queryString(filters as Record<string, string | number | undefined>)}`),
  create: (projectId: string, body: TaskInput) =>
    apiRequest<Task>(`/api/projects/${projectId}/tasks`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  update: (
    id: string,
    body: Partial<TaskInput> & { status?: TaskStatus },
  ) => apiRequest<Task>(`/api/tasks/${id}`, { method: "PATCH", body: JSON.stringify(body) }),
  assign: (id: string, workerId: string) =>
    apiRequest<Task>(`/api/tasks/${id}/assignee`, {
      method: "PATCH",
      body: JSON.stringify({ workerId }),
    }),
  updateStatus: (id: string, status: TaskStatus) =>
    apiRequest<Task>(`/api/tasks/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
  remove: (id: string) => apiRequest<void>(`/api/tasks/${id}`, { method: "DELETE" }),
};

export const commentsApi = {
  list: (taskId: string) => apiRequest<Comment[]>(`/api/tasks/${taskId}/comments`),
  create: (taskId: string, content: string) =>
    apiRequest<Comment>(`/api/tasks/${taskId}/comments`, {
      method: "POST",
      body: JSON.stringify({ content }),
    }),
  update: (id: string, content: string) =>
    apiRequest<Comment>(`/api/comments/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ content }),
    }),
  remove: (id: string) => apiRequest<void>(`/api/comments/${id}`, { method: "DELETE" }),
};

export const activitiesApi = {
  list: (taskId: string) =>
    apiRequest<Activity[]>(`/api/tasks/${taskId}/activities`),
};

export const notificationsApi = {
  list: () => apiRequest<Notification[]>("/api/notifications"),
  unreadCount: async () =>
    (await apiRequest<{ unreadCount: number }>("/api/notifications/unread-count")).unreadCount,
  markRead: (id: string) =>
    apiRequest<Notification>(`/api/notifications/${id}/read`, { method: "PATCH" }),
  markAllRead: () => apiRequest<void>("/api/notifications/read-all", { method: "PATCH" }),
};
