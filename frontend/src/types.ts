export type Role = "ADMIN" | "MANAGER" | "WORKER";
export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH";
export type ActivityType =
  | "TASK_CREATED"
  | "TASK_UPDATED"
  | "TASK_ASSIGNED"
  | "TASK_STATUS_CHANGED"
  | "TASK_DELETED"
  | "COMMENT_CREATED"
  | "COMMENT_UPDATED"
  | "COMMENT_DELETED";
export type NotificationType = "TASK_ASSIGNED" | "TASK_STATUS_CHANGED";

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
}

export interface LoginResponse extends User {
  token: string;
}

export interface RegisterResponse extends User {
  createdAt: string;
}

export interface Project {
  id: string;
  name: string;
  description: string | null;
  createdById: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
}

export interface Task {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  projectId: string;
  projectName: string;
  createdById: string;
  createdByName: string;
  assigneeId: string | null;
  assigneeName: string | null;
  assigneeEmail: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskPage {
  content: Task[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Comment {
  id: string;
  content: string;
  taskId: string;
  authorId: string;
  authorName: string;
  authorEmail: string;
  createdAt: string;
  updatedAt: string;
}

export interface Activity {
  id: string;
  taskId: string;
  actorId: string;
  actorName: string;
  type: ActivityType;
  description: string;
  createdAt: string;
}

export interface Notification {
  id: string;
  message: string;
  type: NotificationType;
  taskId: string | null;
  read: boolean;
  createdAt: string;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  errors?: Record<string, string>;
  path?: string;
}

export interface TaskSearch {
  q?: string;
  projectId?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  assigneeId?: string;
  sortBy?: "createdAt" | "updatedAt" | "title" | "priority" | "status";
  direction?: "asc" | "desc";
  page?: number;
  size?: number;
}

export interface ProjectInput {
  name: string;
  description: string | null;
}

export interface TaskInput {
  title: string;
  description: string | null;
  priority: TaskPriority;
}
