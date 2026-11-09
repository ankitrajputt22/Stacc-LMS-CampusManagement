import type { TodoItem } from '../types/todo'
import { apiClient } from './apiClient'

/** The signed-in account's To-Do list; the backend decides each part from the roles and permissions held. */
export async function getTodo(signal?: AbortSignal): Promise<TodoItem[]> {
  return (await apiClient.get<TodoItem[]>('/me/todo', { signal })).data
}

export async function dismissTodo(key: string): Promise<void> {
  await apiClient.post('/me/todo/dismissals', { key })
}
