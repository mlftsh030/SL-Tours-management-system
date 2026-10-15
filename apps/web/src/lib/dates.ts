/** SL Tours operates in Cape Town: trip times are always shown in this zone, whatever the visitor's own time zone. */
export const CAPE_TOWN_ZONE = "Africa/Johannesburg";

/** Calendar date in Cape Town as YYYY-MM-DD, plus offsetDays. Used for date pickers, so a visitor abroad cannot pick a
 *  day that has already ended in Cape Town. */
export function localDate(offsetDays = 0, now = new Date()) {
  const [y, m, d] = new Intl.DateTimeFormat("en-CA", { timeZone: CAPE_TOWN_ZONE, year: "numeric", month: "2-digit", day: "2-digit" })
    .format(now).split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + offsetDays));
  return date.toISOString().slice(0, 10);
}

/** Formats an instant (a timestamp with an offset) in Cape Town time. Not for date-only values such as "2026-11-01". */
export function capeTown(iso: string, options: Intl.DateTimeFormatOptions) {
  return new Date(iso).toLocaleString("en-ZA", { ...options, timeZone: CAPE_TOWN_ZONE });
}

/** Formats a Cape Town date and time as entered (e.g. "2026-11-01" and "08:00") without converting it: it is a wall-clock
 *  value, not an instant, so the visitor's own zone (and its daylight-saving gaps) must not shift it. */
export function wallClock(date: string, time: string, options: Intl.DateTimeFormatOptions) {
  return new Date(`${date}T${time}:00Z`).toLocaleString("en-ZA", { ...options, timeZone: "UTC" });
}

/** Day and time, e.g. "Sat, 10 Oct, 08:00"; the year is added when it is not the current year, so trips months ahead are not misread. */
export function dayAndTime(iso: string, now = new Date()) {
  const year = (value: Date) => new Intl.DateTimeFormat("en-ZA", { timeZone: CAPE_TOWN_ZONE, year: "numeric" }).format(value);
  return capeTown(iso, { weekday: "short", day: "numeric", month: "short",
    ...(year(new Date(iso)) !== year(now) ? { year: "numeric" } : {}), hour: "2-digit", minute: "2-digit" });
}
