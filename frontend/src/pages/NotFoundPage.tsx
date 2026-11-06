import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="space-y-2">
      <h1 className="text-2xl font-semibold">Not found</h1>
      <Link to="/" className="text-sm underline">
        Back home
      </Link>
    </div>
  )
}
