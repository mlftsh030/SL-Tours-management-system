import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { InspectionForm } from "./InspectionForm";

const checklist = [{ item: "BRAKES", label: "Brakes and parking brake", safetyCritical: true }, { item: "MIRRORS", label: "Mirrors", safetyCritical: false }];
const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
const readBlob = (blob: Blob) => new Promise<string>(resolve => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.readAsText(blob); });
const image = (name: string) => new File([new Uint8Array([0xff, 0xd8, 0xff])], name, { type: "image/jpeg" });

// jsdom has no object URLs; previews only need a placeholder.
beforeEach(() => { URL.createObjectURL = vi.fn(() => "blob:preview"); URL.revokeObjectURL = vi.fn(); });
afterEach(() => vi.unstubAllGlobals());

describe("driver collection inspection", () => {
  it("requires every check, defect photo and selfie, then uploads tagged photos as one multipart request", async () => {
    let upload: FormData | null = null;
    vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
      if (url.endsWith("/checklist")) return json(checklist);
      if (url.endsWith("/collection")) { upload = options?.body as FormData; return json({ inspectionId: "i", type: "COLLECTION", assignmentStatus: "AWAITING_RELEASE", outcome: "Submitted" }, 201); }
      return Promise.reject(new Error(url));
    }));
    const done = vi.fn();
    const { container } = render(<QueryClientProvider client={new QueryClient()}>
      <InspectionForm assignmentId="a-1" kind="collection" minimumPhotos={4} collectionOdometer={null} onDone={done} onCancel={() => undefined} />
    </QueryClientProvider>);
    await screen.findByText("Mirrors");
    const submit = screen.getByRole("button", { name: "Submit collection inspection" });
    await userEvent.click(submit);
    expect(screen.getByRole("alert")).toHaveTextContent("Still to do: Brakes and parking brake, Mirrors");

    await userEvent.click(screen.getAllByRole("button", { name: "OK" })[0]);
    const mirrors = screen.getByRole("group", { name: "Mirrors" });
    await userEvent.click(mirrors.querySelectorAll("button")[1] as HTMLElement);
    await userEvent.type(screen.getByLabelText("What is wrong?"), "Cracked left mirror");
    await userEvent.click(submit);
    expect(screen.getByRole("alert")).toHaveTextContent("Take a photo of: Mirrors");

    const files = container.querySelectorAll<HTMLInputElement>("input[type=file]");
    // defect photo, four vehicle angles, selfie
    await userEvent.upload(files[0], image("defect.jpg"));
    for (let i = 1; i <= 4; i++) await userEvent.upload(files[i], image(`angle-${i}.jpg`));
    await userEvent.click(submit);
    expect(screen.getByRole("alert")).toHaveTextContent("Take your collection selfie");
    await userEvent.upload(files[5], image("selfie.jpg"));
    await userEvent.type(screen.getByLabelText(/Odometer/), "45210");
    await userEvent.click(screen.getByRole("button", { name: "½" }));
    await userEvent.click(submit);

    await waitFor(() => expect(done).toHaveBeenCalled());
    const form = upload as unknown as FormData;
    const inspection = JSON.parse(await readBlob(form.get("inspection") as Blob));
    expect(inspection).toMatchObject({ odometerKm: 45210, fuelLevel: "HALF", photoTags: ["VEHICLE", "VEHICLE", "VEHICLE", "VEHICLE", "MIRRORS"] });
    expect(inspection.checklist).toEqual([
      { item: "BRAKES", result: "OK", severity: null, note: null },
      { item: "MIRRORS", result: "DEFECT", severity: "MINOR", note: "Cracked left mirror" }]);
    expect(form.getAll("photos")).toHaveLength(5);
    expect((form.get("selfie") as File).name).toBe("selfie.jpg");
  });
});
