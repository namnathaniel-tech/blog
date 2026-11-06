import { createBrowserRouter } from 'react-router'
import { Layout } from '@/components/layout/Layout'
import { AdminLayout } from '@/pages/admin/AdminLayout'
import { DashboardPage } from '@/pages/admin/DashboardPage'
import { LoginPage } from '@/pages/admin/LoginPage'
import { SettingsPage } from '@/pages/admin/SettingsPage'
import { HomePage } from '@/pages/HomePage'
import { NotFoundPage } from '@/pages/NotFoundPage'

/**
 * Route table. Keep in sync with SpaIndexController (backend) — every
 * public path here must also be forwarded to index.html by Spring.
 * Planned: /posts/:slug, /projects, /projects/:slug, /tags/:tag, /search.
 */
export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'admin/login', element: <LoginPage /> },
      {
        path: 'admin',
        element: <AdminLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'settings', element: <SettingsPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
