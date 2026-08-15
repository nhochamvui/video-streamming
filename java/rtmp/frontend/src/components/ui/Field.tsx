import type { ReactNode } from 'react'

interface FieldProps {
  label: string
  value: ReactNode
  action?: ReactNode
}

export function Field({ label, value, action }: FieldProps) {
  return (
    <div className="field">
      <span className="field__label">{label}</span>
      <code className="field__value">{value}</code>
      {action ? <span className="field__action">{action}</span> : null}
    </div>
  )
}