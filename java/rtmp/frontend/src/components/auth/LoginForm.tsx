import { useState, type FormEvent } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { login } from '../../store/authSlice'
import { Button } from '../ui/Button'
import { StatusMessage } from '../ui/StatusMessage'

export function LoginForm() {
  const dispatch = useAppDispatch()
  const { status, error } = useAppSelector((state) => state.auth)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const busy = status === 'checking'

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (username.trim() && password) {
      void dispatch(login({ username: username.trim(), password }))
    }
  }

  return (
    <form className="login-form" onSubmit={handleSubmit}>
      <label className="login-form__label">
        <span>Username</span>
        <input
          type="text"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          autoComplete="username"
          autoFocus
          disabled={busy}
        />
      </label>
      <label className="login-form__label">
        <span>Password</span>
        <input
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          disabled={busy}
        />
      </label>
      <Button type="submit" disabled={busy || !username.trim() || !password}>
        {busy ? 'Signing in…' : 'Sign in'}
      </Button>
      <StatusMessage message={error} type="error" />
    </form>
  )
}