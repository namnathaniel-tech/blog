import { useState } from 'react'
import { toast } from 'sonner'
import { useRecoveryStatus, useRegenerateRecoveryCodes } from '@/api/auth'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function SettingsPage() {
  const status = useRecoveryStatus()
  const regenerate = useRegenerateRecoveryCodes()
  const [codes, setCodes] = useState<string[]>()
  const [confirming, setConfirming] = useState(false)

  const remaining = status.data?.remaining ?? 0

  const generate = () => {
    regenerate.mutate(undefined, {
      onSuccess: (res) => {
        setCodes(res.codes)
        setConfirming(false)
      },
      onError: () => toast.error('Could not generate codes.'),
    })
  }

  const copy = async () => {
    if (!codes) return
    await navigator.clipboard.writeText(codes.join('\n'))
    toast.success('Copied to clipboard')
  }

  const download = () => {
    if (!codes) return
    const blob = new Blob([`Blog admin recovery codes (each works once)\n\n${codes.join('\n')}\n`], {
      type: 'text/plain',
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'blog-recovery-codes.txt'
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Settings</h1>
      <Card>
        <CardHeader>
          <CardTitle>Recovery codes</CardTitle>
          <CardDescription>
            Backup login codes for when email doesn’t arrive. Each code works once. Keep them in a password
            manager.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {status.isSuccess && !codes && (
            <p className="text-sm">
              {remaining === 0 ? (
                <span className="font-medium text-destructive">You have no recovery codes. Generate some now.</span>
              ) : (
                <>
                  <span className="font-medium">{remaining}</span> unused code{remaining === 1 ? '' : 's'} left.
                </>
              )}
            </p>
          )}

          {codes && (
            <Alert>
              <AlertTitle>Save these now. They won’t be shown again.</AlertTitle>
              <AlertDescription>
                <ul className="mt-2 grid grid-cols-2 gap-1 font-mono text-sm" aria-label="Recovery codes">
                  {codes.map((c) => (
                    <li key={c}>{c}</li>
                  ))}
                </ul>
                <div className="mt-3 flex gap-2">
                  <Button size="sm" variant="outline" onClick={copy}>
                    Copy
                  </Button>
                  <Button size="sm" variant="outline" onClick={download}>
                    Download .txt
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setCodes(undefined)}>
                    I’ve saved them
                  </Button>
                </div>
              </AlertDescription>
            </Alert>
          )}

          {!codes &&
            (remaining > 0 && !confirming ? (
              <Button variant="outline" onClick={() => setConfirming(true)}>
                Regenerate codes
              </Button>
            ) : (
              <div className="space-y-2">
                {remaining > 0 && (
                  <p className="text-sm text-destructive">
                    This permanently invalidates your {remaining} existing code{remaining === 1 ? '' : 's'}.
                  </p>
                )}
                <div className="flex gap-2">
                  <Button onClick={generate} disabled={regenerate.isPending}>
                    {regenerate.isPending ? 'Generating…' : 'Generate 10 new codes'}
                  </Button>
                  {confirming && (
                    <Button variant="ghost" onClick={() => setConfirming(false)}>
                      Cancel
                    </Button>
                  )}
                </div>
              </div>
            ))}
        </CardContent>
      </Card>
    </div>
  )
}
