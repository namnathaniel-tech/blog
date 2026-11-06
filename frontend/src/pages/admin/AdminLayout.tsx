import { NavLink, Navigate, Outlet, useNavigate } from 'react-router'
import { useLogout } from '@/api/auth'
import { useSiteMeta } from '@/api/meta'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/** Guards every /admin/* page (except /admin/login). The server enforces this too. */
export function AdminLayout() {
  const meta = useSiteMeta()
  const logout = useLogout()
  const navigate = useNavigate()

  if (meta.isPending) return <p className="text-sm text-muted-foreground">Loading…</p>
  if (!meta.data?.admin) return <Navigate to="/admin/login" replace />

  const link = ({ isActive }: { isActive: boolean }) =>
    cn('text-sm', isActive ? 'font-medium text-foreground' : 'text-muted-foreground hover:text-foreground')

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between border-b pb-3">
        <nav className="flex gap-4">
          <NavLink to="/admin" end className={link}>
            Dashboard
          </NavLink>
          <NavLink to="/admin/settings" className={link}>
            Settings
          </NavLink>
        </nav>
        <Button
          variant="outline"
          size="sm"
          disabled={logout.isPending}
          onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })}
        >
          Log out
        </Button>
      </div>
      <Outlet />
    </div>
  )
}
