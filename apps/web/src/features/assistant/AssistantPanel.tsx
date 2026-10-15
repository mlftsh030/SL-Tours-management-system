import { PrivacyNote } from "../../app/PrivacyPage";
import { ReferralNotice } from "../partners/ReferralNotice";
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { useMutation } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Info, RotateCcw, Send, X } from "lucide-react";
import { formatAmount } from "../../lib/format";
import { newIdempotencyKey } from "../../lib/uuid";
import { PlanResult } from "../explore/DayPlannerPage";
import { endConversation, sendMessage, startConversation, submitPlan } from "./assistantApi";
import type { AssistantOption, AssistantReply, AssistantStatus } from "../../types/api";

interface Entry { from: "visitor" | "assistant" | "notice"; text: string }
const MAX_CHARS = 1000;

/**
 * Accessible chat panel: a modal dialog that traps focus, closes with Escape and returns focus to the launcher.
 * Plans are rendered from the planner's own results (costs, labels, sources), never from the assistant's wording.
 */
export function AssistantPanel({ status, onClose }: { status: AssistantStatus; onClose: () => void }) {
  const [conversation, setConversation] = useState<AssistantReply | null>(null);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [draft, setDraft] = useState("");
  const [sendingOption, setSendingOption] = useState<string | null>(null);
  const dialog = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const log = useRef<HTMLDivElement>(null);

  const apply = (reply: AssistantReply) => {
    setConversation(reply);
    setEntries(list => [...list, { from: "assistant", text: reply.text },
      ...(reply.checkedFallback ? [{ from: "notice" as const, text: "This answer was written from the planner's own figures." }] : [])]);
    if (reply.enquiryOptionId) setSendingOption(reply.enquiryOptionId);
  };
  const start = useMutation({ mutationFn: startConversation, onSuccess: reply => { setEntries([]); apply(reply); } });
  const send = useMutation({ mutationFn: (text: string) => sendMessage(conversation!.conversationId, text), onSuccess: apply,
    onError: (error: Error) => setEntries(list => [...list, { from: "notice", text: error.message }]) });

  useEffect(() => { start.mutate(); dialog.current?.focus(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  // Move focus to the message box once it can be used.
  useEffect(() => { if (conversation?.conversationId) input.current?.focus(); }, [conversation?.conversationId]);
  useEffect(() => { if (log.current) log.current.scrollTop = log.current.scrollHeight; }, [entries]);
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = previous; };
  }, []);

  function close() {
    if (conversation) endConversation(conversation.conversationId).catch(() => undefined);
    onClose();
  }
  function restart() {
    if (conversation) endConversation(conversation.conversationId).catch(() => undefined);
    setConversation(null); setSendingOption(null); start.mutate();
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    const text = draft.trim();
    if (!text || !conversation || send.isPending) return;
    setEntries(list => [...list, { from: "visitor", text }]);
    setDraft("");
    send.mutate(text);
  }
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") { event.stopPropagation(); close(); return; }
    if (event.key !== "Tab" || !dialog.current) return;
    const focusable = [...dialog.current.querySelectorAll<HTMLElement>("a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select, summary")]
      .filter(el => el.offsetParent !== null || el === document.activeElement);
    if (focusable.length === 0) return;
    const first = focusable[0], last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }

  const unavailable = conversation && conversation.status !== "OK";
  // Rendered into <body> so it never inherits the colours or stacking of the section that holds the launcher.
  return createPortal(<div className="assistant-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) close(); }}>
    <div ref={dialog} className="assistant-panel" role="dialog" aria-modal="true" aria-labelledby="assistant-title" tabIndex={-1} onKeyDown={onKeyDown}>
      <header className="assistant-header">
        <div>
          <h2 id="assistant-title">Cape Town planning assistant</h2>
          {status.mode === "MOCK" && <p className="assistant-mock">Development mock: scripted replies for testing, not a live assistant.</p>}
        </div>
        <button type="button" className="assistant-icon-button" onClick={restart} aria-label="Start a new conversation"><RotateCcw aria-hidden="true" /></button>
        <button type="button" className="assistant-icon-button" onClick={close} aria-label="Close the assistant"><X aria-hidden="true" /></button>
      </header>
      <p className="assistant-intro"><Info aria-hidden="true" />Uses SL Tours' checked activity information and day planner. Nothing is booked here and
        prices can change. Please don't share sensitive details; chats are deleted when closed or left idle.</p>

      <div ref={log} className="assistant-log" role="log" aria-live="polite" aria-label="Conversation">
        {start.isPending && <p className="assistant-notice">Starting…</p>}
        {start.isError && <p className="assistant-notice">{start.error.message} <Link to="/explore/plan" onClick={onClose}>Use the day planner</Link></p>}
        {entries.map((e, i) => <p key={i} className={`assistant-message from-${e.from}`}>
          {e.from !== "notice" && <span className="sr-only">{e.from === "visitor" ? "You: " : "Assistant: "}</span>}{e.text}</p>)}
        {send.isPending && <p className="assistant-notice" aria-live="polite">The assistant is checking the catalogue and planner…</p>}
        {unavailable && <p className="assistant-notice">
          <Link to={conversation.plannerLink} onClick={onClose}>Open the day planner with your details</Link></p>}
        {conversation && conversation.options.length > 0 && <div className="assistant-options">
          <h3>Plan options</h3>
          {conversation.options.map(o => <OptionCard key={o.id} option={o} canSend={!conversation.enquiryReference}
            onSend={() => setSendingOption(o.id)} />)}
        </div>}
        {conversation && sendingOption && !conversation.enquiryReference && <SendPlanForm conversationId={conversation.conversationId}
          option={conversation.options.find(o => o.id === sendingOption) ?? null} onCancel={() => setSendingOption(null)}
          onSent={reply => { setSendingOption(null); apply(reply); }} />}
      </div>

      <form className="assistant-input" onSubmit={submit}>
        <label htmlFor="assistant-message" className="sr-only">Your message</label>
        <textarea id="assistant-message" ref={input} rows={2} maxLength={MAX_CHARS} value={draft} onChange={event => setDraft(event.target.value)}
          onKeyDown={event => { if (event.key === "Enter" && !event.shiftKey) submit(event); }}
          placeholder="Who, when, where from and what you enjoy…" disabled={!conversation || conversation.status === "LIMITED"} />
        <button type="submit" className="button button-primary" disabled={!draft.trim() || !conversation || send.isPending} aria-label="Send message">
          <Send aria-hidden="true" /></button>
      </form>
    </div>
  </div>, document.body);
}

function OptionCard({ option, canSend, onSend }: { option: AssistantOption; canSend: boolean; onSend: () => void }) {
  const it = option.itinerary;
  return <details className="assistant-option">
    <summary><strong>Option {option.id}: {option.label}</strong>
      <span>Known costs {formatAmount(it.totals.knownTotalCents)}{it.totals.unknownItems > 0 ? `, ${it.totals.unknownItems} not verified` : ""}</span></summary>
    <PlanResult plan={it} />
    {canSend && <button type="button" className="button button-secondary" onClick={onSend}>Send option {option.id} to SL Tours</button>}
  </details>;
}

function SendPlanForm({ conversationId, option, onCancel, onSent }: { conversationId: string; option: AssistantOption | null; onCancel: () => void;
  onSent: (reply: AssistantReply) => void }) {
  const [form, setForm] = useState({ name: "", email: "", phone: "", note: "", confirmed: false });
  const [key] = useState(newIdempotencyKey);
  const send = useMutation({ mutationFn: () => submitPlan(conversationId, { optionId: option!.id, name: form.name, email: form.email,
    phone: form.phone || null, note: form.note || null, confirmed: form.confirmed, idempotencyKey: key }), onSuccess: onSent });
  if (!option) return null;
  return <form className="assistant-send form-surface" onSubmit={event => { event.preventDefault(); send.mutate(); }} aria-label={`Send option ${option.id}`}>
    <h3>Send option {option.id} to SL Tours</h3>
    <p className="form-note">SL Tours receives the plan exactly as shown above, including unverified costs, and replies by email. Nothing is booked yet.</p>
    <label>Name<input required maxLength={160} autoComplete="name" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
    <label>Email<input required type="email" maxLength={180} autoComplete="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label>
    <label>Phone <em className="optional">(optional)</em><input maxLength={80} autoComplete="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></label>
    <label>Note <em className="optional">(optional)</em><textarea maxLength={800} rows={2} value={form.note} onChange={e => setForm({ ...form, note: e.target.value })} /></label>
    <label className="choice"><input type="checkbox" required checked={form.confirmed} onChange={e => setForm({ ...form, confirmed: e.target.checked })} />
      <span>I want to send option {option.id} to SL Tours</span></label>
    <ReferralNotice />
    {send.isError && <p className="assistant-notice" role="alert">{send.error.message}</p>}
    <div className="assistant-send-actions">
      <PrivacyNote />
      <button type="submit" className="button button-primary" disabled={!form.confirmed || send.isPending}>{send.isPending ? "Sending…" : "Send to SL Tours"}</button>
      <button type="button" className="button button-quiet" onClick={onCancel}>Not now</button>
    </div>
  </form>;
}
