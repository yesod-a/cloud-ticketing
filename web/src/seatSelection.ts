export function toggleSelection(selected: string[], seatId: string, max = 6): string[] {
  if (selected.includes(seatId)) return selected.filter(id => id !== seatId)
  return selected.length >= max ? selected : [...selected, seatId]
}
