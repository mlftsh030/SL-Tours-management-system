import { capeTown } from "../../lib/dates";
import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Copy, Download, Mail, Share2 } from "lucide-react";
import { WhatsAppIcon } from "../../components/WhatsAppIcon";
import { ErrorState } from "../../components/StateBlocks";
import { fetchPdf, getCapabilities, recordShareAction, shareDocument } from "./billingApi";
import type { Channel, DocumentDetail, ShareResult } from "./billingTypes";
import { kindLabels } from "./money";

/**
 * Prepares a secure customer link and a message. Nothing is sent from here: WhatsApp and email open with the message
 * filled in and staff press send themselves. Where the device supports it, the PDF itself can be handed to the share sheet.
 */
export function ShareDialog({ document, paymentId, filename, pdfUrl, onClose }: {
  document: DocumentDetail; paymentId?: string; filename: string; pdfUrl: string; onClose: () => void;
}) {
  const id = document.summary.id;
  const capabilities = useQuery({ queryKey: ["billing", "capabilities"], queryFn: getCapabilities, staleTime: 300_000 });
  const [days, setDays] = useState(14);
  const [result, setResult] = useState<{ channel: Channel; share: ShareResult } | null>(null);
  const [message, setMessage] = useState("");
  const [notice, setNotice] = useState("");
  const [nativeFallback, setNativeFallback] = useState(false);
  const prepare = useMutation({
    mutationFn: (channel: Channel) => shareDocument(id, channel, days, paymentId).then(share => ({ channel, share })),
    onSuccess: r => { setResult(r); setMessage(r.share.message); setNotice(""); }
  });
  const what = paymentId ? "receipt" : kindLabels[document.summary.kind].toLowerCase();
  const whatsappHref = result ? `${result.share.whatsappUrl.split("?text=")[0]}?text=${encodeURIComponent(message)}` : "#";
  const mailHref = result ? `${result.share.mailtoUrl.split("&body=")[0]}&body=${encodeURIComponent(message)}` : "#";
  const log = (action: Parameters<typeof recordShareAction>[1], channel?: Channel) => recordShareAction(id, action, channel, paymentId).catch(() => undefined);

  async function nativeShare() {
    setNotice(""); setNativeFallback(false);
    try {
      const file = await fetchPdf(pdfUrl, filename);
      if (typeof navigator.canShare === "function" && navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file], title: filename, text: result ? message : undefined });
        await log("NATIVE_SHARED", "NATIVE");
        setNotice("The PDF was handed to your device's share options. Check in WhatsApp or email that it was sent.");
      } else {
        setNativeFallback(true);
      }
    } catch (error) {
      if ((error as Error).name === "AbortError") setNotice("Sharing was cancelled.");
      else { setNativeFallback(true); setNotice((error as Error).message); }
    }
  }
  async function copy(text: string, label: string) {
    try { await navigator.clipboard.writeText(text); setNotice(`${label} copied.`); if (label === "Link") await log("LINK_COPIED", result?.channel); }
    catch { setNotice(`Copying is blocked in this browser. Select the ${label.toLowerCase()} and copy it yourself.`); }
  }

  return <div className="billing-share-backdrop" role="presentation" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
    <section className="form-surface billing-share" role="dialog" aria-modal="true" aria-labelledby="share-title">
      <div className="admin-heading"><h2 id="share-title">Share {what} {filename.replace(/\.pdf$/, "")}</h2>
        <button className="button button-secondary" onClick={onClose}>Close</button></div>
      <label className="billing-share-days">Customer link works for<select value={days} onChange={e => setDays(Number(e.target.value))}>
        {[7, 14, 30, capabilities.data?.maxLinkDays ?? 90].filter((v, i, a) => a.indexOf(v) === i).map(n => <option key={n} value={n}>{n} days</option>)}</select></label>
      <div className="billing-share-options">
        <button className="button button-primary" disabled={prepare.isPending} onClick={() => prepare.mutate("WHATSAPP")}><WhatsAppIcon aria-hidden="true" />Prepare WhatsApp message</button>
        <button className="button button-secondary" disabled={prepare.isPending} onClick={() => prepare.mutate("EMAIL")}><Mail aria-hidden="true" />Prepare email</button>
        <button className="button button-secondary" onClick={nativeShare}><Share2 aria-hidden="true" />Share the PDF file…</button>
        <a className="button button-quiet" href={`${pdfUrl}${pdfUrl.includes("?") ? "&" : "?"}download=true`} onClick={() => log("PDF_DOWNLOADED")}><Download aria-hidden="true" />Download PDF</a>
      </div>
      {prepare.isError && <ErrorState message={prepare.error.message} />}
      {result && <div className="billing-share-result">
        <label>Message <small>You can change it before opening {result.channel === "EMAIL" ? "email" : "WhatsApp"}</small>
          <textarea rows={9} value={message} onChange={e => setMessage(e.target.value)} /></label>
        <p className="form-note">Link: <span className="billing-link">{result.share.url}</span> · works until {capeTown(result.share.expiresAt, { day: "2-digit", month: "2-digit", year: "numeric" }).replace(/\//g, "-")}</p>
        <div className="admin-actions">
          {result.channel === "WHATSAPP" && <a className="button button-primary" href={whatsappHref} target="_blank" rel="noopener noreferrer" onClick={() => log("WHATSAPP_OPENED", "WHATSAPP")}>
            <WhatsAppIcon aria-hidden="true" />Open WhatsApp</a>}
          {result.channel === "EMAIL" && <a className="button button-primary" href={mailHref} onClick={() => log("EMAIL_OPENED", "EMAIL")}><Mail aria-hidden="true" />Open email app</a>}
          {result.channel === "EMAIL" && <button className="button button-secondary" onClick={() => copy(result.share.subject, "Subject")}><Copy aria-hidden="true" />Copy subject</button>}
          <button className="button button-secondary" onClick={() => copy(message, "Message")}><Copy aria-hidden="true" />Copy message</button>
          <button className="button button-quiet" onClick={() => copy(result.share.url, "Link")}><Copy aria-hidden="true" />Copy link</button>
        </div>
        {result.channel === "WHATSAPP" && <p className="form-note">{result.share.customerNumberMissing
          ? "No WhatsApp number is saved for this customer, so WhatsApp will ask you to choose the chat."
          : "WhatsApp opens with this message for the customer's number."} You press send yourself. Opening WhatsApp does not mean the customer received the message.</p>}
        {result.channel === "EMAIL" && <p className="form-note">{result.share.customerEmailMissing ? "No email address is saved for this customer. " : ""}{capabilities.data?.emailNote}</p>}
      </div>}
      {nativeFallback && <div className="admin-callout" role="status">
        <p>This browser cannot attach files to WhatsApp directly. Download the PDF, then attach it in WhatsApp (paperclip, then Document) or in your email.</p>
        <a className="button button-secondary" href={`${pdfUrl}${pdfUrl.includes("?") ? "&" : "?"}download=true`} onClick={() => log("PDF_DOWNLOADED")}><Download aria-hidden="true" />Download {filename}</a></div>}
      {notice && <p className="form-note" role="status">{notice}</p>}
      <p className="form-note">{capabilities.data?.whatsappNote ?? "Nothing is sent automatically."}</p>
    </section>
  </div>;
}
