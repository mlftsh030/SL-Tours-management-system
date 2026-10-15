import { useQuery } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { displayDate } from "../billing/money";
import { enquiryStatusLabels, enquiryTypeLabels, formatDateTime, listEnquiries } from "./accountApi";
import { EmptyAction } from "./EmptyAction";

export function AccountEnquiries() {
  const enquiries = useQuery({ queryKey: ["account", "enquiries"], queryFn: listEnquiries, retry: false });
  return <section aria-labelledby="enquiries-title">
    <h2 id="enquiries-title">Enquiries</h2>
    {enquiries.isLoading && <LoadingState label="Loading your enquiries" />}
    {enquiries.isError && <ErrorState message={enquiries.error.message} />}
    {enquiries.data && !enquiries.data.length && <EmptyAction title="No enquiries yet" text="Ask about a tour or request a quote for a private trip. We usually reply within a day."
      actions={[{ to: "/book-private-trip", label: "Request a quote", primary: true }, { to: "/contact", label: "Send an enquiry" }]} />}
    <ul className="account-list">{enquiries.data?.map(e => <li key={e.reference} className="form-surface">
      <div className="account-item-head"><h3>{e.tourTitle ?? enquiryTypeLabels[e.type]}</h3>
        <span className={`admin-badge account-status-${e.status.toLowerCase()}`}>{enquiryStatusLabels[e.status]}</span></div>
      <dl className="account-facts">
        <div><dt>Reference</dt><dd>{e.reference}</dd></div>
        <div><dt>Sent</dt><dd>{formatDateTime(e.createdAt)}</dd></div>
        {e.requestedDate && <div><dt>Travel date</dt><dd>{displayDate(e.requestedDate)}</dd></div>}
        {e.passengers && <div><dt>People</dt><dd>{e.passengers}</dd></div>}
        {e.bookingReference && <div><dt>Booking</dt><dd>{e.bookingReference}</dd></div>}
      </dl>
      <p className="account-message">{e.message}</p>
    </li>)}</ul>
  </section>;
}
