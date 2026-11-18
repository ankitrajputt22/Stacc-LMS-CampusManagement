import type { TodoItem } from '../types/todo'
import { apiClient } from './apiClient'

export async function getTodo(signal?: AbortSignal): Promise<TodoItem[]> {
  return (await apiClient.get<TodoItem[]>('/me/todo', { signal })).data
}

export async function dismissTodo(key: string): Promise<void> {
  await apiClient.post('/me/todo/dismissals', { key })
}
