import { NextResponse } from 'next/server'
import { ZodError } from 'zod'
import { HttpError } from './auth'

export function apiError(error: unknown) {
  if (error instanceof HttpError) {
    return NextResponse.json({ error: error.message }, { status: error.status })
  }
  if (error instanceof ZodError) {
    return NextResponse.json(
      { error: error.issues[0]?.message ?? 'Datos no válidos' },
      { status: 400 }
    )
  }
  console.error(error)
  return NextResponse.json({ error: 'Error interno' }, { status: 500 })
}
