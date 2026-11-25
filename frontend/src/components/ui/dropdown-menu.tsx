import * as React from 'react'
import * as DropdownPrimitive from '@radix-ui/react-dropdown-menu'
import { cn } from '@/lib/utils'

const DropdownMenu = DropdownPrimitive.Root
const DropdownMenuTrigger = DropdownPrimitive.Trigger

function DropdownMenuContent({ className, sideOffset = 8, ...props }: React.ComponentProps<typeof DropdownPrimitive.Content>) {
  return (
    <DropdownPrimitive.Portal>
      <DropdownPrimitive.Content
        sideOffset={sideOffset}
        className={cn('z-50 min-w-52 overflow-hidden rounded-xl border border-border bg-white p-1.5 shadow-pop', className)}
        {...props}
      />
    </DropdownPrimitive.Portal>
  )
}
function DropdownMenuItem({ className, ...props }: React.ComponentProps<typeof DropdownPrimitive.Item>) {
  return (
    <DropdownPrimitive.Item
      className={cn(
        'flex cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium text-navy-800 outline-none data-[highlighted]:bg-navy-50 [&_svg]:size-4 [&_svg]:text-navy-500',
        className,
      )}
      {...props}
    />
  )
}
function DropdownMenuLabel({ className, ...props }: React.ComponentProps<typeof DropdownPrimitive.Label>) {
  return <DropdownPrimitive.Label className={cn('px-3 py-2 text-xs font-semibold text-muted-foreground', className)} {...props} />
}
function DropdownMenuSeparator({ className, ...props }: React.ComponentProps<typeof DropdownPrimitive.Separator>) {
  return <DropdownPrimitive.Separator className={cn('my-1 h-px bg-border', className)} {...props} />
}

export { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator }
