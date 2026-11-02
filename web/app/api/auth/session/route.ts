import { NextResponse } from 'next/server'
import { z } from 'zod'
import { getAdminAuth, getAdminDb } from '@/lib/firebase-admin'

export const runtime = 'nodejs'

const schema = z.object({
  idToken: z.string().min(1)
})

export async function POST(request: Request) {
  try {
    const { idToken } = schema.parse(await request.json())
    const auth = getAdminAuth()
    const decoded = await auth.verifyIdToken(idToken)
    const isAdmin = (decoded as unknown as { admin?: boolean }).admin === true
    if (!isAdmin) {
      return NextResponse.json({ error: 'No autorizado' }, { status: 403 })
    }

    const userDoc = await getAdminDb().collection('users').doc(decoded.uid).get()
    if (!userDoc.exists || userDoc.get('status') === 'disabled') {
      return NextResponse.json(
        { error: 'La cuenta no está activa' },
        { status: 403 }
      )
    }

    const expiresIn = 5 * 24 * 60 * 60 * 1000
    const sessionCookie = await auth.createSessionCookie(idToken, { expiresIn })
    const response = NextResponse.json({ ok: true })
    response.cookies.set('__session', sessionCookie, {
      maxAge: expiresIn / 1000,
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/'
    })
    return response
  } catch (error) {
    console.error(error)
    return NextResponse.json(
      { error: 'No se pudo iniciar sesión' },
      { status: 401 }
    )
  }
}

export async function DELETE() {
  const response = NextResponse.json({ ok: true })
  response.cookies.set('__session', '', {
    maxAge: 0,
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/'
  })
  return response
}
