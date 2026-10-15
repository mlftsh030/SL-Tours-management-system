import { Link } from "react-router-dom";

/** An empty list with somewhere useful to go next. */
export function EmptyAction({ title, text, actions }: { title: string; text: string; actions: Array<{ to: string; label: string; primary?: boolean }> }) {
  return <div className="form-surface account-empty">
    <h3>{title}</h3><p>{text}</p>
    <div className="account-empty-actions">{actions.map(a => <Link key={a.to} to={a.to} className={`button ${a.primary ? "button-primary" : "button-secondary"}`}>{a.label}</Link>)}</div>
  </div>;
}
