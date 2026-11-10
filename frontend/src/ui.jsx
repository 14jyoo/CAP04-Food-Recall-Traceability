import { useCallback, useEffect, useState } from 'react'

export function useLoad(fn, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const reload = useCallback(() => {
    setLoading(true)
    return fn().then(d => { setData(d); setError(null) }).catch(e => setError(e.message)).finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(() => { reload() }, [reload])
  return { data, error, loading, reload, setData }
}

export const Card = ({ title, actions, children, className = '' }) => (
  <section className={`card ${className}`}>
    {(title || actions) && <header><h3>{title}</h3><div className="actions">{actions}</div></header>}
    {children}
  </section>
)

export const Kpi = ({ label, value, hint, tone }) => (
  <div className={`kpi ${tone || ''}`}>
    <div className="kpi-value">{value ?? '–'}</div>
    <div className="kpi-label">{label}</div>
    {hint && <div className="kpi-hint">{hint}</div>}
  </div>
)

export const Badge = ({ children, tone = 'neutral' }) => <span className={`badge ${tone}`}>{children}</span>

export const statusTone = s => ({
  OPEN: 'warn', NOTIFYING: 'warn', SIMULATED: 'neutral', CLOSED: 'ok', PENDING: 'warn', SENT: 'info', ACKNOWLEDGED: 'ok', FAILED: 'bad',
  RESOLVED: 'ok', IGNORED: 'neutral', CLASS_I: 'bad', CLASS_II: 'warn', CLASS_III: 'info',
}[s] || 'neutral')

export const ErrorBox = ({ error }) => error ? <div className="error">⚠ {error}</div> : null

export const Empty = ({ children }) => <div className="empty">{children}</div>

export function Table({ columns, rows, empty = 'Nothing here yet.', onRow, rowKey }) {
  if (!rows || rows.length === 0) return <Empty>{empty}</Empty>
  return (
    <div className="table-wrap">
      <table>
        <thead><tr>{columns.map(c => <th key={c.key || c.label} className={c.num ? 'num' : ''}>{c.label}</th>)}</tr></thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={rowKey ? rowKey(r) : i} onClick={onRow ? () => onRow(r) : undefined} className={onRow ? 'clickable' : ''}>
              {columns.map(c => <td key={c.key || c.label} className={c.num ? 'num' : ''}>{c.render ? c.render(r) : r[c.key]}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export const fmtDate = s => (s ? String(s).replace('T', ' ').slice(0, 16) : '–')
