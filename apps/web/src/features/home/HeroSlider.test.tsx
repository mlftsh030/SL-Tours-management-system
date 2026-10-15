import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HeroSlider } from "./HeroSlider";

vi.mock("../../lib/useSiteImages", () => ({ useSiteImages: () => [] }));
afterEach(() => vi.useRealTimers());

const activeSlide = () => screen.getAllByRole("group").find(g => g.getAttribute("aria-hidden") === "false");

describe("homepage hero", () => {
  it("rotates until the visitor presses pause, and stays paused until play", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    render(<MemoryRouter><HeroSlider /></MemoryRouter>);
    expect(activeSlide()).toHaveAttribute("aria-label", "1 of 3");
    act(() => { vi.advanceTimersByTime(6100); });
    expect(activeSlide()).toHaveAttribute("aria-label", "2 of 3");

    await userEvent.click(screen.getByRole("button", { name: "Pause slideshow" }));
    act(() => { vi.advanceTimersByTime(20000); });
    expect(activeSlide()).toHaveAttribute("aria-label", "2 of 3");
    expect(screen.getByRole("button", { name: "Play slideshow" })).toBeInTheDocument();
  });

  it("downloads a slide's photo only when it is on show or next, so the first visit skips the last one", async () => {
    render(<MemoryRouter><HeroSlider /></MemoryRouter>);
    const photos = () => [...document.querySelectorAll<HTMLElement>(".hero-slide-image")].map(e => e.style.backgroundImage);
    expect(photos()).toEqual([expect.stringContaining("slider-1"), expect.stringContaining("slider-3"), ""]);
    await userEvent.click(screen.getByRole("button", { name: "Previous slide" }));
    expect(photos()[2]).toContain("slider-2");
  });

  it("promises nothing a tour does not include", () => {
    render(<MemoryRouter><HeroSlider /></MemoryRouter>);
    const text = document.body.textContent ?? "";
    expect(text).not.toMatch(/free/i);
    expect(screen.getByRole("link", { name: "See the Winelands Tour", hidden: true })).toHaveAttribute("href", "/experiences/cape-winelands-escape");
  });
});
