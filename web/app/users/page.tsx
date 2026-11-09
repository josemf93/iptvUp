import Link from 'next/link'
import { AdminShell } from '@/components/AdminShell'
import { CreateUserForm } from '@/components/CreateUserForm'
import { formatDate, latestAccess } from '@/lib/access'
import { requireAdminPage } from '@/lib/auth'
import { getAdminAuth, getAdminDb } from '@/lib/firebase-admin'

export const dynamic = 'force-dynamic'

export default async function UsersPage() {
  const admin = await requireAdminPage()
  const list = await getAdminAuth().listUsers(1000)
  const db = getAdminDb()

  const [profileSnapshots, userDocs] = await Promise.all([
    Promise.all(
      list.users.map(async (user) => {
        try {
          const snapshot = await db
            .collection('users')
            .doc(user.uid)
            .collection('iptvProfiles')
            .get()
          return snapshot.size
        } catch {
          return 0
        }
      })
    ),
    db.collection('users').get()
  ])

  const lastSeenByUid = new Map<string, number>()
  userDocs.docs.forEach((doc) => {
    const seen = doc.get('lastSeenAt')
    if (typeof seen === 'number') lastSeenByUid.set(doc.id, seen)
  })

  const users = list.users
    .map((user, index) => ({
      uid: user.uid,
      email: user.email ?? user.uid,
      displayName: user.displayName,
      disabled: user.disabled,
      isAdmin: user.customClaims?.admin === true,
      lastAccess: latestAccess(
        user.metadata.lastSignInTime,
        lastSeenByUid.get(user.uid)
      ),
      createdAt: user.metadata.creationTime,
      profileCount: profileSnapshots[index]
    }))
    .sort((a, b) => a.email.localeCompare(b.email))

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

        <ul className="divide-y divide-white/5 md:hidden">
          {users.map((user) => (
            <li key={user.uid} className="space-y-3 px-4 py-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-medium">{user.email}</p>
                  <p className="truncate text-xs text-muted">
                    {user.displayName || 'Sin nombre'}
                  </p>
                </div>
                <Link
                  href={`/users/${user.uid}`}
                  className="shrink-0 rounded-lg border border-white/10 px-3 py-2 text-xs font-medium transition hover:border-primary/50 hover:text-primary"
                >
                  Gestionar
                </Link>
              </div>
              <div className="flex flex-wrap gap-2">
                <Badge tone={user.isAdmin ? 'primary' : 'neutral'}>
                  {user.isAdmin ? 'Admin' : 'Usuario'}
                </Badge>
                <Badge tone={user.disabled ? 'danger' : 'accent'}>
                  {user.disabled ? 'Desactivado' : 'Activo'}
                </Badge>
                <Badge tone="neutral">{user.profileCount} perfiles</Badge>
              </div>
              <dl className="grid grid-cols-2 gap-2 text-xs text-muted">
                <div>
                  <dt className="uppercase tracking-wide">Último acceso</dt>
                  <dd className="mt-0.5 text-white/80">
                    {formatDate(user.lastAccess)}
                  </dd>
                </div>
                <div>
                  <dt className="uppercase tracking-wide">Alta</dt>
                  <dd className="mt-0.5 text-white/80">
                    {formatDate(user.createdAt)}
                  </dd>
                </div>
              </dl>
            </li>
          ))}
          {users.length === 0 ? (
            <li className="px-5 py-10 text-center text-muted">
              No hay usuarios. Crea el primero con el formulario de arriba.
            </li>
          ) : null}
        </ul>

        <div className="hidden overflow-x-auto md:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-white/[0.03] text-xs uppercase tracking-wide text-muted">
              <tr>
                <th className="px-5 py-3">Usuario</th>
                <th className="px-5 py-3">Rol</th>
                <th className="px-5 py-3">Estado</th>
                <th className="px-5 py-3">Perfiles</th>
                <th className="whitespace-nowrap px-5 py-3">Último acceso</th>
                <th className="whitespace-nowrap px-5 py-3">Alta</th>
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
                  <td className="whitespace-nowrap px-5 py-4 text-muted">
                    {formatDate(user.lastAccess)}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-muted">
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
      className={`inline-flex whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium ${classes[tone]}`}
    >
      {children}
    </span>
  )
}
