import { useCallback, useEffect, useRef, useState } from 'react'
import { Loader2, Mic, Square } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * Voice capture. Two honest paths:
 *  - "server": records audio with MediaRecorder and hands the blob to the caller, which uploads it for Sarvam speech-to-text.
 *  - "browser": uses the browser's own SpeechRecognition and returns text. Clearly a different engine, labelled by the caller.
 */
interface Props {
  mode: 'server' | 'browser'
  languageTag: string
  disabled?: boolean
  busy?: boolean
  onAudio?: (blob: Blob) => void
  onText?: (text: string) => void
  className?: string
  size?: 'default' | 'icon' | 'sm'
  label?: string
}

interface SpeechRecognitionLike {
  lang: string
  interimResults: boolean
  maxAlternatives: number
  start: () => void
  stop: () => void
  onresult: ((e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null
  onerror: ((e: { error: string }) => void) | null
  onend: (() => void) | null
}

type RecognitionCtor = new () => SpeechRecognitionLike

function recognitionCtor(): RecognitionCtor | undefined {
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition
}

export function browserSpeechSupported() {
  return typeof window !== 'undefined' && recognitionCtor() !== undefined
}

export function recordingSupported() {
  return typeof navigator !== 'undefined' && Boolean(navigator.mediaDevices?.getUserMedia) && typeof MediaRecorder !== 'undefined'
}

export function VoiceButton({ mode, languageTag, disabled, busy, onAudio, onText, className, size = 'icon', label }: Props) {
  const [recording, setRecording] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const recorder = useRef<MediaRecorder | null>(null)
  const recognition = useRef<SpeechRecognitionLike | null>(null)
  const chunks = useRef<Blob[]>([])
  const timer = useRef<ReturnType<typeof setInterval> | null>(null)
  const stream = useRef<MediaStream | null>(null)

  const stopTimer = () => {
    if (timer.current) clearInterval(timer.current)
    timer.current = null
  }

  const cleanup = useCallback(() => {
    stopTimer()
    stream.current?.getTracks().forEach((t) => t.stop())
    stream.current = null
    setRecording(false)
    setSeconds(0)
  }, [])

  useEffect(
    () => () => {
      recognition.current?.stop()
      if (recorder.current && recorder.current.state !== 'inactive') recorder.current.stop()
      stream.current?.getTracks().forEach((t) => t.stop())
      stopTimer()
    },
    [],
  )

  const startTimer = () => {
    setSeconds(0)
    timer.current = setInterval(() => setSeconds((s) => s + 1), 1000)
  }

  const start = async () => {
    try {
      if (mode === 'browser') {
        const Ctor = recognitionCtor()
        if (!Ctor) {
          toast.error('This browser does not support speech recognition. Please type instead.')
          return
        }
        const r = new Ctor()
        r.lang = languageTag
        r.interimResults = false
        r.maxAlternatives = 1
        r.onresult = (e) => {
          const text = Array.from(e.results)
            .map((res) => res[0]?.transcript ?? '')
            .join(' ')
            .trim()
          if (text) onText?.(text)
        }
        r.onerror = (e) => {
          if (e.error !== 'aborted' && e.error !== 'no-speech') toast.error(`Speech recognition error: ${e.error}`)
        }
        r.onend = cleanup
        recognition.current = r
        r.start()
        setRecording(true)
        startTimer()
        return
      }

      if (!recordingSupported()) {
        toast.error('Audio recording is not supported in this browser.')
        return
      }
      const s = await navigator.mediaDevices.getUserMedia({ audio: true })
      stream.current = s
      const rec = new MediaRecorder(s)
      chunks.current = []
      rec.ondataavailable = (e) => e.data.size > 0 && chunks.current.push(e.data)
      rec.onstop = () => {
        const blob = new Blob(chunks.current, { type: rec.mimeType || 'audio/webm' })
        cleanup()
        if (blob.size > 0) onAudio?.(blob)
      }
      recorder.current = rec
      rec.start()
      setRecording(true)
      startTimer()
    } catch (e) {
      cleanup()
      const denied = e instanceof DOMException && (e.name === 'NotAllowedError' || e.name === 'SecurityError')
      toast.error(denied ? 'Microphone permission was denied.' : 'Could not start the microphone.')
    }
  }

  const stop = () => {
    if (mode === 'browser') recognition.current?.stop()
    else if (recorder.current && recorder.current.state !== 'inactive') recorder.current.stop()
  }

  // Safety net so a forgotten recording cannot exceed the server's upload limit.
  useEffect(() => {
    if (recording && seconds >= 60) stop()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seconds, recording])

  const isIcon = size === 'icon'
  return (
    <Button
      type="button"
      variant={recording ? 'destructive' : 'soft'}
      size={size}
      className={cn(recording && 'animate-pulse', className)}
      disabled={disabled || busy}
      onClick={recording ? stop : start}
      aria-label={recording ? 'Stop recording' : 'Start voice input'}
      aria-pressed={recording}
    >
      {busy ? <Loader2 className="animate-spin" /> : recording ? <Square /> : <Mic />}
      {!isIcon && (recording ? `Stop · ${seconds}s` : (label ?? 'Speak'))}
    </Button>
  )
}
