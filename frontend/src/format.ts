const pad = (n: number) => String(n).padStart(2, '0')

export function fmtDate(s: string | null): string {
  if (!s) return '-'
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
