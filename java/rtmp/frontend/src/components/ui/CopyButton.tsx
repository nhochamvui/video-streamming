import { useState } from 'react'
import { Button } from './Button'

interface CopyButtonProps {
  value: string
  disabled?: boolean
}

export function CopyButton({ value, disabled }: CopyButtonProps) {
  const [copied, setCopied] = useState(false)

  const handleClick = async () => {
    if (!value) return
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      setTimeout(() => setCopied(false), 1200)
    } catch {
      // clipboard unavailable; ignore
    }
  }

  return (
    <Button variant="secondary" onClick={handleClick} disabled={disabled || !value}>
      {copied ? 'Copied' : 'Copy'}
    </Button>
  )
}