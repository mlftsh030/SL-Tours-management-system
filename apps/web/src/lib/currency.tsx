import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { formatMoney } from "./format";

export const SUPPORTED_CURRENCIES = ["ZAR", "USD", "EUR", "GBP"] as const;
export type Currency = (typeof SUPPORTED_CURRENCIES)[number];

const RATES_ENDPOINT = "https://api.frankfurter.dev/v1/latest?from=ZAR";
const RATES_CACHE_KEY = "slToursRatesZAR";
const RATES_TTL_MS = 60 * 60 * 1000;
const CURRENCY_STORAGE_KEY = "slToursSelectedCurrency";
const SYMBOLS: Record<Currency, string> = { ZAR: "R", USD: "$", EUR: "€", GBP: "£" };

type Rates = Partial<Record<Currency, number>>;

interface CurrencyContextValue {
  currency: Currency;
  setCurrency: (currency: Currency) => void;
  /** Converted display amount, or null when the price is shown in ZAR (or rates are unavailable). */
  convert: (zarCents: number) => string | null;
}

const CurrencyContext = createContext<CurrencyContextValue>({
  currency: "ZAR",
  setCurrency: () => undefined,
  convert: () => null
});

function isCurrency(value: unknown): value is Currency {
  return typeof value === "string" && (SUPPORTED_CURRENCIES as readonly string[]).includes(value);
}

function readStoredCurrency(): Currency {
  try {
    const stored = localStorage.getItem(CURRENCY_STORAGE_KEY);
    return isCurrency(stored) ? stored : "ZAR";
  } catch {
    return "ZAR";
  }
}

function readCachedRates(): Rates | null {
  try {
    const raw = sessionStorage.getItem(RATES_CACHE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as { rates?: Rates; fetchedAt?: number };
    if (!parsed.rates || !parsed.fetchedAt || Date.now() - parsed.fetchedAt > RATES_TTL_MS) {
      return null;
    }
    return parsed.rates;
  } catch {
    return null;
  }
}

async function fetchRates(): Promise<Rates | null> {
  const cached = readCachedRates();
  if (cached) {
    return cached;
  }
  try {
    const response = await fetch(RATES_ENDPOINT);
    if (!response.ok) {
      return null;
    }
    const data = (await response.json()) as { rates?: Rates };
    if (!data.rates) {
      return null;
    }
    const rates = { ...data.rates, ZAR: 1 };
    try {
      sessionStorage.setItem(RATES_CACHE_KEY, JSON.stringify({ rates, fetchedAt: Date.now() }));
    } catch {
      // Storage can be unavailable (private browsing); rates still work for this page view.
    }
    return rates;
  } catch {
    return null;
  }
}

export function CurrencyProvider({ children }: { children: ReactNode }) {
  const [currency, setCurrencyState] = useState<Currency>(readStoredCurrency);
  const [rates, setRates] = useState<Rates | null>(null);

  useEffect(() => {
    if (currency === "ZAR" || rates) {
      return;
    }
    let cancelled = false;
    void fetchRates().then((result) => {
      if (!cancelled) {
        setRates(result);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [currency, rates]);

  const setCurrency = useCallback((next: Currency) => {
    setCurrencyState(next);
    try {
      localStorage.setItem(CURRENCY_STORAGE_KEY, next);
    } catch {
      // Selection still applies for this visit.
    }
  }, []);

  const convert = useCallback(
    (zarCents: number) => {
      const rate = rates?.[currency];
      if (currency === "ZAR" || !rate || zarCents === 0) {
        return null;
      }
      const amount = Math.round((zarCents / 100) * rate);
      return `≈ ${SYMBOLS[currency]}${amount.toLocaleString(undefined, { maximumFractionDigits: 0 })}`;
    },
    [currency, rates]
  );

  const value = useMemo(() => ({ currency, setCurrency, convert }), [convert, currency, setCurrency]);
  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
}

export function useCurrency() {
  return useContext(CurrencyContext);
}

/** Shows a ZAR price, converted to the visitor's chosen currency when live rates are available. */
export function Price({ cents }: { cents: number }) {
  const { convert } = useCurrency();
  const zar = formatMoney(cents);
  const converted = convert(cents);
  if (!converted) {
    return <>{zar}</>;
  }
  return <span title={`${zar} (approx., live rate)`}>{converted}</span>;
}

export function CurrencySelect({ className }: { className?: string }) {
  const { currency, setCurrency } = useCurrency();
  return (
    <label className={className}>
      <span className="sr-only">Currency</span>
      <select value={currency} onChange={(event) => isCurrency(event.target.value) && setCurrency(event.target.value)}>
        {SUPPORTED_CURRENCIES.map((code) => (
          <option key={code} value={code}>
            {code}
          </option>
        ))}
      </select>
    </label>
  );
}
