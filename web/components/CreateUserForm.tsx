'use client'

import { useActionState } from 'react'
import { createUserAction, type ActionState } from '@/app/actions/users'

const initialState: ActionState = { error: null }

export function CreateUserForm() {
  const [state, action, pending] = useActionState(
    createUserAction,
    initialState
  )

  return (
    <form
      action={action}
      className="space-y-4 rounded-2xl border border-white/10 bg-surface p-5"
    >
      <div>
        <h2 className="font-semibold">Crear usuario</h2>
        <p className="text-sm text-muted">
          La cuenta se crea directamente en Firebase Auth y en Firestore.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block">
          <span className="mb-1 block text-sm text-muted">Email</span>
          <input
            name="email"
            type="email"
            required
            className="w-full rounded-xl border border-white/10 bg-elevated px-4 py-3 outline-none focus:border-primary"
            placeholder="usuario@ejemplo.com"
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm text-muted">Nombre</span>
          <input
            name="displayName"
            className="w-full rounded-xl border border-white/10 bg-elevated px-4 py-3 outline-none focus:border-primary"
            placeholder="Nombre visible"
          />
        </label>
      </div>

      <label className="block">
        <span className="mb-1 block text-sm text-muted">
          Contraseña temporal
        </span>
        <input
          name="password"
          type="password"
          required
          minLength={8}
          className="w-full rounded-xl border border-white/10 bg-elevated px-4 py-3 outline-none focus:border-primary"
          placeholder="Mínimo 8 caracteres"
        />
      </label>

      <label className="flex items-center gap-2 text-sm text-muted">
        <input
          name="admin"
          type="checkbox"
          className="h-4 w-4 rounded border-white/20 bg-elevated"
        />
        Dar permisos de administrador
      </label>

      {state.error ? (
        <p className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">
          {state.error}
        </p>
      ) : null}

      <button
        type="submit"
        disabled={pending}
        className="rounded-xl bg-primary px-4 py-3 font-semibold text-white transition hover:brightness-110 disabled:opacity-60"
      >
        {pending ? 'Creando...' : 'Crear usuario'}
      </button>
    </form>
  )
}
