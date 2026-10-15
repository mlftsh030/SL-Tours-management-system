import type { ReactNode } from "react";

interface PageBannerProps {
  title: string;
  subtitle?: string;
  /** One of SL Tours' own photos; defaults to the Chapman's Peak view over Hout Bay. */
  image?: string;
  children?: ReactNode;
}

export function PageBanner({ title, subtitle, image = "/assets/gallery/16.jpg", children }: PageBannerProps) {
  return (
    <section className="page-banner" style={{ backgroundImage: `url(${image})` }}>
      <div className="page-banner-inner">
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
        {children}
      </div>
    </section>
  );
}
