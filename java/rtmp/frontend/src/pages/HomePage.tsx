import { Link } from 'react-router-dom'
import { CreateSessionCard } from '../components/session/CreateSessionCard'
import { ActiveStreamsList } from '../components/streams/ActiveStreamsList'
import { Section } from '../components/ui/Section'

export function HomePage() {
  return (
    <main>
      <h1>RTMP Server</h1>
      <p className="intro">
        Create a temporary stream session, then paste the server URL and stream key into OBS.
      </p>

      <nav className="nav">
        <Link to="/dashboard">Dashboard</Link>
      </nav>

      <Section title="Streamer Setup">
        <CreateSessionCard />
      </Section>

      <Section title="Active Streams">
        <ActiveStreamsList />
      </Section>
    </main>
  )
}