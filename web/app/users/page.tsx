import Link from 'next/link'
import { AdminShell } from '@/components/AdminShell'
import { CreateUserForm } from '@/components/CreateUserForm'
import { requireAdminPage } from '@/lib/auth'
import { getAdminAuth, getAdminDb } from '@/lib/firebase-admin'

export const dynamic = 'force-dynamic'

function formatDate(value: string | undefined | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })
}

export default async function UsersPage() {
  const admin = await requireAdminPage()
  const list = await getAdminAuth().listUsers(1000)
  const db = getAdminDb()

  const users = await Promise.all(
    list.users.map(async (user) => {
      let profileCount = 0
      try {
        const snapshot = await db
          .collection('users')
          .doc(user.uid)
          .collection('iptvProfiles')
          .get()
        profileCount = snapshot.size
      } catch {
        profileCount = 0
      }
      return {
        uid: user.uid,
        email: user.email ?? user.uid,
        displayName: user.displayName,
        disabled: user.disabled,
        isAdmin: user.customClaims?.admin === true,
        lastSignIn: user.metadata.lastSignInTime,
        createdAt: user.metadata.creationTime,
        profileCount
      }
    })
  )

  users.sort((a, b) => a.email.localeCompare(b.email))

  return (
    <AdminShell email={admin.email ?? ''}>
      <div className="mb-8">
        <h1 className="text-3xl font-bold">Usuarios</h1>
        <p className="mt-1 text-muted">
          Altas, bajas y control de acceso a la app.
        </p>
      </div>

      <CreateUserForm />

      <section className="mt-8 overflow-hidden rounded-2xl border border-white/10 bg-surface">
        <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
          <h2 className="font-semibold">Cuentas ({users.length})</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-white/[0.03] text-xs uppercase tracking-wide text-muted">
              <tr>
                <th className="px-5 py-3">Usuario</th>
                <th className="px-5 py-3">Rol</th>
                <th className="px-5 py-3">Estado</th>
                <th className="px-5 py-3">Perfiles</th>
                <th className="px-5 py-3">Último acceso</th>
                <th className="px-5 py-3">Alta</th>
                <th className="px-5 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {users.map((user) => (
                <tr key={user.uid} className="transition hover:bg-white/[0.02]">
                  <td className="px-5 py-4">
                    <p className="font-medium">{user.email}</p>
                    <p className="text-xs text-muted">
                      {user.displayName || 'Sin nombre'}
                    </p>
                  </td>
                  <td className="px-5 py-4">
                    <Badge tone={user.isAdmin ? 'primary' : 'neutral'}>
                      {user.isAdmin ? 'Admin' : 'Usuario'}
                    </Badge>
                  </td>
                  <td className="px-5 py-4">
                    <Badge tone={user.disabled ? 'danger' : 'accent'}>
                      {user.disabled ? 'Desactivado' : 'Activo'}
                    </Badge>
                  </td>
                  <td className="px-5 py-4">{user.profileCount}</td>
                  <td className="px-5 py-4 text-muted">
                    {formatDate(user.lastSignIn)}
                  </td>
                  <td className="px-5 py-4 text-muted">
                    {formatDate(user.createdAt)}
                  </td>
                  <td className="px-5 py-4 text-right">
                    <Link
                      href={`/users/${user.uid}`}
                      className="rounded-lg border border-white/10 px-3 py-2 text-xs font-medium transition hover:border-primary/50 hover:text-primary"
                    >
                      Gestionar
                    </Link>
                  </td>
                </tr>
              ))}
              {users.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-10 text-center text-muted">
                    No hay usuarios. Crea el primero con el formulario de arriba.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
    </AdminShell>
  )
}

function Badge({
  children,
  tone
}: {
  children: React.ReactNode
  tone: 'primary' | 'accent' | 'danger' | 'neutral'
}) {
  const classes = {
    primary: 'border-primary/30 bg-primary/10 text-primary',
    accent: 'border-accent/30 bg-accent/10 text-accent',
    danger: 'border-danger/30 bg-danger/10 text-danger',
    neutral: 'border-white/10 bg-white/5 text-muted'
  }
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-medium ${classes[tone]}`}
    >
      {children}
    </span>
  )
}
