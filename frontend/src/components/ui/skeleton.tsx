import type * as React from 'react'
import { cn } from '@/lib/utils'

function Skeleton({ className, ...props }: React.ComponentProps<'div'>) {
  return <div className={cn('animate-pulse rounded-xl bg-navy-100/70', className)} aria-hidden {...props} />
}

export { Skeleton }
