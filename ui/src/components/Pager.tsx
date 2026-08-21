/**
 * Numbered pagination. Shows page-number buttons with ‹ / › arrows, collapsing long ranges with
 * ellipses so the control stays compact (1 … 4 5 [6] 7 8 … 20). Pages are zero-indexed in state
 * but shown one-indexed. Renders nothing for a single page.
 */
export function Pager({ page, totalPages, onChange }:
    { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;

  const items = pageItems(page, totalPages);

  return (
    <nav className="pager" aria-label="Pagination">
      <button className="pager__nav" disabled={page === 0}
              onClick={() => onChange(page - 1)} aria-label="Previous page">‹</button>
      {items.map((it, i) =>
        it === "…"
          ? <span key={`gap-${i}`} className="pager__gap" aria-hidden="true">…</span>
          : <button key={it}
                    className={"pager__num" + (it === page ? " pager__num--active" : "")}
                    aria-current={it === page ? "page" : undefined}
                    onClick={() => onChange(it)}>{it + 1}</button>)}
      <button className="pager__nav" disabled={page + 1 >= totalPages}
              onClick={() => onChange(page + 1)} aria-label="Next page">›</button>
    </nav>
  );
}

/** The visible page indices, with "…" markers where the range is elided. */
function pageItems(page: number, total: number): (number | "…")[] {
  const around = 1;                    // pages either side of the current one
  const pages = new Set<number>([0, total - 1, page]);
  for (let d = 1; d <= around; d++) {
    if (page - d >= 0) pages.add(page - d);
    if (page + d < total) pages.add(page + d);
  }
  const sorted = [...pages].sort((a, b) => a - b);

  const out: (number | "…")[] = [];
  let prev = -1;
  for (const p of sorted) {
    if (prev !== -1 && p - prev > 1) out.push("…");
    out.push(p);
    prev = p;
  }
  return out;
}