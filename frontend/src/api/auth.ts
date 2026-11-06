import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import { metaKeys } from './meta'

/** Mirrors backend dev.blog.auth.AuthDtos. */
export interface MessageResponse {
  message: string
}
export interface RecoveryStatus {
  remaining: number
}
export interface RecoveryCodes {
  codes: string[]
}

export const authKeys = { recoveryStatus: ['admin', 'recovery-codes'] as const }

const json = (body: unknown) => ({ method: 'POST', body: JSON.stringify(body) })

export function useRequestOtp() {
  return useMutation({
    mutationFn: (email: string) => api<MessageResponse>('/auth/otp/request', json({ email: email.trim() })),
  })
}

/** On success the session cookie is set; refetch meta so `admin` flips to true. */
export function useVerifyOtp() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { email: string; code: string }) =>
      api<void>('/auth/otp/verify', json({ email: v.email.trim(), code: v.code })),
    onSuccess: () => qc.invalidateQueries({ queryKey: metaKeys.all }),
  })
}

export function useRecoveryLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (code: string) => api<void>('/auth/recovery', json({ code: code.trim() })),
    onSuccess: () => qc.invalidateQueries({ queryKey: metaKeys.all }),
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/auth/logout', { method: 'POST' }),
    onSuccess: () => qc.clear(),
  })
}

export function useRecoveryStatus() {
  return useQuery({ queryKey: authKeys.recoveryStatus, queryFn: () => api<RecoveryStatus>('/admin/recovery-codes') })
}

export function useRegenerateRecoveryCodes() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api<RecoveryCodes>('/admin/recovery-codes', { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: authKeys.recoveryStatus }),
  })
}
