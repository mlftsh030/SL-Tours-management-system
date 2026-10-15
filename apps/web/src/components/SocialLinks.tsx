import { Facebook, Instagram } from "lucide-react";
import { contact } from "../lib/contact";

function TikTokIcon() {
  return (
    <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor" aria-hidden="true">
      <path d="M12.53.02C13.84 0 15.14.01 16.44 0c.08 1.53.63 3.09 1.75 4.17 1.12 1.11 2.7 1.62 4.24 1.79v4.03c-1.44-.05-2.89-.35-4.2-.97-.57-.26-1.1-.59-1.62-.93-.01 2.92.01 5.84-.02 8.75-.08 1.4-.54 2.79-1.35 3.94-1.31 1.92-3.58 3.17-5.91 3.21-1.43.08-2.86-.31-4.08-1.03-2.02-1.19-3.44-3.37-3.65-5.71-.02-.5-.03-1-.01-1.49.18-1.9 1.12-3.72 2.58-4.96 1.66-1.44 3.98-2.13 6.15-1.72.02 1.48-.04 2.96-.04 4.44-.99-.32-2.15-.23-3.02.37-.63.41-1.11 1.04-1.36 1.75-.21.51-.15 1.07-.14 1.61.24 1.64 1.82 3.02 3.5 2.87 1.12-.01 2.19-.66 2.77-1.61.19-.33.4-.67.41-1.06.1-1.79.06-3.57.07-5.36.01-4.03-.01-8.05.02-12.07z" />
    </svg>
  );
}

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor" aria-hidden="true">
      <path d="M12.24 10.29v3.6h5.01c-.22 1.3-1.52 3.8-5.01 3.8-3.02 0-5.48-2.5-5.48-5.59s2.46-5.59 5.48-5.59c1.72 0 2.87.73 3.53 1.36l2.4-2.32C16.63 4.12 14.63 3.2 12.24 3.2 7.35 3.2 3.4 7.15 3.4 12.04s3.95 8.84 8.84 8.84c5.1 0 8.49-3.59 8.49-8.64 0-.58-.06-1.02-.14-1.46z" />
    </svg>
  );
}

const links = [
  { href: contact.facebook, label: "SL Tours on Facebook", icon: <Facebook aria-hidden="true" /> },
  { href: contact.instagram, label: "SL Tours on Instagram", icon: <Instagram aria-hidden="true" /> },
  { href: contact.tiktok, label: "SL Tours on TikTok", icon: <TikTokIcon /> },
  { href: contact.googleReview, label: "SL Tours reviews on Google", icon: <GoogleIcon /> }
];

export function SocialLinks({ className = "social-links" }: { className?: string }) {
  return (
    <div className={className}>
      {links.map((link) => (
        <a key={link.href} href={link.href} target="_blank" rel="noopener noreferrer" aria-label={link.label}>
          {link.icon}
        </a>
      ))}
    </div>
  );
}
