import Link from 'next/link'
import { logoutAction } from '@/app/actions/auth'

export function AdminShell({
  email,
  children
}: {
  email: string
  children: React.ReactNode
}) {
  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-20 border-b border-white/10 bg-surface/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-5 px-4 py-3">
          <Link href="/" className="flex items-center gap-2 font-semibold">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-primary text-white">
              ▶
            </span>
            <span>IptvUp Admin</span>
          </Link>
          <nav className="flex items-center gap-1 text-sm text-muted">
            <Link
              href="/"
              className="rounded-lg px-3 py-2 transition hover:bg-white/5 hover:text-white"
            >
              Dashboard
            </Link>
            <Link
              href="/users"
              className="rounded-lg px-3 py-2 transition hover:bg-white/5 hover:text-white"
            >
              Usuarios
            </Link>
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm">
            <span className="hidden text-muted sm:inline">{email}</span>
            <form action={logoutAction}>
              <button
                type="submit"
                className="rounded-lg border border-white/10 px-3 py-2 text-muted transition hover:border-danger/40 hover:text-danger"
              >
                Salir
              </button>
            </form>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
    </div>
  )
}
