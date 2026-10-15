import type { DocumentView } from "./billingTypes";

const logoUrl = (ref: string | null) => !ref ? null : ref.startsWith("media:") ? `/api/v1/admin/media/${ref.slice(6)}` : "/assets/sl-tours-logo-original.jpg";

/**
 * On-screen copy of the printed document, drawn from the same server-built view as the PDF and Word files. It is a
 * preview: page breaks are only decided in the PDF.
 */
export function DocumentPreview({ view, stale }: { view: DocumentView; stale?: boolean }) {
  const logo = logoUrl(view.company.logoRef);
  const tagline = view.company.tagline?.trim() ?? "";
  return <div className={`doc-paper${stale ? " is-stale" : ""}`} aria-label={`Preview of ${view.filenameBase}`} role="document">
    {view.watermark && <div className="doc-watermark" aria-hidden="true">{view.watermark}</div>}
    <header className="doc-masthead">
      {logo && <img src={logo} alt={`${view.company.name} logo`} />}
      {tagline && <p className="doc-tagline">{tagline.startsWith("SL Tours")
        ? <><span className="doc-sl">SL</span> <span className="doc-tours">Tours</span>{tagline.slice(8)}</> : tagline}</p>}
    </header>
    <h2 className="doc-title">{view.title}</h2>
    {view.watermark && <p className="doc-status">{view.watermark} – not valid for payment</p>}
    <table className="doc-table doc-info"><tbody>{view.info.map(r => <tr key={r.label}><td>{r.label}</td><td>{r.value}</td></tr>)}</tbody></table>
    <table className="doc-table doc-services">
      <thead><tr><th>DESCRIPTION</th><th>{view.costHeader}</th></tr></thead>
      <tbody>
        {view.items.map((item, i) => <tr key={i} className="doc-item"><td>
          {item.title && <p className="doc-item-title">{item.title}</p>}
          {item.pieces.map((p, j) => p.kind === "GAP" ? <p key={j} className="doc-gap" aria-hidden="true">&nbsp;</p>
            : p.kind === "TEXT" ? <p key={j}>{p.text}</p>
            : p.kind === "LABELLED" ? <p key={j}><b>{p.label}:</b> {p.text}</p>
            : <div key={j}>{p.label && <p><b>{p.label}:</b></p>}<ul>{p.bullets.map((b, k) => <li key={k}>{b}</li>)}</ul></div>)}
        </td><td>{item.amount}</td></tr>)}
        <tr className="doc-filler"><td /><td /></tr>
        {Array.from({ length: view.blankRows }, (_, i) => <tr key={`blank-${i}`} className="doc-sum"><td>&nbsp;</td><td /></tr>)}
        {view.totals.map(t => <tr key={t.label} className={`doc-sum${t.grand ? " doc-grand" : ""}`}><td>{t.label}</td><td>{t.amount}</td></tr>)}
      </tbody>
    </table>
    {view.notesHeading && <section><h3>{view.notesHeading}</h3>{view.notes.map((n, i) => <p key={i}>{n}</p>)}</section>}
    {view.bankingHeading && <section><h3>{view.bankingHeading}</h3>
      <table className="doc-table doc-bank"><tbody>{view.banking.map(r => <tr key={r.label}><td>{r.label}</td><td>{r.value}</td></tr>)}</tbody></table></section>}
    {view.termsHeading && <section><h3>{view.termsHeading}</h3><ul className="doc-terms">{view.terms.map((t, i) => <li key={i}>{t}</li>)}</ul></section>}
    <footer className="doc-footer">
      <div className="doc-strip" aria-hidden="true"><span /><span /></div>
      <p className="doc-company">{view.company.name}</p>
      <p>{view.company.phone && <><b>Mobile:</b> {view.company.phone} </>}
        {view.company.whatsapp && view.company.whatsapp !== view.company.phone && <><b>WhatsApp:</b> {view.company.whatsapp} </>}
        {view.company.email && <><b>Email:</b> {view.company.email}</>}</p>
      {view.company.website && <p><b>Website:</b> {view.company.website}</p>}
      <p><b>Address:</b> {view.company.address}</p>
    </footer>
  </div>;
}
