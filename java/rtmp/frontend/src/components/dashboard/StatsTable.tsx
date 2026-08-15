import { Link } from 'react-router-dom'
import { useAppSelector } from '../../store/hooks'
import type { StreamStats } from '../../api/types'

function fmtUp(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h) return `${h}h ${m}m`
  if (m) return `${m}m ${seconds % 60}s`
  return `${seconds}s`
}

function delayClass(delayMs: number): string {
  if (delayMs > 5000) return 'stats-table__bad'
  if (delayMs > 2000) return 'stats-table__warn'
  return 'stats-table__good'
}

function droppedClass(dropped: number): string {
  return dropped > 0 ? 'stats-table__bad' : 'stats-table__good'
}

function gopValue(s: StreamStats): string {
  return s.maxKeyframeIntervalMs ? `${(s.maxKeyframeIntervalMs / 1000).toFixed(1)}s` : '--'
}

function ffmpegValue(s: StreamStats): string {
  if (!s.ffmpegFps && !s.ffmpegSpeed) return '--'
  return `${s.ffmpegFps || '?'}fps/${s.ffmpegSpeed || '?'}`
}

export function StatsTable() {
  const { stats, statsStatus, lastUpdate } = useAppSelector((state) => state.streams)
  const names = Object.keys(stats)
  const streaming = names.length > 0

  return (
    <div>
      <div className="status-bar">
        <div>
          <span className={`status-dot ${streaming ? 'status-dot--live' : 'status-dot--idle'}`} />
          <span className="status-bar__label">Status:</span>{' '}
          <span className="status-bar__value">{streaming ? 'STREAMING' : 'IDLE'}</span>
        </div>
        <div>
          <span className="status-bar__label">Active streams:</span>{' '}
          <span className="status-bar__value">{names.length}</span>
        </div>
        <div>
          <span className="status-bar__label">Updated:</span>{' '}
          <span className="status-bar__value">
            {lastUpdate ? new Date(lastUpdate).toLocaleTimeString() : '--'}
          </span>
        </div>
      </div>

      <table className="stats-table">
        <thead>
          <tr>
            <th>Stream</th>
            <th>Uptime</th>
            <th>Audio</th>
            <th>Video</th>
            <th>Bitrate</th>
            <th>Delay</th>
            <th>Keyframes</th>
            <th>Dropped</th>
            <th>FFmpeg</th>
          </tr>
        </thead>
        <tbody>
          {names.length === 0 ? (
            <tr>
              <td colSpan={9} className="no-streams">
                {statsStatus === 'loading' ? 'Loading...' : (
                  <>
                    No active streams
                    <div className="no-streams__hint">
                      Connect an RTMP publisher (OBS, ffmpeg) to start
                    </div>
                  </>
                )}
              </td>
            </tr>
          ) : (
            names.map((name) => {
              const s = stats[name]
              return (
                <tr key={name}>
                  <td>
                    <Link to={`/${encodeURIComponent(name)}`}>{name}</Link>
                  </td>
                  <td>{fmtUp(s.uptimeSec || 0)}</td>
                  <td>
                    {s.audioPackets || 0}p / {s.audioBytesHuman || '0B'}
                  </td>
                  <td>
                    {s.videoPackets || 0}p / {s.videoBytesHuman || '0B'}
                  </td>
                  <td>{s.bitrateHuman || '--'}</td>
                  <td className={delayClass(s.delayMs || 0)}>
                    {((s.delayMs || 0) / 1000).toFixed(1)}s
                  </td>
                  <td>
                    {s.keyframeCount || 0} ({gopValue(s)})
                  </td>
                  <td className={droppedClass(s.droppedPackets || 0)}>{s.droppedPackets || 0}</td>
                  <td>{ffmpegValue(s)}</td>
                </tr>
              )
            })
          )}
        </tbody>
      </table>

      <div className="footer">
        Auto-refresh every 5s | <Link to="/">Stream list</Link>
      </div>
    </div>
  )
}