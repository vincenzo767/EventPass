/**
 * Prevents the same QR code from being processed repeatedly while it stays in front of the camera.
 * A code is accepted once, then ignored for `repeatWindowMs` after it was last accepted. A different
 * code is accepted immediately (when the scanner is not busy showing a result).
 */
export class ScanGate {
  private lastCode: string | undefined
  private lastAcceptedAt = -Infinity
  private readonly repeatWindowMs: number

  constructor(repeatWindowMs = 5000) {
    this.repeatWindowMs = repeatWindowMs
  }

  accept(code: string, now: number): boolean {
    const normalized = code.trim().toUpperCase()
    if (!normalized) return false
    if (normalized === this.lastCode && now - this.lastAcceptedAt < this.repeatWindowMs) return false
    this.lastCode = normalized
    this.lastAcceptedAt = now
    return true
  }
}
