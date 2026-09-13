import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * TODO API（/api/user/todos）。親タスクと子タスクは同じ表（親TODOID の自己参照）。
 */
export type TodoStatusCode = 'TODO' | 'DOING' | 'DONE'
export type TodoPriorityCode = 'HIGH' | 'NORMAL' | 'LOW'

export interface TodoRow {
  todoId: number
  parentTodoId: number | null
  order: number
  title: string
  memo: string | null
  status: TodoStatusCode
  priority: TodoPriorityCode
  dueDate: string | null
  startedAt: string | null
  completedAt: string | null
  createdByName: string | null
  childCount: number
  doneChildCount: number
  version: number
  children: TodoRow[]
}

export interface TodoListResult {
  items: TodoRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
  openCount: number
  doingCount: number
  doneCount: number
}

export interface TodoCalendarCell {
  dueDate: string
  openCount: number
  doneCount: number
}

export interface TodoCalendarResult {
  year: number
  month: number
  cells: TodoCalendarCell[]
}

export interface TodoChildRequest {
  title: string
  memo?: string | null
  priority?: string
  dueDate?: string | null
}

export interface TodoSaveRequest {
  title: string
  memo?: string | null
  priority?: string
  dueDate?: string | null
  children?: TodoChildRequest[]
  alsoForStudent?: boolean | null
  version?: number | null
}

export interface TodoMutationResult {
  message: string
  todoId: number | null
  updatedCount: number
}

const http = new HttpClient({ baseUrl: '/api/user' })

export function searchTodos(params: {
  keyword?: string
  status?: string
  priority?: string
  includeDone?: boolean
  dueFrom?: string
  dueTo?: string
  page?: number
  size?: number
}): Promise<ApiResponse<TodoListResult>> {
  return http.get<TodoListResult>('/todos', { params })
}

export function fetchTodoCalendar(year: number, month: number): Promise<ApiResponse<TodoCalendarResult>> {
  return http.get<TodoCalendarResult>('/todos/calendar', { params: { year, month } })
}

export function createTodo(body: TodoSaveRequest): Promise<ApiResponse<TodoMutationResult>> {
  return http.post<TodoMutationResult>('/todos', { body })
}

export function updateTodo(todoId: number, body: TodoSaveRequest): Promise<ApiResponse<TodoMutationResult>> {
  return http.put<TodoMutationResult>(`/todos/${todoId}`, { body })
}

export function deleteTodo(todoId: number): Promise<ApiResponse<TodoMutationResult>> {
  return http.delete<TodoMutationResult>(`/todos/${todoId}`)
}

/**
 * 状態を変える（未着手 → 進行中 → 完了 → 未着手）。
 * `version` は一覧が持っているバージョン。渡すと楽観ロックが効き、
 * 別の画面で先に更新されていた場合は 409（再読み込みを促す）になる。
 */
export function changeTodoStatus(
  todoId: number,
  status: TodoStatusCode,
  version: number | null = null
): Promise<ApiResponse<TodoMutationResult>> {
  return http.post<TodoMutationResult>(`/todos/${todoId}/status`, { body: { status, version } })
}
