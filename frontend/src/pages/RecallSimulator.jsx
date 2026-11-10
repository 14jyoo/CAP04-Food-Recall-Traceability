import { useEffect, useState } from 'react'
import { api } from '../api'
import LineageGraph from '../LineageGraph'
import { Badge, Card, ErrorBox, Kpi, Table } from '../ui'

function Check({ ok, label, detail }) {
  return (
    <div className={`check ${ok ? 'pass' : 'fail'}`}>
      <span className="check-icon">{ok ? '✓' : '✕'}</span>
      <div><b>{label}</b>{detail && <div className="muted">{detail}</div>}</div>
    </div>
  )
}

export default function RecallSimulator({ go, params }) {
  const [q, setQ] = useState('')
  const [lots, setLots] = useState([])
  const [lotId, setLotId] = useState(params?.lotId || null)
  const [sim, setSim] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState('Pathogen positive on supplier CoA re-test')
  const [severity, setSeverity] = useState('CLASS_I')
  const [openPath, setOpenPath] = useState(null)

  useEffect(() => { api.supplierLots(q).then(setLots).catch(e => setError(e.message)) }, [q])
  useEffect(() => {
    if (!lotId) return
    setBusy(true); setError(null)
    api.simulate(lotId).then(setSim).catch(e => setError(e.message)).finally(() => setBusy(false))
  }, [lotId])

  const commit = async (simulationOnly) => {
    setBusy(true)
    try {
      const r = await api.createRecall({ supplierLotId: lotId, reason, severity, simulationOnly })
      go('notifications', { recallId: r.id })
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  const c = sim?.completeness
  return (
    <>
      <ErrorBox error={error} />
      <div className="sim-layout">
        <Card title="1 · Pick the recalled supplier lot" className="lot-picker">
          <input placeholder="Search lot, supplier, ingredient…" value={q} onChange={e => setQ(e.target.value)} />
          <div className="lot-list">
            {lots.map(l => (
              <button key={l.id} className={`lot-item ${l.id === lotId ? 'active' : ''}`} onClick={() => setLotId(l.id)}>
                <b>{l.lotCodeRaw}</b> <span className="muted">{l.supplier.canonicalCode}</span>
                <div className="muted small">{l.supplier.name} · {l.ingredientName || l.ingredientCode}</div>
                <div className="muted small">{l.quantityReceived ?? '?'} {l.uom} · rcvd {l.receivedDate || '–'}</div>
              </button>
            ))}
          </div>
        </Card>

        <div className="sim-main">
          {!sim && <Card><div className="empty">Choose a supplier lot to trace it forward to every dispatched order line.</div></Card>}
          {sim && (
            <>
              <div className={`verdict ${c.verified ? 'ok' : 'bad'}`}>
                <div>
                  <div className="verdict-title">{c.verified ? 'Recall scope PROVEN complete' : 'Recall scope NOT proven complete'}</div>
                  <div className="small">{sim.supplierLot.label} · lot <b>{sim.supplierLot.code}</b></div>
                </div>
                <code title="sha256 of sorted affected order lines">{c.affectedOrderLinesSha256.slice(0, 16)}…</code>
              </div>

              <div className="kpis">
                <Kpi label="Ingredient lots" value={sim.scope.ingredientLots} />
                <Kpi label="WIP / blends" value={sim.scope.wipLots} />
                <Kpi label="Finished batches" value={sim.scope.finishedBatches} />
                <Kpi label="Order lines" value={sim.scope.orderLines} tone="bad" />
                <Kpi label="Orders" value={sim.scope.orders} />
                <Kpi label="Customers" value={sim.scope.customers} />
                <Kpi label="Qty dispatched" value={sim.scope.quantityDispatched} />
                <Kpi label="Traversal" value={`${sim.scope.traversalMillis} ms`} hint={`depth ${sim.scope.maxDepth}`} />
              </div>

              <Card title="2 · Completeness proof">
                <div className="checks">
                  <Check ok={c.bfsEqualsSql} label="Two independent traversals agree"
                    detail={`Java BFS reached ${c.affectedNodesBfs} nodes, SQL recursive CTE reached ${c.affectedNodesSql}`} />
                  <Check ok={c.closureClosed} label="Affected set is closed" detail="No lineage edge in the whole table leaves the affected set" />
                  <Check ok={c.massBalanced} label="Supplier lot quantity fully accounted" detail="Declared quantity = sum of receipts into tracked internal lots" />
                  <Check ok={c.unresolvedRelated === 0} label="No related unresolved records"
                    detail={c.unresolvedRelated ? `${c.unresolvedRelated} record(s) in the reconciliation queue could belong to this recall` : `${c.unresolvedTotal} open record(s) overall, none related`} />
                </div>
                <ul className="notes">{c.notes.map((n, i) => <li key={i}>{n}</li>)}</ul>
                {!c.verified && c.unresolvedRelated > 0 && <button onClick={() => go('reconciliation')}>Open reconciliation queue →</button>}
              </Card>

              <Card title="3 · Explainable lineage">
                <LineageGraph graph={sim.graph} />
              </Card>

              <Card title={`4 · Affected order lines (${sim.affectedLines.length})`}>
                <Table rows={sim.affectedLines} rowKey={l => l.orderNo + l.lineNo}
                  onRow={l => setOpenPath(openPath === l.orderNo + l.lineNo ? null : l.orderNo + l.lineNo)}
                  columns={[
                    { label: 'Order / line', render: l => <b>{l.orderNo}/{l.lineNo}</b> },
                    { label: 'Customer', render: l => <>{l.customerCode}<div className="muted">{l.customerName}</div></> },
                    { label: 'SKU', key: 'skuCode' },
                    { label: 'Finished lot', key: 'finishedLotCode' },
                    { label: 'Qty', render: l => `${l.quantity ?? ''} ${l.uom ?? ''}`, num: true },
                    { label: 'Dispatched', key: 'dispatchDate' },
                    { label: 'Region', key: 'region' },
                    { label: 'Hops', key: 'depth', num: true },
                    { label: 'Why it is affected', render: l => openPath === l.orderNo + l.lineNo
                        ? <div className="paths">{l.paths.map((p, i) => <div key={i}>{p}</div>)}</div>
                        : <span className="link">{l.paths.length} path{l.paths.length > 1 ? 's' : ''} ▸</span> },
                  ]} />
              </Card>

              <Card title="5 · Raise the recall">
                <div className="form-row">
                  <label>Reason<input value={reason} onChange={e => setReason(e.target.value)} /></label>
                  <label>Severity
                    <select value={severity} onChange={e => setSeverity(e.target.value)}>
                      <option>CLASS_I</option><option>CLASS_II</option><option>CLASS_III</option>
                    </select>
                  </label>
                  <button disabled={busy} className="danger" onClick={() => commit(false)}>Commit recall &amp; create notifications</button>
                  <button disabled={busy} className="secondary" onClick={() => commit(true)}>Save as simulation only</button>
                </div>
                {!c.verified && <p className="warn-text">Completeness is not proven. You can still raise the recall (speed matters), then recompute it after reconciliation - new customers will be added to the notification list.</p>}
              </Card>
            </>
          )}
          {busy && <div className="busy">Tracing…</div>}
        </div>
      </div>
    </>
  )
}
