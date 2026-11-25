import * as React from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

const Dialog = DialogPrimitive.Root
const DialogTrigger = DialogPrimitive.Trigger
const DialogClose = DialogPrimitive.Close

function DialogContent({ className, children, ...props }: React.ComponentProps<typeof DialogPrimitive.Content>) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="fixed inset-0 z-50 animate-fade-in bg-navy-950/50 backdrop-blur-[2px]" />
      <DialogPrimitive.Content
        className={cn(
          'fixed inset-x-3 bottom-3 z-50 max-h-[90dvh] overflow-y-auto rounded-2xl bg-white p-6 shadow-pop focus:outline-none sm:inset-x-auto sm:bottom-auto sm:left-1/2 sm:top-1/2 sm:w-full sm:max-w-lg sm:-translate-x-1/2 sm:-translate-y-1/2',
          className,
        )}
        {...props}
      >
        {children}
        <DialogPrimitive.Close
          className="absolute right-4 top-4 rounded-lg p-1.5 text-navy-500 transition-colors hover:bg-navy-50 hover:text-navy-900"
          aria-label="Close"
        >
          <X className="size-4" />
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  )
}
function DialogHeader({ className, ...props }: React.ComponentProps<'div'>) {
  return <div className={cn('mb-4 flex flex-col gap-1 pr-8', className)} {...props} />
}
function DialogTitle({ className, ...props }: React.ComponentProps<typeof DialogPrimitive.Title>) {
  return <DialogPrimitive.Title className={cn('font-display text-lg font-bold text-navy-900', className)} {...props} />
}
function DialogDescription({ className, ...props }: React.ComponentProps<typeof DialogPrimitive.Description>) {
  return <DialogPrimitive.Description className={cn('text-sm text-muted-foreground', className)} {...props} />
}
function DialogFooter({ className, ...props }: React.ComponentProps<'div'>) {
  return <div className={cn('mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end', className)} {...props} />
}

export { Dialog, DialogTrigger, DialogClose, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter }
