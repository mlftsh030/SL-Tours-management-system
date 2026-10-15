import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { GalleryPage } from "./GalleryPage";
import { galleryPhotos } from "./photos";

function renderGallery() {
  render(
    <MemoryRouter>
      <GalleryPage />
    </MemoryRouter>
  );
}

describe("GalleryPage", () => {
  it("lists every tour photo with descriptive text", () => {
    renderGallery();

    const buttons = screen.getAllByRole("button", { name: /^Open photo:/ });
    expect(buttons).toHaveLength(galleryPhotos.length);
    expect(galleryPhotos.every((photo) => photo.alt.length > 10)).toBe(true);
  });

  it("opens the lightbox on the chosen photo, navigates with arrow keys, and returns focus to the last photo viewed", async () => {
    const user = userEvent.setup();
    renderGallery();

    const penguins = galleryPhotos.findIndex((photo) => photo.id === 43);
    const trigger = screen.getByRole("button", { name: `Open photo: ${galleryPhotos[penguins].alt}` });
    await user.click(trigger);

    const viewer = screen.getByRole("dialog", { name: "Photo viewer" });
    expect(screen.getByRole("img", { name: galleryPhotos[penguins].alt })).toHaveAttribute("src", "/assets/gallery/43.jpg");
    expect(viewer).toHaveTextContent(`${penguins + 1} / ${galleryPhotos.length}`);

    fireEvent.keyDown(viewer, { key: "ArrowRight" });
    expect(screen.getByRole("img", { name: galleryPhotos[penguins + 1].alt })).toBeInTheDocument();

    fireEvent.keyDown(viewer, { key: "ArrowLeft" });
    fireEvent.keyDown(viewer, { key: "ArrowLeft" });
    expect(screen.getByRole("img", { name: galleryPhotos[penguins - 1].alt })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Close photo viewer" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: `Open photo: ${galleryPhotos[penguins - 1].alt}` })).toHaveFocus();
  });

  it("wraps from the last photo to the first", async () => {
    const user = userEvent.setup();
    renderGallery();

    const last = galleryPhotos[galleryPhotos.length - 1];
    await user.click(screen.getByRole("button", { name: `Open photo: ${last.alt}` }));
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "ArrowRight" });

    expect(screen.getByRole("img", { name: galleryPhotos[0].alt })).toBeInTheDocument();
  });
});
