import { createBrowserRouter } from 'react-router'
import { Layout } from '@/components/layout/Layout'
import { HomePage } from '@/pages/HomePage'
import { NotFoundPage } from '@/pages/NotFoundPage'

/**
 * Route table. Keep in sync with SpaForwardingController (backend) — every
 * public path here must also be forwarded to index.html by Spring.
 * Planned: /posts/:slug, /projects, /projects/:slug, /tags/:tag, /search,
 *          /admin/login, /admin/** (guarded).
 */
export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
