import Link from 'next/link'
import { AdminShell } from '@/components/AdminShell'
import { requireAdminPage } from '@/lib/auth'
import { getAdminAuth, getAdminDb } from '@/lib/firebase-admin'

export const dynamic = 'force-dynamic'

function formatDate(value: string | number | undefined | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })
}

export default async function DashboardPage() {
  const admin = await requireAdminPage()
  const list = await getAdminAuth().listUsers(1000)
  const db = getAdminDb()

  const profileCounts = await Promise.all(
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
  )
  const profileCount = profileCounts.reduce((total, count) => total + count, 0)
  const activeCount = list.users.filter((user) => !user.disabled).length
  const disabledCount = list.users.length - activeCount
  const recent = [...list.users]
    .filter((user) => user.metadata.lastSignInTime)
    .sort(
      (a, b) =>
        new Date(b.metadata.lastSignInTime ?? 0).getTime() -
        new Date(a.metadata.lastSignInTime ?? 0).getTime()
    )
    .slice(0, 5)

  return (
    <AdminShell email={admin.email ?? ''}>
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Dashboard</h1>
          <p className="mt-1 text-muted">
            Resumen de tu instalación privada de IptvUp.
          </p>
        </div>
        <Link
          href="/users"
          className="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white transition hover:brightness-110"
        >
          Gestionar usuarios
        </Link>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Usuarios" value={String(list.users.length)} />
        <StatCard label="Activos" value={String(activeCount)} />
        <StatCard label="Desactivados" value={String(disabledCount)} />
        <StatCard label="Perfiles IPTV" value={String(profileCount)} />
      </div>

      <section className="mt-8 rounded-2xl border border-white/10 bg-surface">
        <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
          <h2 className="font-semibold">Últimos accesos</h2>
          <Link href="/users" className="text-sm text-primary hover:underline">
            Ver todos
          </Link>
        </div>
        {recent.length === 0 ? (
          <p className="p-5 text-sm text-muted">
            Todavía no hay inicios de sesión registrados.
          </p>
        ) : (
          <div className="divide-y divide-white/5">
            {recent.map((user) => (
              <div
                key={user.uid}
                className="flex flex-wrap items-center justify-between gap-2 px-5 py-3"
              >
                <div>
                  <p className="text-sm font-medium">
                    {user.email ?? user.uid}
                  </p>
                  <p className="text-xs text-muted">
                    {user.displayName || 'Sin nombre'}
                  </p>
                </div>
                <div className="text-right text-xs text-muted">
                  <p>{formatDate(user.metadata.lastSignInTime)}</p>
                  <p>{user.disabled ? 'Desactivado' : 'Activo'}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <div className="mt-8 grid gap-4 md:grid-cols-2">
        <Link
          href="/users"
          className="rounded-2xl border border-white/10 bg-surface p-5 transition hover:border-primary/40"
        >
          <h2 className="font-semibold">Usuarios</h2>
          <p className="mt-1 text-sm text-muted">
            Crea cuentas, desactívalas, cambia contraseñas y revisa sus perfiles.
          </p>
        </Link>
        <a
          href="https://console.firebase.google.com"
          target="_blank"
          rel="noreferrer"
          className="rounded-2xl border border-white/10 bg-surface p-5 transition hover:border-primary/40"
        >
          <h2 className="font-semibold">Firebase</h2>
          <p className="mt-1 text-sm text-muted">
            Abre la consola para revisar Auth, Firestore y las reglas.
          </p>
        </a>
      </div>
    </AdminShell>
  )
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-white/10 bg-surface p-5">
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-2 text-3xl font-bold">{value}</p>
    </div>
  )
}
