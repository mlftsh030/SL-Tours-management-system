import { PrivacyNote } from "../../app/PrivacyPage";
import { ReferralNotice } from "../partners/ReferralNotice";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { addDays, format } from "date-fns";
import { CheckCircle2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { PageBanner } from "../../components/PageBanner";
import { ErrorState } from "../../components/StateBlocks";
import { createTripPlanningRequest } from "../../lib/api";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { useIdempotencyKey } from "../../lib/useIdempotencyKey";
import { useState } from "react";
import { Link } from "react-router-dom";

const options = {
  interests: ["nature", "wine", "culture", "history", "food", "wildlife", "beaches", "adventure", "shopping"],
  services: ["tours", "airport transfer", "private driver", "custom itinerary", "activity recommendations"],
  groups: ["solo", "couple", "family", "friends", "corporate", "school/group"]
};

const plannerSchema = z.object({
  customerName: z.string().min(2, "Enter your name").max(160),
  customerEmail: z.string().email("Enter a valid email").max(180),
  customerPhone: z.string().max(80).optional(),
  arrivalDate: z.string().min(1, "Choose arrival date"),
  departureDate: z.string().min(1, "Choose departure date"),
  adults: z.coerce.number().int().min(1).max(60),
  children: z.coerce.number().int().min(0).max(60),
  groupType: z.string().min(1, "Choose group type"),
  interests: z.array(z.string()).min(1, "Choose at least one interest"),
  services: z.array(z.string()).min(1, "Choose at least one service"),
  notes: z.string().max(1600).optional()
});

type PlannerForm = z.infer<typeof plannerSchema>;

export function TripPlannerPage() {
  useDocumentTitle("Plan My Trip | SL Tours");
  const mutation = useMutation({ mutationFn: createTripPlanningRequest });
  // An unchanged retry reuses the key so a slow or repeated submit is stored once.
  const submission = useIdempotencyKey();
  const today = new Date();
  const form = useForm<PlannerForm>({
    resolver: zodResolver(plannerSchema),
    defaultValues: {
      customerName: "",
      customerEmail: "",
      customerPhone: "",
      arrivalDate: format(addDays(today, 14), "yyyy-MM-dd"),
      departureDate: format(addDays(today, 19), "yyyy-MM-dd"),
      adults: 2,
      children: 0,
      groupType: "couple",
      interests: ["nature"],
      services: ["tours", "custom itinerary"],
      notes: ""
    }
  });

  const onSubmit = form.handleSubmit((values) => {
    if (!mutation.isPending) mutation.mutate({ ...values, idempotencyKey: submission.keyFor(values) });
  });

  if (mutation.isSuccess) {
    return (
      <section className="section confirmation-page">
        <CheckCircle2 aria-hidden="true" />
        <p className="eyebrow">Trip planning request received</p>
        <h1>{mutation.data.reference}</h1>
        <p>{mutation.data.message}</p>
      </section>
    );
  }

  return (
    <>
      <PageBanner
        title="Plan My Trip"
        subtitle="Tell us your dates, who is travelling, and what you love. We will shape a tailored plan and quote."
        image="/assets/gallery/22.webp"
      />
      <section className="section planner-page">
        <aside className="private-trip-cta">
          <div>
            <p className="eyebrow">Just one day?</p>
            <h2>Plan a day yourself</h2>
            <p>See a suggested route with opening times and every admission, transport and food cost, using prices from official sources.</p>
          </div>
          <Link className="button button-primary" to="/explore/plan">Build a day plan</Link>
        </aside>
        <form onSubmit={onSubmit} className="form-surface wide-form">
          <div className="form-grid">
            <label>
              <span>Name</span>
              <input {...form.register("customerName")} />
              {form.formState.errors.customerName && <small>{form.formState.errors.customerName.message}</small>}
            </label>
            <label>
              <span>Email</span>
              <input type="email" {...form.register("customerEmail")} />
              {form.formState.errors.customerEmail && <small>{form.formState.errors.customerEmail.message}</small>}
            </label>
            <label>
              <span>Phone</span>
              <input {...form.register("customerPhone")} />
            </label>
            <label>
              <span>Group type</span>
              <select {...form.register("groupType")}>
                {options.groups.map((group) => (
                  <option key={group} value={group}>
                    {group}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>Arrival</span>
              <input type="date" {...form.register("arrivalDate")} />
            </label>
            <label>
              <span>Departure</span>
              <input type="date" {...form.register("departureDate")} />
            </label>
            <label>
              <span>Adults</span>
              <input type="number" min="1" {...form.register("adults")} />
            </label>
            <label>
              <span>Children</span>
              <input type="number" min="0" {...form.register("children")} />
            </label>
          </div>
          <fieldset>
            <legend>Interests</legend>
            <div className="choice-grid">
              {options.interests.map((item) => (
                <label key={item} className="choice">
                  <input type="checkbox" value={item} {...form.register("interests")} />
                  <span>{item}</span>
                </label>
              ))}
            </div>
            {form.formState.errors.interests && <small>{form.formState.errors.interests.message}</small>}
          </fieldset>
          <fieldset>
            <legend>Services</legend>
            <div className="choice-grid">
              {options.services.map((item) => (
                <label key={item} className="choice">
                  <input type="checkbox" value={item} {...form.register("services")} />
                  <span>{item}</span>
                </label>
              ))}
            </div>
          </fieldset>
          <label>
            <span>Notes</span>
            <textarea rows={5} {...form.register("notes")} />
          </label>
          {mutation.isError && <ErrorState message={(mutation.error as Error).message} />}
          <ReferralNotice />
          <PrivacyNote />
          <button className="button button-primary" type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? "Sending..." : "Send Trip Request"}
          </button>
        </form>
      </section>
    </>
  );
}
