'use client'

import { useActionState } from 'react'
import {
  deleteUserAction,
  resetPasswordAction,
  toggleAdminAction,
  toggleUserDisabledAction,
  type ActionState
} from '@/app/actions/users'

const initialState: ActionState = { error: null }

export function ResetPasswordForm({ uid }: { uid: string }) {
  const [state, action, pending] = useActionState(
    resetPasswordAction,
    initialState
  )

  return (
    <form
      action={action}
      className="space-y-3 rounded-2xl border border-white/10 bg-surface p-5"
    >
      <div>
        <h2 className="font-semibold">Cambiar contraseña</h2>
        <p className="text-sm text-muted">
          Asigna una contraseña temporal y comunícasela al usuario.
        </p>
      </div>
      <input type="hidden" name="uid" value={uid} />
      <input
        name="password"
        type="password"
        required
        minLength={8}
        className="w-full rounded-xl border border-white/10 bg-elevated px-4 py-3 outline-none focus:border-primary"
        placeholder="Nueva contraseña (mínimo 8)"
      />
      {state.error ? (
        <p className="text-sm text-danger">{state.error}</p>
      ) : null}
      {state.success ? (
        <p className="text-sm text-accent">{state.success}</p>
      ) : null}
      <button
        type="submit"
        disabled={pending}
        className="rounded-xl border border-white/10 px-4 py-2 text-sm font-medium transition hover:border-primary/50 hover:text-primary disabled:opacity-60"
      >
        {pending ? 'Guardando...' : 'Actualizar contraseña'}
      </button>
    </form>
  )
}

export function UserActions({
  uid,
  disabled,
  isAdmin,
  isSelf
}: {
  uid: string
  disabled: boolean
  isAdmin: boolean
  isSelf: boolean
}) {
  const [disableState, disableAction, disablePending] = useActionState(
    toggleUserDisabledAction,
    initialState
  )
  const [adminState, adminAction, adminPending] = useActionState(
    toggleAdminAction,
    initialState
  )
  const [deleteState, deleteAction, deletePending] = useActionState(
    deleteUserAction,
    initialState
  )

  return (
    <div className="space-y-4 rounded-2xl border border-white/10 bg-surface p-5">
      <div>
        <h2 className="font-semibold">Cuenta</h2>
        <p className="text-sm text-muted">
          Activa, desactiva o elimina el acceso del usuario.
        </p>
        <p className="mt-1 text-xs text-muted">
          Los cambios de rol se aplican cuando el usuario vuelve a iniciar
          sesión.
        </p>
      </div>

      <div className="flex flex-wrap gap-3">
        <form action={disableAction}>
          <input type="hidden" name="uid" value={uid} />
          <input
            type="hidden"
            name="disabled"
            value={disabled ? 'false' : 'true'}
          />
          <button
            type="submit"
            disabled={disablePending}
            className="rounded-xl border border-white/10 px-4 py-2 text-sm font-medium transition hover:border-primary/50 hover:text-primary disabled:opacity-60"
          >
            {disablePending
              ? 'Guardando...'
              : disabled
                ? 'Activar cuenta'
                : 'Desactivar cuenta'}
          </button>
        </form>

        <form action={adminAction}>
          <input type="hidden" name="uid" value={uid} />
          <input type="hidden" name="admin" value={isAdmin ? 'false' : 'true'} />
          <button
            type="submit"
            disabled={adminPending || isSelf}
            className="rounded-xl border border-white/10 px-4 py-2 text-sm font-medium transition hover:border-primary/50 hover:text-primary disabled:opacity-40"
          >
            {isAdmin ? 'Quitar admin' : 'Hacer admin'}
          </button>
        </form>
      </div>

      {disableState.error || adminState.error ? (
        <p className="text-sm text-danger">
          {disableState.error || adminState.error}
        </p>
      ) : null}
      {disableState.success || adminState.success ? (
        <p className="text-sm text-accent">
          {disableState.success || adminState.success}
        </p>
      ) : null}

      <div className="border-t border-white/10 pt-4">
        <p className="mb-3 text-sm text-muted">
          Borrar la cuenta elimina también sus perfiles, favoritos y datos
          sincronizados.
        </p>
        <form
          action={deleteAction}
          onSubmit={(event) => {
            if (
              !window.confirm(
                '¿Seguro que quieres borrar esta cuenta y todos sus datos?'
              )
            ) {
              event.preventDefault()
            }
          }}
        >
          <input type="hidden" name="uid" value={uid} />
          <button
            type="submit"
            disabled={deletePending || isSelf}
            className="rounded-xl border border-danger/30 px-4 py-2 text-sm font-medium text-danger transition hover:bg-danger/10 disabled:opacity-40"
          >
            {deletePending ? 'Borrando...' : 'Borrar cuenta'}
          </button>
        </form>
        {deleteState.error ? (
          <p className="mt-2 text-sm text-danger">{deleteState.error}</p>
        ) : null}
      </div>
    </div>
  )
}
