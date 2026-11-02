import 'server-only'
import {
  applicationDefault,
  cert,
  getApps,
  initializeApp,
  type App
} from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { getFirestore } from 'firebase-admin/firestore'

let cachedApp: App | null = null

function getAdminApp(): App {
  if (cachedApp) return cachedApp
  const existing = getApps()[0]
  if (existing) {
    cachedApp = existing
    return existing
  }

  const useEmulator = process.env.NEXT_PUBLIC_USE_FIREBASE_EMULATOR === 'true'
  if (useEmulator) {
    cachedApp = initializeApp({
      projectId:
        process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ||
        process.env.FIREBASE_PROJECT_ID ||
        'iptvup-demo'
    })
    return cachedApp
  }

  const projectId = process.env.FIREBASE_PROJECT_ID

  // Desarrollo local: permite apuntar al JSON de la cuenta de servicio.
  if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    cachedApp = initializeApp({
      credential: applicationDefault(),
      projectId
    })
    return cachedApp
  }

  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL
  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n')

  if (!projectId || !clientEmail || !privateKey) {
    throw new Error(
      'Configura GOOGLE_APPLICATION_CREDENTIALS o FIREBASE_PROJECT_ID + FIREBASE_CLIENT_EMAIL + FIREBASE_PRIVATE_KEY'
    )
  }

  cachedApp = initializeApp({
    credential: cert({ projectId, clientEmail, privateKey })
  })
  return cachedApp
}

export function getAdminAuth() {
  return getAuth(getAdminApp())
}

export function getAdminDb() {
  return getFirestore(getAdminApp())
}
