interface StatusMessageProps {
  message: string
  type?: 'ok' | 'error'
}

export function StatusMessage({ message, type }: StatusMessageProps) {
  if (!message) return null
  const classes = ['status', type ? `status--${type}` : ''].filter(Boolean).join(' ')
  return (
    <div className={classes} aria-live="polite">
      {message}
    </div>
  )
}