import type { SystemHealth } from '../types/system'
import { apiClient } from './apiClient'

export const getSystemHealth = async (signal?: AbortSignal) => (await apiClient.get<SystemHealth>('/admin/system', { signal })).data
