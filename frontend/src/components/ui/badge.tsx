import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva('inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap [&_svg]:size-3', {
  variants: {
    tone: {
      neutral: 'bg-navy-50 text-navy-700',
      navy: 'bg-navy-900 text-white',
      brand: 'bg-brand-100 text-brand-700',
      blue: 'bg-sky-100 text-sky-800',
      green: 'bg-emerald-100 text-emerald-800',
      amber: 'bg-amber-100 text-amber-800',
      red: 'bg-red-100 text-red-700',
      violet: 'bg-violet-100 text-violet-800',
      outline: 'border border-border bg-white text-navy-700',
    },
  },
  defaultVariants: { tone: 'neutral' },
})

function Badge({ className, tone, ...props }: React.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />
}

export { Badge }
