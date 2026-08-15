import { Routes, Route } from 'react-router-dom'
import { HomePage } from './pages/HomePage'
import { PlayerPage } from './pages/PlayerPage'
import { DashboardPage } from './pages/DashboardPage'
import { NotFoundPage } from './pages/NotFoundPage'

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/:playbackId" element={<PlayerPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}