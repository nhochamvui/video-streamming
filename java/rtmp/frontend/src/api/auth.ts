import { apiFetch } from './client'

export interface AuthStatus {
  authenticated: boolean
  username: string | null
}

export interface LoginResponse {
  message: string
  username: string
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

export function logout(): Promise<{ message: string }> {
  return apiFetch<{ message: string }>('/api/v1/auth/logout', { method: 'POST' })
}

export function fetchAuthStatus(): Promise<AuthStatus> {
  return apiFetch<AuthStatus>('/api/v1/auth/me')
}