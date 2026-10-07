import { describe, expect, it } from 'vitest'
import { formatLearningTaskDuration, learningTaskMinutes, sumLearningTaskMinutes } from './learningDuration'

describe('learning task durations', () => {
  it('displays segmented task minutes instead of rounded-up hours', () => {
    expect(formatLearningTaskDuration({ estimatedMinutes: 30, estimatedHours: 1 })).toBe('30 分钟')
    expect(formatLearningTaskDuration({ estimatedMinutes: 90, estimatedHours: 2 })).toBe('90 分钟')
    expect(formatLearningTaskDuration({ estimatedMinutes: 120, estimatedHours: 2 })).toBe('2 小时')
  })

  it('uses legacy hours only when minutes are missing', () => {
    expect(formatLearningTaskDuration({ estimatedHours: 3 })).toBe('3 小时')
    expect(learningTaskMinutes({ estimatedMinutes: null, estimatedHours: 2 })).toBe(120)
    expect(learningTaskMinutes({ estimatedMinutes: 0, estimatedHours: 1 })).toBe(0)
  })

  it('sums exact minutes across segmented and legacy tasks for the weekly budget', () => {
    expect(sumLearningTaskMinutes([
      { estimatedMinutes: 30, estimatedHours: 1 },
      { estimatedMinutes: 90, estimatedHours: 2 },
      { estimatedHours: 2 }
    ])).toBe(240)
    expect(sumLearningTaskMinutes([])).toBe(0)
  })
})
