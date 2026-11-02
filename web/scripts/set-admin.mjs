import { applicationDefault, cert, initializeApp } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { getFirestore } from 'firebase-admin/firestore'

const email = process.argv[2]
if (!email) {
  console.error('Uso: npm run set-admin -- admin@ejemplo.com')
  process.exit(1)
}

const projectId = process.env.FIREBASE_PROJECT_ID
const clientEmail = process.env.FIREBASE_CLIENT_EMAIL
const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n')
const useEmulator = process.env.NEXT_PUBLIC_USE_FIREBASE_EMULATOR === 'true'

const app = useEmulator
  ? initializeApp({ projectId: projectId || 'iptvup-demo' })
  : process.env.GOOGLE_APPLICATION_CREDENTIALS
    ? initializeApp({ credential: applicationDefault(), projectId })
    : initializeApp({
        credential: cert({ projectId, clientEmail, privateKey })
      })

const auth = getAuth(app)
const db = getFirestore(app)

try {
  const user = await auth.getUserByEmail(email)
  await auth.setCustomUserClaims(user.uid, { admin: true })
  await db.collection('users').doc(user.uid).set(
    {
      email: user.email,
      displayName: user.displayName || null,
      role: 'admin',
      status: 'active',
      createdAt: Date.now()
    },
    { merge: true }
  )
  console.log(`Admin activado: ${email} (uid ${user.uid})`)
  console.log('Cierra sesión y vuelve a entrar para que el claim se refresque.')
  process.exit(0)
} catch (error) {
  console.error('No se pudo activar el admin:', error)
  process.exit(1)
}
