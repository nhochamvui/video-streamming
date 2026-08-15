import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <main>
      <h1>Page not found</h1>
      <p>
        <Link to="/">Back to stream list</Link>
      </p>
    </main>
  )
}