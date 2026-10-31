// Each course gets one steady colour, chosen from its ID, so it looks the same everywhere on the dashboard. Every
// colour is dark enough for text on white and for white text on it.
const COURSE_COLORS = ['#1d4ed8', '#0f766e', '#be185d', '#7c3aed', '#b45309', '#0369a1', '#15803d', '#c2410c']

export function courseColor(id: number): string {
  return COURSE_COLORS[Math.abs(id) % COURSE_COLORS.length]
}
