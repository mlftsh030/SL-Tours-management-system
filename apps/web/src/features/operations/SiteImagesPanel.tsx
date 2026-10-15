import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { deleteSiteImage, listSiteImages, updateSiteImage, uploadSiteImage } from "./fleetApi";
import type { SiteImage } from "../../types/api";

const slots = {
  HOME_HERO: { label: "Homepage slider", help: "The built-in slider stays live until you activate at least one image here. Active images then replace it, in order." },
  GALLERY: { label: "Gallery", help: "Active images appear at the start of the gallery, before the existing photos." }
} as const;

export function SiteImagesPanel() {
  const [slot, setSlot] = useState<keyof typeof slots>("HOME_HERO");
  const client = useQueryClient();
  const images = useQuery({ queryKey: ["admin", "site-images", slot], queryFn: () => listSiteImages(slot), retry: false });
  const refresh = async () => { await client.invalidateQueries({ queryKey: ["admin", "site-images", slot] }); await client.invalidateQueries({ queryKey: ["site-images"] }); };
  const [file, setFile] = useState<File | null>(null); const [alt, setAlt] = useState("");
  const upload = useMutation({ mutationFn: () => uploadSiteImage(slot, file!, alt), onSuccess: async () => { setFile(null); setAlt(""); await refresh(); } });
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Website</p><h1>Website images</h1><p>Replace, order and describe public images. New uploads stay hidden until you activate them.</p></div>
      <label>Area<select value={slot} onChange={event => setSlot(event.target.value as keyof typeof slots)}>{Object.entries(slots).map(([key, s]) => <option key={key} value={key}>{s.label}</option>)}</select></label></div>
    <p className="form-note">{slots[slot].help}</p>
    <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); if (file) upload.mutate(); }}>
      <label>Image<input type="file" accept="image/jpeg,image/png,image/webp" onChange={event => setFile(event.target.files?.[0] ?? null)} required /></label>
      <label className="admin-grow">Alternative text<input value={alt} onChange={event => setAlt(event.target.value)} maxLength={300} required placeholder="Describe what the image shows" /></label>
      <button className="button button-primary" type="submit" disabled={!file || !alt.trim() || upload.isPending}>{upload.isPending ? "Uploading…" : "Upload"}</button>
    </form>
    {upload.isError && <ErrorState message={upload.error.message} />}
    {images.isLoading && <LoadingState label="Loading images" />}{images.isError && <ErrorState message={images.error.message} />}
    {images.data && <div className="admin-site-images">{images.data.map(image => <SiteImageEditor key={image.id + image.version} image={image} hero={slot === "HOME_HERO"} onChanged={refresh} />)}
      {!images.data.length && <p className="admin-empty">No uploaded images. The website is using its built-in images.</p>}</div>}
  </section>;
}

function SiteImageEditor({ image, hero, onChanged }: { image: SiteImage; hero: boolean; onChanged: () => Promise<void> }) {
  const [draft, setDraft] = useState(image);
  const save = useMutation({ mutationFn: () => updateSiteImage(image.id, draft), onSuccess: onChanged });
  const remove = useMutation({ mutationFn: () => deleteSiteImage(image.id), onSuccess: onChanged });
  const set = (field: keyof SiteImage) => (event: { target: { value: string } }) => setDraft(current => ({ ...current, [field]: event.target.value }));
  return <article className="form-surface admin-site-image">
    <img src={image.url} alt={image.altText} loading="lazy" />
    <div className="form-grid">
      <label>Alternative text<input value={draft.altText} onChange={set("altText")} maxLength={300} required /></label>
      {hero && <><label>Headline<input value={draft.title ?? ""} onChange={set("title")} maxLength={160} /></label>
        <label>Second line<input value={draft.subtitle ?? ""} onChange={set("subtitle")} maxLength={160} /></label>
        <label>Supporting text<input value={draft.body ?? ""} onChange={set("body")} maxLength={300} /></label></>}
      <label>Order<input type="number" min={0} max={1000} value={draft.sortOrder} onChange={event => setDraft(c => ({ ...c, sortOrder: Number(event.target.value) }))} /></label>
      <label className="admin-check"><input type="checkbox" checked={draft.active} onChange={event => setDraft(c => ({ ...c, active: event.target.checked }))} />Show on the website</label>
    </div>
    <div className="admin-actions"><button className="button button-primary" onClick={() => save.mutate()} disabled={save.isPending || !draft.altText.trim()}>Save</button>
      <button className="button button-quiet" onClick={() => { if (window.confirm("Remove this image permanently?")) remove.mutate(); }} disabled={remove.isPending}>Remove</button></div>
    {(save.isError || remove.isError) && <ErrorState message={(save.error ?? remove.error)!.message} />}
  </article>;
}
