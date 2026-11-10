import { useState } from 'react'
import { api } from '../api'
import { Badge, Card, ErrorBox, Table, fmtDate, useLoad } from '../ui'

const SYSTEMS = { SUPPLIERS: 'ERP', SUPPLIER_LOTS: 'SUPPLIER_PORTAL', RECEIPTS: 'WMS', MOVEMENTS: 'MES', DISPATCHES: 'OMS' }

export default function Ingestion() {
  const feeds = useLoad(api.feeds)
  const runs = useLoad(api.runs)
  const [feed, setFeed] = useState('SUPPLIER_LOTS')
  const [file, setFile] = useState(null)
  const [source, setSource] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [openRun, setOpenRun] = useState(null)

  const upload = async () => {
    setError(null)
    try { setResult(await api.ingest(feed, file, source || SYSTEMS[feed])); runs.reload() } catch (e) { setError(e.message) }
  }
  const reset = async () => {
    if (!confirm('Delete ALL data (lots, orders, recalls)? Use this before loading a hidden scenario.')) return
    await api.reset(); runs.reload(); setResult(null)
  }
  const def = (feeds.data || []).find(f => f.name === feed)
  const report = r => { try { return JSON.parse(r.validationReport || '[]') } catch { return [] } }

  return (
    <>
      <ErrorBox error={error} />
      <div className="grid2">
        <Card title="Upload a feed" actions={<button className="secondary danger-text" onClick={reset}>Reset database</button>}>
          <p className="muted">Load order: suppliers → supplier lots → goods receipts → lot movements → dispatches. CSV (any delimiter) or JSON accepted; column names are matched by synonym.</p>
          <div className="form-col">
            <label>Feed
              <select value={feed} onChange={e => setFeed(e.target.value)}>
                {(feeds.data || []).map(f => <option key={f.name} value={f.name}>{f.name} - {f.description}</option>)}
              </select>
            </label>
            <label>Source system<input value={source} onChange={e => setSource(e.target.value)} placeholder={SYSTEMS[feed]} /></label>
            <label>File<input type="file" accept=".csv,.json,.txt,.tsv" onChange={e => setFile(e.target.files[0])} /></label>
            <button disabled={!file} onClick={upload}>Validate &amp; ingest</button>
          </div>
          {result && (
            <div className="result">
              <b>{result.fileName}</b>: read {result.rowsRead} · <Badge tone="ok">accepted {result.rowsAccepted}</Badge>{' '}
              {result.rowsUnmatched > 0 && <Badge tone="bad">unmatched {result.rowsUnmatched}</Badge>}{' '}
              {result.rowsRejected > 0 && <Badge tone="warn">rejected {result.rowsRejected}</Badge>}{' '}
              {result.rowsDuplicate > 0 && <Badge>duplicate {result.rowsDuplicate}</Badge>}
              <ul className="notes">{report(result).map((m, i) => <li key={i}>row {m.row}: <b>{m.outcome}</b> {m.message}</li>)}</ul>
            </div>
          )}
        </Card>
        <Card title={`Expected columns · ${feed}`}>
          {def && (
            <Table rows={Object.entries(def.columns)} rowKey={([k]) => k} columns={[
              { label: 'Field', render: ([k]) => <><code>{k}</code>{def.required.includes(k) && <Badge tone="warn">required</Badge>}</> },
              { label: 'Accepted header names', render: ([, v]) => <span className="muted small">{v.join(', ')}</span> },
            ]} />
          )}
        </Card>
      </div>
      <Card title="Ingestion runs (audit)">
        <Table rows={runs.data} rowKey={r => r.id} onRow={r => setOpenRun(openRun === r.id ? null : r.id)} columns={[
          { label: 'When', render: r => fmtDate(r.startedAt) },
          { label: 'Feed', key: 'feedType' },
          { label: 'System', key: 'sourceSystem' },
          { label: 'File', key: 'fileName' },
          { label: 'Read', key: 'rowsRead', num: true },
          { label: 'Accepted', key: 'rowsAccepted', num: true },
          { label: 'Unmatched', render: r => r.rowsUnmatched ? <Badge tone="bad">{r.rowsUnmatched}</Badge> : 0, num: true },
          { label: 'Rejected', render: r => r.rowsRejected ? <Badge tone="warn">{r.rowsRejected}</Badge> : 0, num: true },
          { label: 'Dup', key: 'rowsDuplicate', num: true },
          { label: 'Messages', render: r => openRun === r.id
              ? <ul className="notes">{report(r).map((m, i) => <li key={i}>row {m.row}: <b>{m.outcome}</b> {m.message}</li>)}</ul>
              : <span className="link">{report(r).length} ▸</span> },
        ]} />
      </Card>
    </>
  )
}
