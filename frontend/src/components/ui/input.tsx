import * as React from 'react'
import { cn } from '@/lib/utils'

const field =
  'w-full rounded-xl border border-input bg-white px-3.5 text-sm text-foreground placeholder:text-slate-400 transition-colors focus-visible:border-brand-500 focus-visible:outline-2 focus-visible:outline-brand-500/30 disabled:cursor-not-allowed disabled:bg-navy-50 disabled:opacity-70 aria-[invalid=true]:border-destructive'

function Input({ className, type = 'text', ...props }: React.ComponentProps<'input'>) {
  return <input type={type} className={cn(field, 'h-11', className)} {...props} />
}

function Textarea({ className, ...props }: React.ComponentProps<'textarea'>) {
  return <textarea className={cn(field, 'min-h-24 py-3', className)} {...props} />
}

/** A native select keeps the best possible mobile and accessibility behaviour. */
function Select({ className, children, ...props }: React.ComponentProps<'select'>) {
  return (
    <select
      className={cn(field, 'h-11 appearance-none bg-[url("data:image/svg+xml;utf8,<svg xmlns=%27http://www.w3.org/2000/svg%27 width=%2716%27 height=%2716%27 viewBox=%270 0 24 24%27 fill=%27none%27 stroke=%27%235b6b86%27 stroke-width=%272.5%27 stroke-linecap=%27round%27 stroke-linejoin=%27round%27><path d=%27m6 9 6 6 6-6%27/></svg>")] bg-[length:16px] bg-[right_0.9rem_center] bg-no-repeat pr-9', className)}
      {...props}
    >
      {children}
    </select>
  )
}

export { Input, Textarea, Select }
