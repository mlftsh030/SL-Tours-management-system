import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { getOperationsDashboard } from "../../lib/api";
import { formatAmount as formatMoney } from "../../lib/format";
import { LoadingState, ErrorState } from "../../components/StateBlocks";
export function DashboardPanel() {
  const data = useQuery({ queryKey: ["admin", "dashboard"], queryFn: getOperationsDashboard, retry: false });
  return <section><div className="admin-heading"><div><p className="eyebrow">Overview</p><h1>Dashboard</h1><p>Your bookings and operations at a glance.</p></div><Link className="button button-primary" to="/operations/tours">Manage tours</Link></div>
    {data.isLoading && <LoadingState label="Loading dashboard" />}
    {data.isError && <ErrorState message={data.error.message} />}
    {data.data && <div className="admin-metrics">{[
      ["New enquiries", data.data.newEnquiries], ["Bookings today", data.data.bookingsToday], ["New booking requests", data.data.enquiries],
      ["Awaiting payment", data.data.awaitingPayment], ["Confirmed bookings", data.data.confirmedBookings],
      ["Trips needing attention", data.data.tripsNeedingAttention], ["Private-trip requests", data.data.privateTripRequests], ["Inspections to review", data.data.inspectionsAwaitingReview],
      ["Vehicles restricted", data.data.openVehicleRestrictions], ["Vehicles in service", data.data.activeVehicles],
      ["Booking value", formatMoney(data.data.revenueCents)]
    ].map(([label, value]) => <article className="admin-metric" key={label}><span>{label}</span><strong>{value}</strong></article>)}</div>}
    <div className="admin-shortcuts"><Link to="/operations/enquiries"><h2>Enquiries inbox</h2><p>Reply to website enquiries, add notes and convert them to bookings.</p></Link><Link to="/operations/bookings"><h2>Customer bookings</h2><p>Review requests, pickup details, and booking status.</p></Link><Link to="/operations/trips"><h2>Trips &amp; inspections</h2><p>Approve vehicle releases, compare collection and return condition.</p></Link><Link to="/operations/vehicles"><h2>Vehicles</h2><p>Fleet status, website listings and safety or maintenance restrictions.</p></Link><Link to="/operations/customers"><h2>Customers & accounts</h2><p>See guest customers, registered accounts, and active sessions.</p></Link></div>
  </section>;
}
