package com.sltours.billing.render;

/**
 * Amounts as printed on SL Tours documents: a space between thousands, no cents when the amount is whole
 * ("2 850", "R2 850"), a decimal comma otherwise ("2 850,50"), and brackets for deductions ("(22 900)").
 */
public final class MoneyFormat {
    private MoneyFormat() {}

    /** For the COST column, whose header already names the currency. */
    public static String amount(long cents) {
        String digits = digits(Math.abs(cents));
        return cents < 0 ? "(" + digits + ")" : digits;
    }

    /** With the currency prefix, e.g. "R2 850" or "USD 1 200". */
    public static String withCurrency(long cents, String currency) {
        String prefix = currency == null || currency.equals("ZAR") ? "R" : currency + " ";
        return (cents < 0 ? "-" : "") + prefix + digits(Math.abs(cents));
    }

    public static String symbol(String currency) { return currency == null || currency.equals("ZAR") ? "R" : currency; }

    private static String digits(long cents) {
        long whole = cents / 100, part = cents % 100;
        String grouped = String.format(java.util.Locale.ROOT, "%,d", whole).replace(',', ' ');
        return part == 0 ? grouped : grouped + "," + String.format("%02d", part);
    }
}
