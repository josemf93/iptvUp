'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { signInWithPassword } from '@/lib/firebase-rest'

export default function LoginPage() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const idToken = await signInWithPassword(email, password)
      const response = await fetch('/api/auth/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken })
      })
      if (!response.ok) {
        const data = (await response.json().catch(() => null)) as {
          error?: string
        } | null
        throw new Error(data?.error || 'No autorizado')
      }
      router.replace('/')
      router.refresh()
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : 'No se pudo iniciar sesión'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="grid min-h-screen place-items-center px-4">
      <div className="w-full max-w-md rounded-3xl border border-white/10 bg-surface p-8 shadow-2xl">
        <div className="mb-8 flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-xl bg-primary text-xl text-white">
            ▶
          </span>
          <div>
            <h1 className="text-xl font-semibold">IptvUp Admin</h1>
            <p className="text-sm text-muted">Panel privado</p>
          </div>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block">
            <span className="mb-1 block text-sm text-muted">Email</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
              className="w-full rounded-xl border border-white/10 bg-elevated px-4 py-3 outline-none transition focus:border-primary"
              placeholder="admin@ejemplo.com"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm text-muted">Contraseña</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
              className="w-full rounded-xl border border-white/10 bg-elevated px-4 py-3 outline-none transition focus:border-primary"
              placeholder="••••••••"
            />
          </label>

          {error ? (
            <p className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">
              {error}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-xl bg-primary px-4 py-3 font-semibold text-white transition hover:brightness-110 disabled:opacity-60"
          >
            {loading ? 'Entrando...' : 'Entrar'}
          </button>
        </form>

        <p className="mt-6 text-center text-xs text-muted">
          Solo para administradores. Las cuentas de usuario se crean desde este
          panel.
        </p>
      </div>
    </main>
  )
}
