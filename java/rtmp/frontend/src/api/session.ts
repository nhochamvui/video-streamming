import { apiFetch } from './client'
import type { StreamSession } from './types'

export function createStreamSession(): Promise<StreamSession> {
  return apiFetch<StreamSession>('/api/v1/stream-sessions', { method: 'POST' })
}