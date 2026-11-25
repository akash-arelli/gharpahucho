import * as React from 'react'
import * as ProgressPrimitive from '@radix-ui/react-progress'
import { cn } from '@/lib/utils'

function Progress({
  className,
  value,
  indicatorClassName,
  ...props
}: React.ComponentProps<typeof ProgressPrimitive.Root> & { indicatorClassName?: string }) {
  return (
    <ProgressPrimitive.Root className={cn('relative h-2 w-full overflow-hidden rounded-full bg-navy-100', className)} value={value} {...props}>
      <ProgressPrimitive.Indicator
        className={cn('h-full rounded-full bg-brand-500 transition-all', indicatorClassName)}
        style={{ width: `${value ?? 0}%` }}
      />
    </ProgressPrimitive.Root>
  )
}

export { Progress }
