/**
 * Último acceso real de una cuenta: el más reciente entre el último inicio de sesión
 * (Firebase Auth) y la última actividad registrada por la app (`lastSeenAt` en Firestore, en ms).
 */
export function latestAccess(
  lastSignIn: string | null | undefined,
  lastSeenAt: number | null | undefined
): string | null {
  const times = [
    lastSignIn ? new Date(lastSignIn).getTime() : Number.NaN,
    typeof lastSeenAt === 'number' ? lastSeenAt : Number.NaN
  ].filter((time) => !Number.isNaN(time))
  if (times.length === 0) return null
  return new Date(Math.max(...times)).toISOString()
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })
}
