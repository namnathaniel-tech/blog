import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useRecoveryLogin, useRequestOtp, useVerifyOtp } from '@/api/auth'
import { ApiError } from '@/api/client'
import { useSiteMeta } from '@/api/meta'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp'
import { Label } from '@/components/ui/label'

type Step = 'email' | 'code' | 'recovery'

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 429) return 'Too many attempts. Wait a few minutes and try again.'
    return err.message
  }
  return 'Something went wrong. Please try again.'
}

export function LoginPage() {
  const navigate = useNavigate()
  const meta = useSiteMeta()
  const [step, setStep] = useState<Step>('email')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [recoveryCode, setRecoveryCode] = useState('')
  const [info, setInfo] = useState<string>()

  const requestOtp = useRequestOtp()
  const verifyOtp = useVerifyOtp()
  const recoveryLogin = useRecoveryLogin()

  if (meta.data?.admin) return <Navigate to="/admin" replace />

  const onLoggedIn = () => navigate('/admin', { replace: true })

  const submitEmail = (e: FormEvent) => {
    e.preventDefault()
    requestOtp.mutate(email, {
      onSuccess: (res) => {
        setInfo(res.message)
        setCode('')
        setStep('code')
      },
    })
  }

  const submitCode = (value: string) => {
    verifyOtp.mutate({ email, code: value }, { onSuccess: onLoggedIn, onError: () => setCode('') })
  }

  const submitRecovery = (e: FormEvent) => {
    e.preventDefault()
    recoveryLogin.mutate(recoveryCode, { onSuccess: onLoggedIn })
  }

  const activeError = { email: requestOtp.error, code: verifyOtp.error, recovery: recoveryLogin.error }[step]

  return (
    <div className="mx-auto max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle>Admin login</CardTitle>
          <CardDescription>
            {step === 'email' && 'Enter your email and we’ll send you a 6-digit code.'}
            {step === 'code' && (info ?? 'Check your inbox.')}
            {step === 'recovery' && 'Enter one of your saved recovery codes.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {activeError ? (
            <Alert variant="destructive">
              <AlertDescription>{errorMessage(activeError)}</AlertDescription>
            </Alert>
          ) : null}

          {step === 'email' && (
            <form onSubmit={submitEmail} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  autoFocus
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
              <Button type="submit" className="w-full" disabled={requestOtp.isPending}>
                {requestOtp.isPending ? 'Sending…' : 'Send code'}
              </Button>
            </form>
          )}

          {step === 'code' && (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="otp">6-digit code</Label>
                <InputOTP
                  id="otp"
                  maxLength={6}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  autoFocus
                  value={code}
                  onChange={setCode}
                  onComplete={submitCode}
                  disabled={verifyOtp.isPending}
                >
                  <InputOTPGroup>
                    {[0, 1, 2, 3, 4, 5].map((i) => (
                      <InputOTPSlot key={i} index={i} />
                    ))}
                  </InputOTPGroup>
                </InputOTP>
              </div>
              <Button
                className="w-full"
                disabled={code.length !== 6 || verifyOtp.isPending}
                onClick={() => submitCode(code)}
              >
                {verifyOtp.isPending ? 'Checking…' : 'Log in'}
              </Button>
              <div className="flex justify-between text-sm">
                <button type="button" className="underline" onClick={() => setStep('email')}>
                  Use a different email
                </button>
                <button
                  type="button"
                  className="underline"
                  disabled={requestOtp.isPending}
                  onClick={() => requestOtp.mutate(email)}
                >
                  Resend code
                </button>
              </div>
            </div>
          )}

          {step === 'recovery' && (
            <form onSubmit={submitRecovery} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="recovery">Recovery code</Label>
                <Input
                  id="recovery"
                  placeholder="XXXX-XXXX-XXXX"
                  autoComplete="off"
                  autoFocus
                  required
                  value={recoveryCode}
                  onChange={(e) => setRecoveryCode(e.target.value)}
                  className="font-mono uppercase"
                />
              </div>
              <Button type="submit" className="w-full" disabled={recoveryLogin.isPending}>
                {recoveryLogin.isPending ? 'Checking…' : 'Log in'}
              </Button>
            </form>
          )}

          <div className="text-center text-sm text-muted-foreground">
            {step === 'recovery' ? (
              <button type="button" className="underline" onClick={() => setStep('email')}>
                Back to email login
              </button>
            ) : (
              <button type="button" className="underline" onClick={() => setStep('recovery')}>
                Use a recovery code instead
              </button>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
