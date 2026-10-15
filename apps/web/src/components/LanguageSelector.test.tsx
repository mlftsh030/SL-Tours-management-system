import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { LanguageSelector } from "./LanguageSelector";

describe("language selector", () => {
  it("comes back after a page without it (sign-in) was visited", () => {
    const drawn = vi.fn();
    const win = window as unknown as { google: unknown; googleTranslateElementInit?: () => void };
    win.google = { translate: { TranslateElement: Object.assign(function (this: unknown, _options: unknown, id: string) { drawn(id); }, { InlineLayout: { SIMPLE: 0 } }) } };

    const first = render(<LanguageSelector />);
    win.googleTranslateElementInit?.(); // Google's script has loaded.
    expect(drawn).toHaveBeenCalledTimes(1);
    const widget = first.container.querySelector("#google_translate_element");
    expect(widget).not.toBeNull();
    first.unmount(); // e.g. /login, which uses its own layout

    const again = render(<LanguageSelector />);
    // Google allows one widget per page, so the same widget element returns rather than a second widget being drawn.
    expect(drawn).toHaveBeenCalledTimes(1);
    expect(again.container.querySelector("#google_translate_element")).toBe(widget);
    expect(document.querySelectorAll("script[src*='translate.google.com']")).toHaveLength(1);
  });
});
