import { describe, expect, it } from "vitest";
import { wallClock, capeTown, dayAndTime, localDate } from "./dates";

describe("dayAndTime", () => {
  const now = new Date(2026, 9, 4, 12, 0);
  it("leaves out the year for dates this year", () => {
    expect(dayAndTime(new Date(2026, 9, 10, 8, 0).toISOString(), now)).not.toMatch(/2026/);
  });
  it("shows the year for dates in another year", () => {
    expect(dayAndTime(new Date(2027, 2, 24, 9, 0).toISOString(), now)).toMatch(/2027/);
  });
});

describe("Cape Town time", () => {
  it("shows trip times in Cape Town whatever the browser's time zone", () => {
    // 07:00 UTC is 09:00 in Cape Town (UTC+2); the test machine's own zone must not matter.
    expect(capeTown("2026-11-01T07:00:00Z", { hour: "2-digit", minute: "2-digit" })).toBe("09:00");
    expect(capeTown("2026-11-01T23:30:00Z", { day: "numeric", month: "short" })).toMatch(/^0?2 Nov$/);
  });
  it("shows an entered Cape Town date and time unchanged, even where that time does not exist locally", () => {
    // 02:30 on 8 March 2026 falls in New York's daylight-saving gap; run with TZ=America/New_York to see the old bug.
    expect(wallClock("2026-03-08", "02:30", { hour: "2-digit", minute: "2-digit" })).toBe("02:30");
    expect(wallClock("2026-11-01", "23:30", { day: "numeric", month: "short" })).toMatch(/^0?1 Nov$/);
  });
  it("picks today's date as it is in Cape Town", () => {
    expect(localDate(0, new Date("2026-11-01T22:30:00Z"))).toBe("2026-11-02");
    expect(localDate(1, new Date("2026-12-31T21:00:00Z"))).toBe("2027-01-01");
    expect(localDate(1, new Date("2026-12-31T23:00:00Z"))).toBe("2027-01-02");
  });
});
