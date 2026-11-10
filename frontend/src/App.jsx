import { useState } from 'react'
import Dashboard from './pages/Dashboard'
import Ingestion from './pages/Ingestion'
import RecallSimulator from './pages/RecallSimulator'
import AffectedOrders from './pages/AffectedOrders'
import Reconciliation from './pages/Reconciliation'
import Notifications from './pages/Notifications'
import MasterData from './pages/MasterData'

const PAGES = [
  { id: 'dashboard', label: 'Recall analytics', icon: '▦', C: Dashboard },
  { id: 'recall', label: 'Recall simulator', icon: '⟿', C: RecallSimulator },
  { id: 'affected', label: 'Affected orders', icon: '⌕', C: AffectedOrders },
  { id: 'notifications', label: 'Notifications', icon: '✉', C: Notifications },
  { id: 'reconciliation', label: 'Reconciliation', icon: '⇄', C: Reconciliation },
  { id: 'ingestion', label: 'Ingestion', icon: '⇪', C: Ingestion },
  { id: 'master', label: 'Lots & suppliers', icon: '☰', C: MasterData },
]

export default function App() {
  const [page, setPage] = useState('dashboard')
  const [params, setParams] = useState({})
  const [nonce, setNonce] = useState(0)
  const go = (id, p = {}) => { setPage(id); setParams(p); setNonce(n => n + 1) }
  const P = PAGES.find(p => p.id === page)

  return (
    <div className="app">
      <aside>
        <div className="brand">
          <div className="logo">RT</div>
          <div><b>Recall Trace</b><div className="small">Supplier impact analyzer</div></div>
        </div>
        <nav>
          {PAGES.map(p => (
            <button key={p.id} className={p.id === page ? 'active' : ''} onClick={() => go(p.id)}>
              <span className="icon">{p.icon}</span>{p.label}
            </button>
          ))}
        </nav>
        <div className="aside-foot small">CAP-04 · supplier lot → internal lot → finished SKU → order line</div>
      </aside>
      <main>
        <h1>{P.label}</h1>
        <P.C key={`${page}-${nonce}`} go={go} params={params} />
      </main>
    </div>
  )
}
