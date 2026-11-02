import Link from 'next/link'
import { notFound } from 'next/navigation'
import { AdminShell } from '@/components/AdminShell'
import { ResetPasswordForm, UserActions } from '@/components/UserAdminForms'
import { requireAdminPage } from '@/lib/auth'
import { getAdminAuth, getAdminDb } from '@/lib/firebase-admin'

export const dynamic = 'force-dynamic'

function formatDate(value: string | number | undefined | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })
}

export default async function UserDetailPage({
  params
}: {
  params: Promise<{ uid: string }>
}) {
  const { uid } = await params
  const admin = await requireAdminPage()

  let user
  try {
    user = await getAdminAuth().getUser(uid)
  } catch {
    notFound()
  }

  const db = getAdminDb()
  const userDoc = await db.collection('users').doc(uid).get()
  const profiles = await db
    .collection('users')
    .doc(uid)
    .collection('iptvProfiles')
    .get()

  const isAdmin = user.customClaims?.admin === true
  const isSelf = admin.uid === uid

  return (
    <AdminShell email={admin.email ?? ''}>
      <div className="mb-6">
        <Link
          href="/users"
          className="text-sm text-muted transition hover:text-white"
        >
          ← Volver a usuarios
        </Link>
      </div>

      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">{user.email ?? user.uid}</h1>
          <p className="mt-1 text-muted">
            {user.displayName || 'Sin nombre visible'}
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Badge tone={user.disabled ? 'danger' : 'accent'}>
              {user.disabled ? 'Desactivado' : 'Activo'}
            </Badge>
            <Badge tone={isAdmin ? 'primary' : 'neutral'}>
              {isAdmin ? 'Administrador' : 'Usuario'}
            </Badge>
            {isSelf ? <Badge tone="neutral">Tu cuenta</Badge> : null}
          </div>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <InfoCard label="UID" value={user.uid} mono />
        <InfoCard label="Alta" value={formatDate(user.metadata.creationTime)} />
        <InfoCard
          label="Último acceso"
          value={formatDate(user.metadata.lastSignInTime)}
        />
        <InfoCard
          label="Estado en Firestore"
          value={String(userDoc.get('status') ?? '—')}
        />
      </div>

      <section className="mt-8 overflow-hidden rounded-2xl border border-white/10 bg-surface">
        <div className="border-b border-white/10 px-5 py-4">
          <h2 className="font-semibold">Perfiles IPTV ({profiles.size})</h2>
          <p className="text-sm text-muted">
            Solo se muestran metadatos. Las credenciales están cifradas.
          </p>
        </div>
        {profiles.empty ? (
          <p className="p-5 text-sm text-muted">
            Este usuario todavía no ha creado ningún perfil.
          </p>
        ) : (
          <div className="divide-y divide-white/5">
            {profiles.docs.map((profile) => (
              <div
                key={profile.id}
                className="flex flex-wrap items-center justify-between gap-2 px-5 py-4"
              >
                <div>
                  <p className="font-medium">
                    {profile.get('name') ?? profile.id}
                  </p>
                  <p className="text-xs text-muted">{profile.id}</p>
                </div>
                <div className="flex items-center gap-3 text-sm">
                  <Badge tone="neutral">
                    {profile.get('kind') === 'xtream'
                      ? 'Xtream Codes'
                      : 'Lista M3U'}
                  </Badge>
                  <span className="text-xs text-muted">
                    {formatDate(profile.get('updatedAt'))}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <div className="mt-8 grid gap-4 lg:grid-cols-2">
        <ResetPasswordForm uid={uid} />
        <UserActions
          uid={uid}
          disabled={user.disabled}
          isAdmin={isAdmin}
          isSelf={isSelf}
        />
      </div>
    </AdminShell>
  )
}

function InfoCard({
  label,
  value,
  mono = false
}: {
  label: string
  value: string
  mono?: boolean
}) {
  return (
    <div className="rounded-2xl border border-white/10 bg-surface p-5">
      <p className="text-xs uppercase tracking-wide text-muted">{label}</p>
      <p
        className={`mt-2 truncate text-sm ${mono ? 'font-mono text-xs' : 'font-medium'}`}
        title={value}
      >
        {value}
      </p>
    </div>
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
