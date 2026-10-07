interface LearningTaskDuration {
  estimatedMinutes?: number | null
  estimatedHours: number
}

export function learningTaskMinutes(task: LearningTaskDuration) {
  return task.estimatedMinutes ?? task.estimatedHours * 60
}

export function formatLearningTaskDuration(task: LearningTaskDuration) {
  const minutes = learningTaskMinutes(task)
  return minutes >= 60 && minutes % 60 === 0 ? `${minutes / 60} 小时` : `${minutes} 分钟`
}

export function sumLearningTaskMinutes(tasks: readonly LearningTaskDuration[]) {
  return tasks.reduce((sum, task) => sum + learningTaskMinutes(task), 0)
}
