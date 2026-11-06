import { Link, Outlet } from 'react-router'
import { useSiteMeta } from '@/api/meta'

export function Layout() {
  const { data } = useSiteMeta()
  return (
    <div className="mx-auto flex min-h-svh max-w-3xl flex-col px-4">
      <header className="flex items-center justify-between py-6">
        <Link to="/" className="text-lg font-semibold tracking-tight">
          {data?.siteName ?? 'Blog'}
        </Link>
        <nav className="flex gap-4 text-sm text-muted-foreground">
          <Link to="/">Posts</Link>
          <Link to="/projects">Projects</Link>
        </nav>
      </header>
      <main className="flex-1 py-6">
        <Outlet />
      </main>
      <footer className="py-6 text-xs text-muted-foreground">
        {data ? `v${data.version}` : null}
      </footer>
    </div>
  )
}
