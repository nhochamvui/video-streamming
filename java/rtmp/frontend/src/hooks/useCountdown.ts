import { useEffect, useState } from 'react'

export function useCountdown(expiresAt: number | null): number {
  const [remaining, setRemaining] = useState(() =>
    expiresAt === null ? 0 : Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000)),
  )

  useEffect(() => {
    if (expiresAt === null) {
      setRemaining(0)
      return
    }
    const update = () => setRemaining(Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000)))
    update()
    const id = setInterval(update, 1000)
    return () => clearInterval(id)
  }, [expiresAt])

  return remaining
}

export function formatCountdown(remaining: number): string {
  const minutes = String(Math.floor(remaining / 60)).padStart(2, '0')
  const seconds = String(remaining % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}