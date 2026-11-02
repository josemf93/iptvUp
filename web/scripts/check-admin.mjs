import { applicationDefault, cert, initializeApp } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { getFirestore } from 'firebase-admin/firestore'

const email = process.argv[2]
if (!email) {
  console.error('Uso: npm run check-admin -- admin@ejemplo.com')
  process.exit(1)
}

const projectId = process.env.FIREBASE_PROJECT_ID
const useEmulator = process.env.NEXT_PUBLIC_USE_FIREBASE_EMULATOR === 'true'
const clientEmail = process.env.FIREBASE_CLIENT_EMAIL
const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n')

const app = useEmulator
  ? initializeApp({ projectId: projectId || 'iptvup-demo' })
  : process.env.GOOGLE_APPLICATION_CREDENTIALS
    ? initializeApp({ credential: applicationDefault(), projectId })
    : initializeApp({
        credential: cert({ projectId, clientEmail, privateKey })
      })

try {
  const auth = getAuth(app)
  const db = getFirestore(app)
  const user = await auth.getUserByEmail(email)
  const doc = await db.collection('users').doc(user.uid).get()

  console.log(
    JSON.stringify(
      {
        projectId,
        uid: user.uid,
        email: user.email,
        disabled: user.disabled,
        customClaims: user.customClaims,
        firestoreExists: doc.exists,
        firestoreRole: doc.get('role'),
        firestoreStatus: doc.get('status')
      },
      null,
      2
    )
  )
  process.exit(0)
} catch (error) {
  console.error('No se pudo comprobar el admin:', error)
  process.exit(1)
}
