import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { listAdminTours, saveAdminTour } from "./operationsApi";
import { TourEditor } from "./TourEditor";
import { Pagination } from "./Pagination";
import { LoadingState, ErrorState } from "../../components/StateBlocks";
import { formatAmount as formatMoney, priceLabel } from "../../lib/format";
import type { AdminTour } from "../../types/api";
export function ToursPanel({ canEdit }: { canEdit: boolean }) {
  const [page, setPage] = useState(0); const [editing, setEditing] = useState<AdminTour | "new" | null>(null);
  const client = useQueryClient();
  const data = useQuery({ queryKey: ["admin", "tours", page], queryFn: () => listAdminTours(page), retry: false });
  // Publishing sends the full current tour with the new status, so optimistic locking still applies.
  const publish = useMutation({
    mutationFn: ({ tour, status }: { tour: AdminTour; status: AdminTour["status"] }) =>
      saveAdminTour({ ...tour.detail, version: tour.version, status, displayOrder: tour.displayOrder, cardNote: tour.cardNote }, tour.id),
    onSuccess: () => saved()
  });
  async function saved() { setEditing(null); await client.invalidateQueries({ queryKey: ["admin", "tours"] }); await client.invalidateQueries({ queryKey: ["tours"] }); await client.invalidateQueries({ queryKey: ["tour"] }); }
  return <section><div className="admin-heading"><div><p className="eyebrow">Catalogue</p><h1>Tours & prices</h1><p>Manage the tours and ZAR prices shown on the public website.</p></div>{canEdit && <button className="button button-primary" onClick={() => setEditing("new")}>Add tour</button>}</div>
    {!canEdit && <p>Only administrators can add tours and change prices.</p>}
    {editing && <TourEditor key={editing === "new" ? "new" : `${editing.id}-${editing.version}`} tour={editing === "new" ? undefined : editing} onSaved={saved} onCancel={() => setEditing(null)} />}
    {publish.isError && <ErrorState message={publish.error.message} />}
    {data.isLoading && <LoadingState label="Loading tours" />}{data.isError && <ErrorState message={data.error.message} />}
    {data.data && <><div className="admin-table-wrap"><table><thead><tr><th>Tour</th><th>Category</th><th>Price (ZAR)</th><th>Guests</th><th>Visibility</th><th>Action</th></tr></thead><tbody>{data.data.content.map(tour => <tr key={tour.id}><td><div className="admin-tour-cell"><img src={tour.detail.heroImage} alt="" width="64" height="48" /><span>{tour.detail.title}<small>/{tour.detail.slug}</small></span></div></td><td>{tour.detail.category.replace(/_/g, " ")}</td><td>{formatMoney(tour.detail.basePriceCents)}<small>{priceLabel(tour.detail.pricingType)}</small></td><td>{tour.detail.minimumGuests}–{tour.detail.maximumGuests}</td><td><span className={`admin-badge status-${tour.status.toLowerCase()}`}>{tour.status}</span>{tour.detail.featured && <small>Featured</small>}</td><td>{canEdit && <button className="button button-secondary" onClick={() => setEditing(tour)}>Edit tour</button>}
              {canEdit && <button className="button button-quiet admin-publish" disabled={publish.isPending}
                onClick={() => publish.mutate({ tour, status: tour.status === "ACTIVE" ? "DRAFT" : "ACTIVE" })}>{tour.status === "ACTIVE" ? "Unpublish" : "Publish"}</button>}{tour.status === "ACTIVE" && <a className="admin-preview" href={`/experiences/${tour.detail.slug}`} target="_blank" rel="noopener noreferrer">View tour</a>}</td></tr>)}</tbody></table>{!data.data.content.length && <p className="admin-empty">No tours yet. Add your first tour.</p>}</div><Pagination page={page} totalPages={data.data.totalPages} totalElements={data.data.totalElements} onPage={setPage} /></>}
  </section>;
}
