const useEmulator = process.env.NEXT_PUBLIC_USE_FIREBASE_EMULATOR === 'true'
const apiKey = process.env.NEXT_PUBLIC_FIREBASE_API_KEY || 'demo-key'

function endpoint() {
  return useEmulator
    ? 'http://localhost:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword'
    : 'https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword'
}

function friendlyError(code: string | undefined): string {
  switch (code) {
    case 'EMAIL_NOT_FOUND':
    case 'INVALID_PASSWORD':
    case 'INVALID_LOGIN_CREDENTIALS':
      return 'Email o contraseña incorrectos'
    case 'USER_DISABLED':
      return 'Esta cuenta está desactivada'
    case 'TOO_MANY_ATTEMPTS_TRY_LATER':
      return 'Demasiados intentos. Espera un momento y vuelve a probar'
    case 'OPERATION_NOT_ALLOWED':
      return 'El acceso con email y contraseña no está activado en Firebase'
    default:
      return 'No se pudo iniciar sesión'
  }
}

export async function signInWithPassword(
  email: string,
  password: string
): Promise<string> {
  const response = await fetch(`${endpoint()}?key=${encodeURIComponent(apiKey)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: email.trim(),
      password,
      returnSecureToken: true
    })
  })
  const data = (await response.json().catch(() => null)) as {
    idToken?: string
    error?: { message?: string }
  } | null

  if (!response.ok || !data?.idToken) {
    throw new Error(friendlyError(data?.error?.message))
  }
  return data.idToken
}
