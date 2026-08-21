const MONTHS = ["January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"];

/**
 * Long form for display: "3rd August, 2026".
 *
 * Renders in the viewer's local timezone rather than UTC, so a late-evening transaction shows the
 * date the customer remembers making it. Statements and audit records keep the ISO instant.
 */
export function formatDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso.slice(0, 10);
  const day = d.getDate();
  return `${day}${ordinal(day)} ${MONTHS[d.getMonth()]}, ${d.getFullYear()}`;
}

/** 11th, 12th and 13th are the exceptions - they take "th" despite ending 1, 2, 3. */
function ordinal(day: number): string {
  if (day > 3 && day < 21) return "th";
  switch (day % 10) {
    case 1: return "st";
    case 2: return "nd";
    case 3: return "rd";
    default: return "th";
  }
}
