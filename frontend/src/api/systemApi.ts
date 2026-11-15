import type { AiHealth, SystemHealth } from '../types/system'
import { apiClient } from './apiClient'

export const getSystemHealth = async (signal?: AbortSignal) => (await apiClient.get<SystemHealth>('/admin/system', { signal })).data

export const getAiHealth = async (signal?: AbortSignal) => (await apiClient.get<AiHealth>('/admin/system/ai', { signal })).data
