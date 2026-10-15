import { request } from "../../lib/api";
import type { ChecklistDefinition, DriverAssignmentDetail, DriverAssignmentSummary, DriverProfile, FuelLevel, SubmissionResult } from "../../types/api";

export const getDriverProfile = () => request<DriverProfile>("/api/v1/driver/me");
export const listDriverAssignments = () => request<DriverAssignmentSummary[]>("/api/v1/driver/assignments");
export const getDriverAssignment = (id: string) => request<DriverAssignmentDetail>(`/api/v1/driver/assignments/${id}`);
export const getChecklist = () => request<ChecklistDefinition[]>("/api/v1/driver/checklist");

export interface ChecklistAnswer { item: string; result: "OK" | "DEFECT"; severity: "MINOR" | "SAFETY" | null; note: string | null; }
export interface InspectionSubmission {
  odometerKm: number; fuelLevel: FuelLevel; notes: string; checklist: ChecklistAnswer[];
  /** Photos in upload order with their tag: "VEHICLE" or the checklist item they show. */
  photos: Array<{ file: File; tag: string }>; selfie?: File;
}

export function submitInspection(assignmentId: string, kind: "collection" | "return", submission: InspectionSubmission) {
  const data = new FormData();
  const inspection = { odometerKm: submission.odometerKm, fuelLevel: submission.fuelLevel, notes: submission.notes || null,
    checklist: submission.checklist, photoTags: submission.photos.map(p => p.tag) };
  data.append("inspection", new Blob([JSON.stringify(inspection)], { type: "application/json" }));
  submission.photos.forEach(p => data.append("photos", p.file));
  if (submission.selfie) data.append("selfie", submission.selfie);
  return request<SubmissionResult>(`/api/v1/driver/assignments/${assignmentId}/${kind}`, { method: "POST", body: data });
}
