import { useEffect, useRef } from 'react'
import videojs from 'video.js'
import type PlayerInstance from 'video.js/dist/types/player'
import 'video.js/dist/video-js.css'

interface PlayerProps {
  playbackId: string
}

export function Player({ playbackId }: PlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const playerRef = useRef<PlayerInstance | null>(null)

  useEffect(() => {
    if (!videoRef.current) return

    let disposed = false

    async function init() {
      let hlsCdn = ''
      try {
        const res = await fetch('/config')
        const cfg = await res.json()
        hlsCdn = typeof cfg.hlsCdn === 'string' ? cfg.hlsCdn : ''
      } catch {
        hlsCdn = ''
      }

      if (disposed || !videoRef.current) return

      const src = hlsCdn
        ? `${hlsCdn}/hls/${encodeURIComponent(playbackId)}/master.m3u8`
        : `/hls/${encodeURIComponent(playbackId)}/master.m3u8`

      const player = videojs(videoRef.current, {
        liveui: true,
        liveTracker: {
          trackingThreshold: 0,
          liveTolerance: 5,
        },
        html5: {
          vhs: {
            overrideNative: true,
            enableLowInitialPlaylist: false,
            goalBufferLength: 30,
            maxBufferLength: 45,
            liveSyncDuration: 10,
          },
          nativeAudioTracks: false,
          nativeVideoTracks: false,
        },
        sources: [
          {
            src,
            type: 'application/x-mpegURL',
          },
        ],
      })
      playerRef.current = player
    }

    init()

    return () => {
      disposed = true
      playerRef.current?.dispose()
      playerRef.current = null
    }
  }, [playbackId])

  return (
    <video
      ref={videoRef}
      className="video-js vjs-default-skin"
      controls
      preload="auto"
      autoPlay
      muted
    />
  )
}