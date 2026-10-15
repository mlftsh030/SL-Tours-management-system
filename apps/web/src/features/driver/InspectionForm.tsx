import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Camera, CheckCircle2, TriangleAlert } from "lucide-react";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { getChecklist, submitInspection, type ChecklistAnswer } from "./driverApi";
import type { FuelLevel, SubmissionResult } from "../../types/api";

const fuelLevels: Array<[FuelLevel, string]> = [["EMPTY", "Empty"], ["QUARTER", "¼"], ["HALF", "½"], ["THREE_QUARTERS", "¾"], ["FULL", "Full"]];
const angles = ["Front", "Rear", "Left side", "Right side"];

function Preview({ file }: { file: File }) {
  // Created and revoked in the same effect, so a re-run (e.g. React strict mode) never leaves a revoked URL.
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    const next = URL.createObjectURL(file);
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [file]);
  return url ? <img src={url} alt="" className="driver-thumb" /> : null;
}

function PhotoInput({ label, file, onChange, capture = "environment" }: { label: string; file: File | null; onChange: (f: File | null) => void; capture?: "user" | "environment" }) {
  return <label className={`driver-photo-input${file ? " has-photo" : ""}`}>
    {file ? <Preview file={file} /> : <Camera aria-hidden="true" />}
    <span>{file ? `Retake: ${label.toLowerCase()}` : label}</span>
    <input type="file" accept="image/jpeg,image/png,image/webp" capture={capture} onChange={event => onChange(event.target.files?.[0] ?? null)} />
  </label>;
}

/** Collection or return inspection. Nothing is recorded until the server confirms the submission. */
export function InspectionForm({ assignmentId, kind, minimumPhotos, collectionOdometer, onDone, onCancel }: {
  assignmentId: string; kind: "collection" | "return"; minimumPhotos: number; collectionOdometer: number | null;
  onDone: (result: SubmissionResult) => void; onCancel: () => void;
}) {
  const checklist = useQuery({ queryKey: ["driver", "checklist"], queryFn: getChecklist, staleTime: Infinity });
  const [answers, setAnswers] = useState<Record<string, ChecklistAnswer>>({});
  const [defectPhotos, setDefectPhotos] = useState<Record<string, File | null>>({});
  const [overview, setOverview] = useState<Array<File | null>>(() => Array(Math.max(minimumPhotos, 4)).fill(null));
  const [selfie, setSelfie] = useState<File | null>(null);
  const [odometer, setOdometer] = useState(""); const [fuel, setFuel] = useState<FuelLevel | "">(""); const [notes, setNotes] = useState("");
  const [error, setError] = useState("");
  const submit = useMutation({ mutationFn: submitInspection.bind(null, assignmentId, kind), onSuccess: onDone });

  if (checklist.isLoading) return <LoadingState label="Loading checklist" />;
  if (checklist.isError) return <ErrorState message={checklist.error.message} />;
  const items = checklist.data!;
  const answer = (item: string, patch: Partial<ChecklistAnswer>) =>
    setAnswers(current => {
      const base: ChecklistAnswer = current[item] ?? { item, result: "OK", severity: null, note: null };
      return { ...current, [item]: { ...base, ...patch } };
    });

  function send(event: FormEvent) {
    event.preventDefault(); setError("");
    const missing = items.filter(i => !answers[i.item]);
    if (missing.length) return setError(`Check every item. Still to do: ${missing.map(i => i.label).join(", ")}.`);
    const defects = Object.values(answers).filter(a => a.result === "DEFECT");
    const undescribed = defects.find(a => !a.note?.trim());
    if (undescribed) return setError(`Describe the problem with: ${items.find(i => i.item === undescribed.item)?.label}.`);
    const unphotographed = defects.find(a => !defectPhotos[a.item]);
    if (unphotographed) return setError(`Take a photo of: ${items.find(i => i.item === unphotographed.item)?.label}.`);
    if (overview.filter(Boolean).length < minimumPhotos) return setError(`Take all ${minimumPhotos} vehicle photos.`);
    if (kind === "collection" && !selfie) return setError("Take your collection selfie.");
    const km = Number(odometer);
    if (!odometer || !Number.isInteger(km) || km < 0) return setError("Enter the odometer reading in whole kilometres.");
    if (collectionOdometer != null && km < collectionOdometer) return setError(`The odometer cannot be lower than at collection (${collectionOdometer.toLocaleString("en-ZA")} km).`);
    if (!fuel) return setError("Choose the fuel level.");
    submit.mutate({
      odometerKm: km, fuelLevel: fuel, notes,
      checklist: items.map(i => answers[i.item]),
      photos: [...overview.filter((f): f is File => Boolean(f)).map(file => ({ file, tag: "VEHICLE" })),
        ...defects.map(d => ({ file: defectPhotos[d.item]!, tag: d.item }))],
      selfie: selfie ?? undefined
    });
  }

  return <form className="driver-inspection" onSubmit={send} noValidate>
    <h2>{kind === "collection" ? "Collection inspection" : "Return inspection"}</h2>
    <p className="driver-hint">{kind === "collection" ? "Inspect the vehicle before you drive it. Report anything unsafe; do not drive a vehicle with a safety defect."
      : "Inspect the vehicle as you return it, so new damage can be compared with collection."}</p>
    <fieldset className="driver-section"><legend>Condition checklist</legend>
      {items.map(item => { const a = answers[item.item]; return <div key={item.item} className={`driver-check${a?.result === "DEFECT" ? " is-defect" : ""}`}>
        <span className="driver-check-label">{item.label}{item.safetyCritical && <small>Safety-critical</small>}</span>
        <div className="driver-toggle" role="group" aria-label={item.label}>
          <button type="button" aria-pressed={a?.result === "OK"} onClick={() => answer(item.item, { result: "OK", severity: null, note: null })}>OK</button>
          <button type="button" aria-pressed={a?.result === "DEFECT"} onClick={() => answer(item.item, { result: "DEFECT", severity: item.safetyCritical ? "SAFETY" : "MINOR" })}>Problem</button>
        </div>
        {a?.result === "DEFECT" && <div className="driver-defect">
          {!item.safetyCritical && <div className="driver-toggle" role="group" aria-label="How serious?">
            <button type="button" aria-pressed={a.severity === "MINOR"} onClick={() => answer(item.item, { severity: "MINOR" })}>Minor</button>
            <button type="button" aria-pressed={a.severity === "SAFETY"} onClick={() => answer(item.item, { severity: "SAFETY" })}>Unsafe to drive</button>
          </div>}
          {(item.safetyCritical || a.severity === "SAFETY") && <p className="driver-warning"><TriangleAlert aria-hidden="true" />Treated as a safety defect. Do not drive the vehicle.</p>}
          <label>What is wrong?<textarea rows={2} maxLength={500} value={a.note ?? ""} onChange={event => answer(item.item, { note: event.target.value })} /></label>
          <PhotoInput label="Photo of the problem" file={defectPhotos[item.item] ?? null} onChange={file => setDefectPhotos(c => ({ ...c, [item.item]: file }))} />
        </div>}
      </div>; })}
    </fieldset>
    <fieldset className="driver-section"><legend>Vehicle photos</legend>
      <div className="driver-photo-grid">{overview.map((file, index) => <PhotoInput key={index} label={angles[index] ?? `Photo ${index + 1}`} file={file}
        onChange={next => setOverview(c => c.map((f, i) => i === index ? next : f))} />)}</div>
    </fieldset>
    {kind === "collection" && <fieldset className="driver-section"><legend>Collection selfie</legend>
      <p className="driver-hint">Take a fresh selfie with the vehicle. It is kept as an accountability record only; no face recognition is used.</p>
      <PhotoInput label="Selfie" file={selfie} onChange={setSelfie} capture="user" />
    </fieldset>}
    <fieldset className="driver-section"><legend>Readings</legend>
      <label>Odometer (km){collectionOdometer != null && <small> At collection: {collectionOdometer.toLocaleString("en-ZA")} km</small>}
        <input inputMode="numeric" pattern="[0-9]*" value={odometer} onChange={event => setOdometer(event.target.value.replace(/\D/g, ""))} /></label>
      <div className="driver-fuel" role="group" aria-label="Fuel level"><span>Fuel level</span>
        <div className="driver-toggle">{fuelLevels.map(([value, label]) => <button key={value} type="button" aria-pressed={fuel === value} onClick={() => setFuel(value)}>{label}</button>)}</div></div>
      <label>Notes <em className="optional">(optional)</em><textarea rows={3} maxLength={2000} value={notes} onChange={event => setNotes(event.target.value)} /></label>
    </fieldset>
    {(error || submit.isError) && <ErrorState message={error || submit.error!.message} />}
    <div className="driver-actions">
      <button className="button button-primary" type="submit" disabled={submit.isPending}>{submit.isPending ? "Uploading…" : `Submit ${kind} inspection`}</button>
      <button className="button button-secondary" type="button" onClick={onCancel} disabled={submit.isPending}>Cancel</button>
    </div>
  </form>;
}

export function SubmissionOutcome({ result, onBack }: { result: SubmissionResult; onBack: () => void }) {
  const blocked = result.outcome.startsWith("Do not") || result.outcome.includes("Do not take");
  return <div className={`driver-outcome${blocked ? " is-blocked" : ""}`} role="status">
    {blocked ? <TriangleAlert aria-hidden="true" /> : <CheckCircle2 aria-hidden="true" />}
    <p>{result.outcome}</p>
    <button className="button button-primary" onClick={onBack}>Back to assignment</button>
  </div>;
}
