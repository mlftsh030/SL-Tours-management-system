import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { CustomerReviews } from "./CustomerReviews";

const widgetScript = () => document.querySelector<HTMLScriptElement>("script[src*='elfsight']");
afterEach(() => widgetScript()?.remove());

describe("customer reviews", () => {
  it("offers the Google listing instead of an empty box when the review widget cannot load", () => {
    render(<CustomerReviews />);
    expect(screen.queryByRole("button", { name: "Show all reviews" })).not.toBeInTheDocument();
    act(() => { widgetScript()!.onerror?.(new Event("error")); }); // blocked by an ad blocker, or the service is down
    expect(screen.getByRole("heading", { name: "What Our Customers Say" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Read them on Google" })).toHaveAttribute("href", expect.not.stringContaining("/review"));
    expect(screen.queryByRole("button", { name: "Show all reviews" })).not.toBeInTheDocument();
  });

  it("shows the expand button once the widget has drawn its reviews", async () => {
    const { container } = render(<CustomerReviews />);
    await act(async () => {
      container.querySelector("[data-elfsight-app-lazy]")!.appendChild(document.createElement("div"));
      await Promise.resolve();
    });
    expect(await screen.findByRole("button", { name: "Show all reviews" })).toBeInTheDocument();
    expect(screen.queryByText(/could not be shown/)).not.toBeInTheDocument();
  });
});
