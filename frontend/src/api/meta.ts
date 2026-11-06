import { useQuery } from '@tanstack/react-query'
import { api } from './client'

/** GET /api/public/meta — site metadata + server status. Phase 0 smoke endpoint. */
export interface SiteMeta {
  siteName: string
  version: string
  /** true when the current browser has an authenticated admin session. */
  admin: boolean
}

export const metaKeys = { all: ['meta'] as const }

export function useSiteMeta() {
  return useQuery({ queryKey: metaKeys.all, queryFn: () => api<SiteMeta>('/public/meta') })
}
