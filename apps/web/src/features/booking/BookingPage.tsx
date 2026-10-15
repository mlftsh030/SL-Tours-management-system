import { PrivacyNote } from "../../app/PrivacyPage";
import { ReferralNotice } from "../partners/ReferralNotice";
import { useSession } from "../auth/useSession";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2 } from "lucide-react";
import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { Link, useParams } from "react-router-dom";
import { z } from "zod";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { createBooking, getTour } from "../../lib/api";
import { Price } from "../../lib/currency";
import { localDate } from "../../lib/dates";
import { formatAmount as formatMoney, priceLabel } from "../../lib/format";
import { useDocumentTitle } from "../../lib/useDocumentTitle";

const bookingSchema = z.object({
  travelDate: z.string().min(1, "Choose a date"),
  guests: z.coerce.number().int().min(1, "Add at least one guest"),
  pickupLocation: z.string().max(240).optional(),
  customerName: z.string().min(2, "Enter your name").max(160),
  customerEmail: z.string().email("Enter a valid email").max(180),
  customerPhone: z.string().max(80).optional(),
  customerCountry: z.string().max(80).optional(),
  notes: z.string().max(1200).optional()
});

type BookingForm = z.infer<typeof bookingSchema>;

export function BookingPage() {
  const session = useSession();
  const { slug = "" } = useParams();
  const tour = useQuery({ queryKey: ["tour", slug], queryFn: () => getTour(slug), enabled: Boolean(slug) });
  const booking = useMutation({ mutationFn: createBooking });
  useDocumentTitle("Book Experience | SL Tours");

  const minDate = localDate(1);
  const form = useForm<BookingForm>({
    resolver: zodResolver(bookingSchema),
    defaultValues: {
      travelDate: minDate,
      guests: 2,
      pickupLocation: "",
      customerName: "",
      customerEmail: "",
      customerPhone: "",
      customerCountry: "",
      notes: ""
    }
  });
  // Start at the tour's minimum group so the form never opens on a group size the tour does not accept.
  const minimumGuests = tour.data?.minimumGuests ?? 1;
  useEffect(() => {
    if (tour.data && Number(form.getValues("guests")) < tour.data.minimumGuests) form.setValue("guests", tour.data.minimumGuests);
  }, [tour.data, form]);
  useEffect(() => {
    if (session.data?.role === "CUSTOMER") {
      form.setValue("customerName", session.data.name ?? "");
      form.setValue("customerEmail", session.data.email ?? "");
    }
  }, [session.data, form]);
  const guests = form.watch("guests");

  const estimate = useMemo(() => {
    if (!tour.data) {
      return 0;
    }
    if (tour.data.pricingType === "CUSTOM") {
      return 0;
    }
    if (tour.data.pricingType === "PER_TRIP") {
      return tour.data.basePriceCents;
    }
    return tour.data.basePriceCents * Number(guests || 0);
  }, [guests, tour.data]);

  if (tour.isLoading) {
    return <LoadingState label="Loading booking" />;
  }

  if (tour.isError || !tour.data) {
    return <ErrorState message={(tour.error as Error)?.message ?? "Tour not found"} />;
  }

  const onSubmit = form.handleSubmit((values) => {
    const { minimumGuests: min, maximumGuests: max } = tour.data;
    if (values.guests < min || values.guests > max)
      return form.setError("guests", { message: min === max ? `This tour is for ${min} guests` : `Choose between ${min} and ${max} guests` });
    booking.mutate({
      tourSlug: tour.data.slug,
      ...values
    });
  });

  if (booking.isSuccess) {
    return (
      <section className="section confirmation-page">
        <CheckCircle2 aria-hidden="true" />
        <p className="eyebrow">Booking request received</p>
        <h1>{booking.data.reference}</h1>
        <p>{booking.data.message}</p>
        <dl>
          <div>
            <dt>Experience</dt>
            <dd>{booking.data.tourTitle}</dd>
          </div>
          <div>
            <dt>Estimated total</dt>
            <dd>{formatMoney(booking.data.totalAmountCents)}</dd>
          </div>
        </dl>
        <a className="button button-primary" href="/account">
          View my bookings
        </a>
      </section>
    );
  }

  return (
    <section className="section booking-flow">
      <Link className="text-link" to={`/experiences/${tour.data.slug}`}>
        <ArrowLeft aria-hidden="true" />
        Back to tour
      </Link>
      <div className="booking-grid">
        <form onSubmit={onSubmit} className="form-surface">
          <p className="eyebrow">1 Date  2 Guests  3 Details  4 Review</p>
          <h1>Book {tour.data.title}</h1>
          {session.data?.role === "CUSTOMER" ? <p>Booking as {session.data.name}. This request will appear in your account.</p> : <p><a href={`/login?next=${encodeURIComponent(`/book/${slug}`)}`}>Sign in</a> to save this booking to your account, or continue as a guest.</p>}
          <div className="form-grid">
            <label>
              <span>Date</span>
              <input type="date" min={minDate} {...form.register("travelDate")} />
              {form.formState.errors.travelDate && <small>{form.formState.errors.travelDate.message}</small>}
            </label>
            <label>
              <span>Guests</span>
              <input type="number" min={tour.data.minimumGuests} max={tour.data.maximumGuests} {...form.register("guests")} />
              {form.formState.errors.guests && <small>{form.formState.errors.guests.message}</small>}
              {minimumGuests > 1 && <small className="field-hint">Minimum {minimumGuests} guests. Fewer?{" "}
                <Link to="/experiences/private-custom-tour#enquire">Ask for a private quote</Link></small>}
            </label>
            <label className="full-field">
              <span>Pickup</span>
              <input placeholder="Hotel, address, or airport terminal" {...form.register("pickupLocation")} />
            </label>
            <label>
              <span>Name</span>
              <input readOnly={session.data?.role === "CUSTOMER"} {...form.register("customerName")} />
              {form.formState.errors.customerName && <small>{form.formState.errors.customerName.message}</small>}
            </label>
            <label>
              <span>Email</span>
              <input type="email" readOnly={session.data?.role === "CUSTOMER"} {...form.register("customerEmail")} />
              {form.formState.errors.customerEmail && <small>{form.formState.errors.customerEmail.message}</small>}
            </label>
            <label>
              <span>Phone</span>
              <input {...form.register("customerPhone")} />
            </label>
            <label>
              <span>Country</span>
              <input {...form.register("customerCountry")} />
            </label>
            <label className="full-field">
              <span>Notes</span>
              <textarea rows={4} placeholder="Timing, children, accessibility, food, or special requirements" {...form.register("notes")} />
            </label>
          </div>
          {booking.isError && <ErrorState message={(booking.error as Error).message} />}
          <ReferralNotice />
          <PrivacyNote />
          <button className="button button-primary" type="submit" disabled={booking.isPending}>
            {booking.isPending ? "Submitting..." : "Submit Booking Request"}
          </button>
        </form>
        <aside className="booking-panel">
          <img src={tour.data.heroImage} alt="" />
          <h2>{tour.data.title}</h2>
          <p>{tour.data.duration}</p>
          <span>
            <Price cents={tour.data.basePriceCents} /> {priceLabel(tour.data.pricingType)}
          </span>
          {tour.data.pricingType !== "CUSTOM" && (
            <>
              <span>Estimated total</span>
              <strong>{formatMoney(estimate)}</strong>
            </>
          )}
          <p>This is a booking request, not a confirmed booking. SL Tours confirms availability, inclusions and payment
            instructions with you.</p>
        </aside>
      </div>
    </section>
  );
}
