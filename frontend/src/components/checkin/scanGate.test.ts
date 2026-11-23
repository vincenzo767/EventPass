import { describe, expect, it } from 'vitest'
import { ScanGate } from './scanGate'

describe('ScanGate', () => {
  it('accepts a code once and ignores repeats while it stays in view', () => {
    const gate = new ScanGate(5000)
    expect(gate.accept('CDM-7K3P9-Q2XHM', 0)).toBe(true)
    expect(gate.accept('CDM-7K3P9-Q2XHM', 300)).toBe(false)
    expect(gate.accept('cdm-7k3p9-q2xhm ', 4999)).toBe(false)
  })

  it('accepts the same code again after the repeat window', () => {
    const gate = new ScanGate(5000)
    gate.accept('CDM-7K3P9-Q2XHM', 0)
    expect(gate.accept('CDM-7K3P9-Q2XHM', 5000)).toBe(true)
  })

  it('accepts a different code immediately', () => {
    const gate = new ScanGate(5000)
    gate.accept('CDM-7K3P9-Q2XHM', 0)
    expect(gate.accept('CDM-AAAAA-BBBBB', 10)).toBe(true)
  })

  it('ignores empty input', () => {
    expect(new ScanGate().accept('   ', 0)).toBe(false)
  })
})
