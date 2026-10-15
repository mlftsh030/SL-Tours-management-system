import { Navigate, Route, Routes } from "react-router-dom";
import { lazy, Suspense, type ReactNode } from "react";
import { LoadingState } from "../components/StateBlocks";
import { ExperiencesPage } from "../features/tours/ExperiencesPage";
import { HomePage } from "../features/tours/HomePage";
import { TourDetailPage } from "../features/tours/TourDetailPage";
import { AppLayout } from "../layouts/AppLayout";
import { AboutPage, ContactPage } from "./InfoPages";
import { NotFoundPage } from "./NotFoundPage";
import { PrivacyPage } from "./PrivacyPage";

// Public pages beyond the main landing pages (home, tours) load on demand to keep the first download small.
const AuthPage = lazy(() => import("../features/auth/AuthPage").then(m => ({ default: m.AuthPage })));
const BookingPage = lazy(() => import("../features/booking/BookingPage").then(m => ({ default: m.BookingPage })));
const GalleryPage = lazy(() => import("../features/gallery/GalleryPage").then(m => ({ default: m.GalleryPage })));
const PrivateTripPage = lazy(() => import("../features/private-trips/PrivateTripPage").then(m => ({ default: m.PrivateTripPage })));
const ExplorePage = lazy(() => import("../features/explore/ExplorePage").then(m => ({ default: m.ExplorePage })));
const ActivityDetailPage = lazy(() => import("../features/explore/ActivityDetailPage").then(m => ({ default: m.ActivityDetailPage })));
const DayPlannerPage = lazy(() => import("../features/explore/DayPlannerPage").then(m => ({ default: m.DayPlannerPage })));
const TripPlannerPage = lazy(() => import("../features/trip-planner/TripPlannerPage").then(m => ({ default: m.TripPlannerPage })));
const PartnerWithUsPage = lazy(() => import("../features/partners/PartnerWithUsPage").then(m => ({ default: m.PartnerWithUsPage })));
const ForgotPasswordPage = lazy(() => import("../features/auth/RecoveryPages").then(m => ({ default: m.ForgotPasswordPage })));
const ResetPasswordPage = lazy(() => import("../features/auth/RecoveryPages").then(m => ({ default: m.ResetPasswordPage })));
const VerifyEmailPage = lazy(() => import("../features/auth/RecoveryPages").then(m => ({ default: m.VerifyEmailPage })));
// Signed-in areas load on demand so public pages keep their original bundle size.
const OperationsPage = lazy(() => import("../features/operations/OperationsPage").then(m => ({ default: m.OperationsPage })));
const DriverPortalPage = lazy(() => import("../features/driver/DriverPortalPage").then(m => ({ default: m.DriverPortalPage })));
const PartnerPortalPage = lazy(() => import("../features/partners/PartnerPortalPage").then(m => ({ default: m.PartnerPortalPage })));
const AccountPage = lazy(() => import("../features/account/AccountPage").then(m => ({ default: m.AccountPage })));
const deferred = (page: ReactNode) => <Suspense fallback={<LoadingState label="Loading" />}>{page}</Suspense>;

export default function App() {
  return (
    <Routes>
      <Route path="operations/*" element={deferred(<OperationsPage />)} />
      <Route path="driver/*" element={deferred(<DriverPortalPage />)} />
      <Route path="partner/*" element={deferred(<PartnerPortalPage />)} />
      <Route path="login" element={deferred(<AuthPage />)} />
      <Route path="register" element={deferred(<AuthPage registration />)} />
      <Route path="forgot-password" element={deferred(<ForgotPasswordPage />)} />
      <Route path="reset-password" element={deferred(<ResetPasswordPage />)} />
      <Route path="verify-email" element={deferred(<VerifyEmailPage />)} />
      <Route path="account/*" element={deferred(<AccountPage />)} />
      <Route path="my-trip" element={<Navigate to="/account" replace />} />
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        <Route path="experiences" element={<ExperiencesPage key="all" />} />
        <Route path="experiences/:slug" element={<TourDetailPage />} />
        <Route path="private-tours" element={<ExperiencesPage key="private" focus="private" />} />
        <Route path="transfers" element={<ExperiencesPage key="transfer" focus="transfer" />} />
        <Route path="book/:slug" element={deferred(<BookingPage />)} />
        <Route path="book-private-trip" element={deferred(<PrivateTripPage />)} />
        <Route path="explore" element={deferred(<ExplorePage />)} />
        <Route path="explore/plan" element={deferred(<DayPlannerPage />)} />
        <Route path="explore/:slug" element={deferred(<ActivityDetailPage />)} />
        <Route path="plan-my-trip" element={deferred(<TripPlannerPage />)} />
        <Route path="gallery" element={deferred(<GalleryPage />)} />
        <Route path="about" element={<AboutPage />} />
        <Route path="contact" element={<ContactPage />} />
        <Route path="partner-with-us" element={deferred(<PartnerWithUsPage />)} />
        <Route path="privacy" element={<PrivacyPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
