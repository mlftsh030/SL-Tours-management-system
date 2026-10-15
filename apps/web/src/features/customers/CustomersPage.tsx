import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, NavLink, Route, Routes } from "react-router-dom";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { Pagination } from "../operations/Pagination";
import { formatMoney } from "../billing/money";
import { dateTime } from "../operations/labels";
import { listDuplicates, searchCustomers, type CustomerSummary } from "./customersApi";
import { CustomerRecord } from "./CustomerRecord";

/** Customer records: search, full history, possible duplicates. Login accounts are shown but managed separately. */
export function CustomersPage({ isAdmin }: { isAdmin: boolean }) {
  return <Routes>
    <Route index element={<CustomerList />} />
    <Route path="duplicates" element={<Duplicates isAdmin={isAdmin} />} />
    <Route path=":id" element={<CustomerRecord isAdmin={isAdmin} />} />
  </Routes>;
}

function Tabs() {
  return <nav className="customer-tabs" aria-label="Customer views">
    <NavLink to="/operations/customers" end>All customers</NavLink>
    <NavLink to="/operations/customers/duplicates">Possible duplicates</NavLink>
  </nav>;
}

function CustomerList() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const data = useQuery({ queryKey: ["admin", "customers", query, page], queryFn: () => searchCustomers(query, page), retry: false });
  function submit(event: FormEvent) { event.preventDefault(); setQuery(search); setPage(0); }
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">People</p><h1>Customers</h1>
      <p>One record per customer, built from every enquiry, booking and invoice with their email address. Login accounts are linked only once the customer verifies that address.</p></div></div>
    <Tabs />
    <form className="admin-filters customer-search" role="search" onSubmit={submit}>
      <label>Search<input value={search} onChange={e => setSearch(e.target.value)} maxLength={120} placeholder="Name, email, phone, booking or enquiry reference, invoice number" /></label>
      <button className="button button-primary" type="submit">Search</button>
    </form>
    {data.isLoading && <LoadingState label="Loading customers" />}
    {data.isError && <ErrorState message={data.error.message} />}
    {data.data && <><div className="admin-table-wrap"><table><thead><tr><th>Customer</th><th>Contact</th><th>Login</th><th>Enquiries</th><th>Bookings</th><th>Outstanding</th><th>Last activity</th></tr></thead>
      <tbody>{data.data.content.map(c => <Row key={c.id} c={c} />)}</tbody></table>
      {!data.data.content.length && <p className="admin-empty">{query ? "No customer matches that search." : "No customers yet."}</p>}</div>
      <Pagination page={page} totalPages={data.data.totalPages} totalElements={data.data.totalElements} onPage={setPage} /></>}
  </section>;
}

function Row({ c }: { c: CustomerSummary }) {
  return <tr>
    <td><Link to={`/operations/customers/${c.id}`}>{c.name}</Link>{c.demoData && <span className="admin-badge status-draft">Sample</span>}</td>
    <td>{c.email ?? "—"}<small>{c.phone}</small></td>
    <td>{c.hasAccount ? <span className="admin-badge status-active">{c.activeSessions > 0 ? "Signed in now" : "Has login"}</span> : <span className="admin-badge">Guest</span>}</td>
    <td>{c.enquiries}{c.openEnquiries > 0 && <small>{c.openEnquiries} open</small>}</td>
    <td>{c.bookings}{c.upcomingBookings > 0 && <small>{c.upcomingBookings} upcoming</small>}</td>
    <td>{c.outstandingCents > 0 ? <strong>{formatMoney(c.outstandingCents)}</strong> : "—"}</td>
    <td>{c.lastActivityAt ? dateTime(c.lastActivityAt) : "—"}</td>
  </tr>;
}

function Duplicates({ isAdmin }: { isAdmin: boolean }) {
  const data = useQuery({ queryKey: ["admin", "customers", "duplicates"], queryFn: listDuplicates, retry: false });
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">People</p><h1>Possible duplicates</h1>
      <p>Customers with the same phone number or the same name but different email addresses. These are suggestions only: nothing is merged automatically.
        {isAdmin ? " Open a record to merge it into another after checking with the customer." : " An administrator can merge records."}</p></div></div>
    <Tabs />
    {data.isLoading && <LoadingState label="Looking for duplicates" />}
    {data.isError && <ErrorState message={data.error.message} />}
    {data.data && !data.data.length && <p className="admin-empty">No possible duplicates.</p>}
    {data.data?.map(g => <section key={g.reason + g.value} className="form-surface customer-duplicate">
      <h2>{g.reason === "SAME_PHONE" ? `Same phone number (+${g.value})` : `Same name: ${g.customers[0]?.name ?? g.value}`}</h2>
      <div className="admin-table-wrap"><table><thead><tr><th>Customer</th><th>Contact</th><th>Login</th><th>Enquiries</th><th>Bookings</th><th>Outstanding</th><th>Last activity</th></tr></thead>
        <tbody>{g.customers.map(c => <Row key={c.id} c={c} />)}</tbody></table></div>
    </section>)}
  </section>;
}
