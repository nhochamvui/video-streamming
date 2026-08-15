import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadActiveStreams } from '../../store/streamsSlice'

export function ActiveStreamsList() {
  const dispatch = useAppDispatch()
  const { names, namesStatus } = useAppSelector((state) => state.streams)

  useEffect(() => {
    void dispatch(loadActiveStreams())
  }, [dispatch])

  if (namesStatus === 'loading' && names.length === 0) {
    return <p className="loading">Loading streams...</p>
  }

  if (names.length === 0) {
    return <p className="stream-list__empty">No active streams.</p>
  }

  return (
    <ul className="stream-list">
      {names.map((name) => (
        <li key={name}>
          <Link to={`/${encodeURIComponent(name)}`}>{name}</Link>
        </li>
      ))}
    </ul>
  )
}