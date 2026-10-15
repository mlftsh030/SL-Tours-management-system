import { request } from "../../lib/api";
import { withReferral } from "../../lib/referral";
import type { EnquiryReceipt } from "../../types/api";

export interface EnquiryPayload {
  idempotencyKey: string;
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  message: string;
  enquiryType: "GENERAL" | "TOUR" | "PRIVATE_TRIP";
  tourSlug?: string;
  requestedDate?: string;
  pickupLocation?: string;
  passengers?: number;
  /** Airport transfers: recorded on the enquiry as labelled lines. */
  transfer?: { direction: "ARRIVAL" | "DEPARTURE"; flightNumber?: string; flightDate?: string; flightTime?: string; destination: string; luggage?: string };
  /** Custom tours: travel dates and interests. */
  custom?: { startDate?: string; endDate?: string; interests?: string };
}

export const submitEnquiry = (payload: EnquiryPayload) =>
  request<EnquiryReceipt>("/api/v1/enquiries", { method: "POST", body: JSON.stringify(withReferral(payload)) });
