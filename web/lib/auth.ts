import 'server-only'
import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import { getAdminAuth, getAdminDb } from './firebase-admin'

export class HttpError extends Error {
  constructor(
    public status: number,
    message: string
  ) {
    super(message)
  }
}

export async function getSessionUser() {
  const cookieStore = await cookies()
  const sessionCookie = cookieStore.get('__session')?.value
  if (!sessionCookie) return null
  try {
    return await getAdminAuth().verifySessionCookie(sessionCookie, true)
  } catch {
    return null
  }
}

export async function requireAdminPage() {
  const user = await getSessionUser()
  if (!user || user.admin !== true) {
    redirect('/login')
  }
  return user
}

export async function requireAdmin() {
  const user = await getSessionUser()
  if (!user || user.admin !== true) {
    throw new HttpError(403, 'No autorizado')
  }
  return user
}

export async function requireBearerUser(request: Request) {
  const header = request.headers.get('authorization') || ''
  if (!header.startsWith('Bearer ')) {
    throw new HttpError(401, 'Falta el token de sesión')
  }
  let decoded
  try {
    decoded = await getAdminAuth().verifyIdToken(header.slice(7), true)
  } catch {
    throw new HttpError(401, 'Token no válido o caducado')
  }

  const snapshot = await getAdminDb().collection('users').doc(decoded.uid).get()
  if (!snapshot.exists) {
    throw new HttpError(403, 'La cuenta no está dada de alta')
  }
  if (snapshot.get('status') === 'disabled') {
    throw new HttpError(403, 'La cuenta está desactivada')
  }
  return decoded
}
