import { request } from "../../lib/api";
import type { Session } from "../../types/api";

/** SENT: handed to the mail server. NOT_CONFIGURED: this site cannot send email. FAILED: the mail server refused it. */
export type EmailDelivery = "SENT" | "NOT_CONFIGURED" | "FAILED";
export interface RegisterResult { email: string; verificationEmail: EmailDelivery }
export interface LinkedRecords { bookings: number; enquiries: number; documents: number }

export const getSession = () => request<Session>("/api/v1/auth/me");
export async function signIn(email: string, password: string) {
  await request<void>("/api/v1/auth/login", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ username: email, password }).toString()
  });
  return getSession();
}
export const signOut = () => request<void>("/api/v1/auth/logout", { method: "POST" });
export const register = (details: { name: string; email: string; phone?: string; password: string; passwordConfirmation: string }) =>
  request<RegisterResult>("/api/v1/auth/register", { method: "POST", body: JSON.stringify({ ...details, phone: details.phone || null }) });
export const verifyEmail = (token: string) =>
  request<{ email: string; linked: LinkedRecords }>("/api/v1/auth/verify-email", { method: "POST", body: JSON.stringify({ token }) });
export const resendVerification = () => request<{ delivery: EmailDelivery }>("/api/v1/account/verification-email", { method: "POST" });
export const requestPasswordReset = (email: string) =>
  request<{ delivery: EmailDelivery }>("/api/v1/auth/password-reset/request", { method: "POST", body: JSON.stringify({ email }) });
export const resetPassword = (token: string, password: string, passwordConfirmation: string) =>
  request<void>("/api/v1/auth/password-reset/confirm", { method: "POST", body: JSON.stringify({ token, password, passwordConfirmation }) });

/** Where each kind of account lands after signing in. */
export const homeFor = (role: Session["role"]) =>
  role === "CUSTOMER" ? "/account" : role === "DRIVER" ? "/driver" : role === "PARTNER" ? "/partner" : "/operations";
