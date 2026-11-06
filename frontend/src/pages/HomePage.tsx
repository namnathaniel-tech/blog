import { useSiteMeta } from '@/api/meta'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

/** Phase 0 placeholder. Phase 1 replaces this with the published post list. */
export function HomePage() {
  const { data, isPending, isError } = useSiteMeta()
  return (
    <Card>
      <CardHeader>
        <CardTitle>Skeleton is alive</CardTitle>
      </CardHeader>
      <CardContent className="text-sm text-muted-foreground">
        {isPending && 'Contacting backend…'}
        {isError && 'Backend unreachable. Is Spring Boot running on :8080?'}
        {data && `Connected to ${data.siteName} backend v${data.version}.`}
      </CardContent>
    </Card>
  )
}
