import { useCallback } from 'react'
import { StatsTable } from '../components/dashboard/StatsTable'
import { useAppDispatch } from '../store/hooks'
import { loadStats } from '../store/streamsSlice'
import { usePolling } from '../hooks/usePolling'

const REFRESH_MS = 5000

export function DashboardPage() {
  const dispatch = useAppDispatch()

  const refresh = useCallback(() => {
    void dispatch(loadStats())
  }, [dispatch])

  usePolling(refresh, REFRESH_MS)

  return (
    <main>
      <h1>RTMP Dashboard</h1>
      <StatsTable />
    </main>
  )
}