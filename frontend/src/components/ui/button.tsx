import * as React from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

const buttonVariants = cva(
  'inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-xl text-sm font-semibold transition-all select-none disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98] [&_svg]:pointer-events-none [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground shadow-sm hover:bg-navy-800',
        brand: 'bg-brand-500 text-white shadow-sm shadow-brand-500/30 hover:bg-brand-600',
        outline: 'border border-input bg-white text-foreground hover:bg-navy-50 hover:border-navy-300',
        soft: 'bg-navy-50 text-navy-800 hover:bg-navy-100',
        softBrand: 'bg-brand-50 text-brand-700 hover:bg-brand-100',
        ghost: 'text-navy-700 hover:bg-navy-50',
        destructive: 'bg-destructive text-white hover:bg-red-700',
        link: 'text-brand-600 underline-offset-4 hover:underline px-0',
      },
      size: {
        sm: 'h-9 px-3.5 text-[13px] [&_svg]:size-4',
        default: 'h-11 px-5 [&_svg]:size-4.5',
        lg: 'h-13 px-7 text-base [&_svg]:size-5',
        icon: 'size-10 [&_svg]:size-5',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
)

export interface ButtonProps extends React.ComponentProps<'button'>, VariantProps<typeof buttonVariants> {
  asChild?: boolean
  loading?: boolean
}

function Button({ className, variant, size, asChild = false, loading = false, children, disabled, ...props }: ButtonProps) {
  const Comp = asChild ? Slot : 'button'
  return (
    <Comp className={cn(buttonVariants({ variant, size }), className)} disabled={disabled || loading} {...props}>
      {asChild ? (
        children
      ) : (
        <>
          {loading && <Loader2 className="animate-spin" aria-hidden />}
          {children}
        </>
      )}
    </Comp>
  )
}

export { Button, buttonVariants }
