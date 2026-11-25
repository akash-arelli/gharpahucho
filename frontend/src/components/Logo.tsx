import { Link } from 'react-router-dom'
import { cn } from '@/lib/utils'

export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={cn('size-9', className)} aria-hidden>
      <rect width="64" height="64" rx="16" fill="#0b1f3a" />
      <path d="M14 33 32 17l18 16v15a2 2 0 0 1-2 2H16a2 2 0 0 1-2-2V33Z" fill="#fff" />
      <path d="M26 50V37a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v13H26Z" fill="#f97316" />
      <circle cx="32" cy="12" r="3.5" fill="#f97316" />
    </svg>
  )
}

export function Logo({ to = '/', light = false, className }: { to?: string; light?: boolean; className?: string }) {
  return (
    <Link to={to} className={cn('inline-flex items-center gap-2.5', className)} aria-label="GharPahucho home">
      <LogoMark />
      <span className={cn('font-display text-lg font-extrabold tracking-tight', light ? 'text-white' : 'text-navy-900')}>
        Ghar<span className="text-brand-500">Pahucho</span>
      </span>
    </Link>
  )
}
