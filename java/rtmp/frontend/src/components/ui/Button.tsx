import type { ButtonHTMLAttributes } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary'
}

export function Button({ variant = 'primary', className = '', ...props }: ButtonProps) {
  const classes = ['button', variant === 'secondary' ? 'button--secondary' : '', className]
    .filter(Boolean)
    .join(' ')
  return <button className={classes} {...props} />
}