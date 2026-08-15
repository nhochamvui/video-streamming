import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Player } from '../components/player/Player'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { loadActiveStreams } from '../store/streamsSlice'

export function PlayerPage() {
  const { playbackId = '' } = useParams()
  const dispatch = useAppDispatch()
  const { names, namesStatus } = useAppSelector((state) => state.streams)

  useEffect(() => {
    void dispatch(loadActiveStreams())
  }, [dispatch, playbackId])

  if (namesStatus === 'loading' && names.length === 0) {
    return (
      <div className="player__frame">
        <p className="loading">Loading stream...</p>
      </div>
    )
  }

  if (!names.includes(playbackId)) {
    return (
      <div className="not-found">
        <h1 className="not-found__title">Stream not found: {playbackId}</h1>
        <p>
          <Link to="/">Back to stream list</Link>
        </p>
      </div>
    )
  }

  return (
    <div className="player__frame">
      <Player playbackId={playbackId} />
    </div>
  )
}