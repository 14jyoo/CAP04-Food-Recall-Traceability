import { useState } from 'react'
import { api } from '../api'
import LineageGraph from '../LineageGraph'
import { Badge, Card, ErrorBox, Table, statusTone } from '../ui'

export default function AffectedOrders() {
  const [orderNo, setOrderNo] = useState('')
  const [customer, setCustomer] = useState('')
  const [rows, setRows] = useState(null)
  const [trace, setTrace] = useState(null)
  const [error, setError] = useState(null)

  const search = async (e) => {
    e?.preventDefault()
    setError(null); setTrace(null)
    try {
      setRows(await api.affected({ ...(orderNo && { orderNo }), ...(customer && { customer }) }))
      if (orderNo) setTrace(await api.traceOrder(orderNo))
    } catch (ex) { setError(ex.message) }
  }

  return (
    <>
      <ErrorBox error={error} />
      <Card title="Affected-order search">
        <form className="form-row" onSubmit={search}>
          <label>Order no.<input value={orderNo} onChange={e => setOrderNo(e.target.value)} placeholder="ORD-1003" /></label>
          <label>Customer code<input value={customer} onChange={e => setCustomer(e.target.value)} placeholder="CUST-RT-01" /></label>
          <button type="submit">Search</button>
          <button type="button" className="secondary" onClick={() => { setOrderNo(''); setCustomer(''); setTimeout(search, 0) }}>All affected</button>
        </form>
        <p className="muted small">Searches every committed recall. Enter an order number to also trace it <i>backwards</i> to every supplier lot that fed it.</p>
        {rows && (
          <Table rows={rows} empty="This order/customer is not in any recall." columns={[
            { label: 'Recall', render: r => <><b>{r.recallRef}</b> <Badge tone={statusTone(r.severity)}>{r.severity}</Badge></> },
            { label: 'Status', render: r => <Badge tone={statusTone(r.recallStatus)}>{r.recallStatus}</Badge> },
            { label: 'Order / line', render: r => `${r.orderNo}/${r.lineNo}` },
            { label: 'Customer', render: r => <>{r.customerCode}<div className="muted">{r.customerName}</div></> },
            { label: 'SKU', key: 'skuCode' },
            { label: 'Finished lot', key: 'finishedLotCode' },
            { label: 'Qty', key: 'quantity', num: true },
            { label: 'Dispatched', key: 'dispatchDate' },
            { label: 'Path', render: r => <div className="paths small">{(r.lineagePath || '').split(' || ')[0]}</div> },
          ]} />
        )}
      </Card>

      {trace && trace.lines.map(({ line, lineage }) => (
        <Card key={line.id} title={`Trace back · ${line.orderNo}/${line.lineNo} · ${line.skuCode} → ${lineage.nodes.filter(n => n.type === 'SUPPLIER_LOT').length} supplier lot(s)`}>
          <LineageGraph graph={lineage} height={380} />
        </Card>
      ))}
    </>
  )
}

