import { z } from 'zod'

export const profileSchema = z
  .object({
    name: z.string().trim().min(1, 'El nombre es obligatorio').max(80),
    kind: z.enum(['m3u', 'xtream']),
    m3uUrl: z.string().trim().url('URL M3U no válida').optional().or(z.literal('')),
    xtreamHost: z.string().trim().optional().or(z.literal('')),
    xtreamUsername: z.string().trim().optional().or(z.literal('')),
    xtreamPassword: z.string().optional().or(z.literal('')),
    epgUrl: z.string().trim().url('URL de EPG no válida').optional().or(z.literal(''))
  })
  .superRefine((data, ctx) => {
    if (data.kind === 'm3u' && !data.m3uUrl) {
      ctx.addIssue({
        code: 'custom',
        path: ['m3uUrl'],
        message: 'La URL M3U es obligatoria'
      })
    }
    if (data.kind === 'xtream') {
      if (!data.xtreamHost) {
        ctx.addIssue({
          code: 'custom',
          path: ['xtreamHost'],
          message: 'El host es obligatorio'
        })
      }
      if (!data.xtreamUsername) {
        ctx.addIssue({
          code: 'custom',
          path: ['xtreamUsername'],
          message: 'El usuario es obligatorio'
        })
      }
      if (!data.xtreamPassword) {
        ctx.addIssue({
          code: 'custom',
          path: ['xtreamPassword'],
          message: 'La contraseña es obligatoria'
        })
      }
    }
  })

export const createUserSchema = z.object({
  email: z.string().trim().email('Email no válido'),
  password: z.string().min(8, 'La contraseña debe tener al menos 8 caracteres'),
  displayName: z.string().trim().max(80).optional().or(z.literal('')),
  admin: z.coerce.boolean().optional()
})

export const resetPasswordSchema = z.object({
  password: z.string().min(8, 'La contraseña debe tener al menos 8 caracteres')
})
