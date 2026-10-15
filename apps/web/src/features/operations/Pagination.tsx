export function Pagination({ page, totalPages, totalElements, onPage }: {
  page: number; totalPages: number; totalElements: number; onPage: (page: number) => void;
}) {
  return <div className="admin-pagination">
    <span>{totalElements} results · Page {page + 1} of {Math.max(1, totalPages)}</span>
    <button className="button button-secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button>
    <button className="button button-secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button>
  </div>;
}
