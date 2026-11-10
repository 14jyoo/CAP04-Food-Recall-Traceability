import { useState } from 'react'
import { api } from '../api'
import { Badge, Card, ErrorBox, Table, fmtDate, statusTone, useLoad } from '../ui'

export default function Reconciliation() {
  const [status, setStatus] = useState('OPEN')
  const list = useLoad(() => api.reconciliation(status), [status])
  const recalls = useLoad(api.recalls)
  const [sel, setSel] = useState(null)
  const [sugg, setSugg] = useState([])
  const [target, setTarget] = useState('')
  const [msg, setMsg] = useState(null)
  const [error, setError] = useState(null)

  const pick = async (u) => {
    setSel(u); setMsg(null); setTarget('')
    try { const s = await api.suggest(u.id); setSugg(s); if (s[0]) setTarget(s[0].candidate) } catch (e) { setError(e.message) }
  }

  const resolve = async () => {
    try {
      const r = await api.resolve(sel.id, target)
      const open = (recalls.data || []).filter(x => x.status !== 'CLOSED')
      for (const x of open) await api.recompute(x.id)
      setMsg(`Resolved to ${target}: ${r.accepted}/${r.replayed} parked row(s) replayed into the lineage graph.` +
        (open.length ? ` Recomputed ${open.length} active recall(s) - check their scope and notifications.` : ''))
      setSel(null); list.reload(); recalls.reload()
    } catch (e) { setError(e.message) }
  }

  const ignore = async () => {
    await api.ignore(sel.id); setSel(null); list.reload()
  }

  let raw = null
  try { raw = sel ? JSON.parse(sel.rawRow) : null } catch { raw = null }

  return (
    <>
      <ErrorBox error={error || list.error} />
      {msg && <div className="success">{msg}</div>}
      <div className="grid2 wide-left">
        <Card title="Reconciliation queue" actions={
          <select value={status} onChange={e => setStatus(e.target.value)}>
            <option>OPEN</option><option>RESOLVED</option><option>IGNORED</option>
          </select>}>
          <p className="muted">Rows that could not be linked are never dropped. Each open row blocks the completeness proof of any recall it could belong to.</p>
          <Table rows={list.data} empty="Queue is empty - every source record is linked." onRow={pick} rowKey={r => r.id}
            columns={[
              { label: 'Issue', render: r => <Badge tone="bad">{r.issue}</Badge> },
              { label: 'Value', render: r => <code>{r.offendingValue}</code> },
              { label: 'Feed', key: 'recordType' },
              { label: 'Source', render: r => `${r.sourceSystem} · ${r.sourceRecordId}` },
              { label: 'Status', render: r => <Badge tone={statusTone(r.status)}>{r.status}</Badge> },
              { label: 'Resolved to', key: 'resolvedTo' },
              { label: 'Parked', render: r => fmtDate(r.createdAt) },
            ]} />
        </Card>
        <Card title={sel ? `Resolve ${sel.offendingValue}` : 'Select a row'}>
          {!sel && <div className="empty">Pick a queued record to see match suggestions.</div>}
          {sel && (
            <>
              <h4>Original row</h4>
              <pre className="raw">{JSON.stringify(raw, null, 2)}</pre>
              <h4>Suggestions</h4>
              {sugg.length === 0 && <div className="muted">No close candidates - enter the code manually.</div>}
              {sugg.map(s => (
                <label key={s.candidate} className="sugg">
                  <input type="radio" checked={target === s.candidate} onChange={() => setTarget(s.candidate)} />
                  <b>{s.label}</b> <Badge tone={s.score >= 80 ? 'ok' : 'warn'}>score {s.score}</Badge>
                  <div className="muted small">{s.reason}</div>
                </label>
              ))}
              <div className="form-row">
                <label>Map to<input value={target} onChange={e => setTarget(e.target.value)} placeholder="SUP0001 or lot code" /></label>
                <button disabled={!target || sel.status !== 'OPEN'} onClick={resolve}>Resolve &amp; replay</button>
                <button className="secondary" disabled={sel.status !== 'OPEN'} onClick={ignore}>Ignore</button>
              </div>
              <p className="muted small">Supplier codes become a MANUAL alias, so future files with the same code link automatically.</p>
            </>
          )}
        </Card>
      </div>
    </>
  )
}
