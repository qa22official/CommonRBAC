import { reactive } from 'vue'

export interface RateLimitState {
  seconds: number
  message: string
}

export const rateLimitState = reactive<RateLimitState>({
  seconds: 0,
  message: '',
})

let timer: ReturnType<typeof setInterval> | null = null

export function startRateLimitCountdown(
  seconds: number,
  message = '操作过于频繁，请稍后重试',
): void {
  const total = Math.max(1, Math.ceil(seconds))
  rateLimitState.seconds = total
  rateLimitState.message = message
  if (timer !== null) {
    clearInterval(timer)
  }
  timer = setInterval(() => {
    if (rateLimitState.seconds <= 1) {
      resetRateLimitCountdown()
      return
    }
    rateLimitState.seconds -= 1
  }, 1000)
}

export function resetRateLimitCountdown(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
  rateLimitState.seconds = 0
  rateLimitState.message = ''
}
