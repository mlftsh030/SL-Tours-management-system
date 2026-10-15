package com.sltours.billing.service;
import com.sltours.audit.AuditService;
import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.DocumentViews;
import com.sltours.billing.render.PdfRenderer;
import com.sltours.media.*;
import com.sltours.shared.error.ConflictException;
import java.io.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Company, banking, terms, tax and numbering settings for new documents. Changes never touch issued documents, which
 * carry their own snapshot. Banking changes are audited separately with the account number masked.
 */
@Service
public class BillingSettingsService implements PdfRenderer.LogoSource {
    public static final String BUNDLED_LOGO = "bundled:sl-tours-logo";
    private final CompanySettingsRepository settings;
    private final NumberAllocator numbers;
    private final MediaService media;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    public BillingSettingsService(CompanySettingsRepository settings, NumberAllocator numbers, MediaService media, AuditService audit, JdbcTemplate jdbc) {
        this.settings = settings; this.numbers = numbers; this.media = media; this.audit = audit; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CompanySettings current() { return settings.findById((short) 1).orElseThrow(() -> new IllegalStateException("Billing settings are missing")); }

    @Transactional(readOnly = true)
    public SettingsView view() { return view(current()); }

    public CompanyProfile company(CompanySettings s) {
        return new CompanyProfile(s.getCompanyName(), s.getTagline(), s.getPhone(), s.getWhatsapp(), s.getEmail(), s.getWebsite(), s.getAddress(),
            s.getLogoMediaId() == null ? BUNDLED_LOGO : "media:" + s.getLogoMediaId());
    }
    public Banking banking(CompanySettings s) {
        return new Banking(s.getBankAccountName(), s.getBankName(), s.getBankAccountNumber(), s.getBankAccountType(), s.getBankBranchCode(), s.getBankSwiftCode());
    }
    public List<String> terms(CompanySettings s, Kind kind) {
        return DocumentViews.termLines(kind == Kind.INVOICE ? s.getInvoiceTerms() : s.getQuotationTerms());
    }
    public BillingCalculator.TaxRule tax(CompanySettings s) {
        return s.isTaxEnabled() && s.getTaxRateBasisPoints() != null ? new BillingCalculator.TaxRule(s.getTaxRateBasisPoints(), s.getTaxLabel()) : null;
    }

    @Transactional
    public SettingsView update(SettingsUpdate r) {
        CompanySettings s = current();
        if (s.getVersion() != r.version()) throw new ConflictException("Settings were changed by someone else. Reload before saving.");
        if (r.taxEnabled() && (!StringUtils.hasText(r.taxLabel()) || r.taxRateBasisPoints() == null || r.taxRateBasisPoints() <= 0))
            throw new IllegalArgumentException("To charge tax, enter its name (for example VAT) and a rate above 0%");
        String bankBefore = bankSummary(s);
        s.setCompanyName(r.companyName().trim()); s.setTagline(trim(r.tagline())); s.setPhone(trim(r.phone())); s.setWhatsapp(trim(r.whatsapp()));
        s.setEmail(trim(r.email())); s.setWebsite(trim(r.website()));
        boolean addressChanged = !r.address().trim().equals(s.getAddress());
        s.setAddress(r.address().trim()); s.setAddressNeedsReview(r.addressNeedsReview());
        s.setBankAccountName(trim(r.bankAccountName())); s.setBankName(trim(r.bankName()));
        s.setBankAccountNumber(r.bankAccountNumber() == null || r.bankAccountNumber().isBlank() ? null : r.bankAccountNumber().replace(" ", ""));
        s.setBankAccountType(trim(r.bankAccountType())); s.setBankBranchCode(trim(r.bankBranchCode())); s.setBankSwiftCode(trim(r.bankSwiftCode()));
        s.setPaymentInstructions(trim(r.paymentInstructions()));
        s.setInvoiceTerms(r.invoiceTerms().trim()); s.setQuotationTerms(r.quotationTerms().trim());
        s.setCurrency(r.currency());
        s.setTaxEnabled(r.taxEnabled()); s.setTaxLabel(r.taxEnabled() ? r.taxLabel().trim() : trim(r.taxLabel()));
        s.setTaxRateBasisPoints(r.taxRateBasisPoints()); s.setTaxNumber(trim(r.taxNumber()));
        s.setDefaultDueDays(r.defaultDueDays()); s.setQuotationValidDays(r.quotationValidDays());
        s.touch(BillingSupport.actor());
        settings.flush();
        String bankAfter = bankSummary(s);
        if (!bankBefore.equals(bankAfter)) audit.record("BILLING_BANKING_CHANGED", "company_settings", bankBefore + " -> " + bankAfter);
        audit.record("BILLING_SETTINGS_CHANGED", "company_settings", "tax=" + (s.isTaxEnabled() ? s.getTaxLabel() + " " + s.getTaxRateBasisPoints() + "bp" : "off")
            + (addressChanged ? "; address changed" : "") + "; address review " + (s.isAddressNeedsReview() ? "pending" : "confirmed"));
        return view(s);
    }

    @Transactional
    public SettingsView uploadLogo(MultipartFile file) {
        MediaFile stored = media.store(file, MediaPurpose.COMPANY_LOGO, MediaFile.Visibility.PRIVATE, EnumSet.of(MediaService.Kind.JPEG, MediaService.Kind.PNG));
        CompanySettings s = current();
        s.setLogoMediaId(stored.getId());
        s.touch(BillingSupport.actor());
        settings.flush();
        audit.record("BILLING_LOGO_CHANGED", "company_settings", "logo media " + stored.getId());
        return view(s);
    }

    @Transactional
    public SettingsView useOriginalLogo() {
        CompanySettings s = current();
        s.setLogoMediaId(null);
        s.touch(BillingSupport.actor());
        settings.flush();
        audit.record("BILLING_LOGO_CHANGED", "company_settings", "original SL Tours logo restored");
        return view(s);
    }

    /** Sets the next number. It can never be at or below a number already issued in the system. */
    @Transactional
    public SettingsView updateSequence(NumberAllocator.Series series, SequenceUpdate r) {
        jdbc.query("select kind from document_sequences where kind = ? for update", (rs, i) -> rs.getString(1), series.name());
        long highest = numbers.highestUsed(series);
        if (r.nextNumber() <= highest)
            throw new IllegalArgumentException(label(series) + " " + highest + " is already in the system. The next number must be at least " + (highest + 1) + ".");
        jdbc.update("update document_sequences set next_number = ?, confirmed = ?, updated_by = ?, updated_at = ? where kind = ?",
            r.nextNumber(), r.confirmed(), BillingSupport.actor(), OffsetDateTime.now(), series.name());
        audit.record("BILLING_NUMBERING_CHANGED", series.name(), "next " + r.nextNumber() + (r.confirmed() ? " (confirmed)" : " (not confirmed)"));
        return view();
    }

    @Override
    public PdfRenderer.Logo resolve(String ref) {
        if (ref == null) return null;
        if (ref.startsWith("media:")) {
            MediaFile file = media.find(UUID.fromString(ref.substring(6)));
            return new PdfRenderer.Logo(media.readAll(file), file.getContentType());
        }
        try (InputStream in = BillingSettingsService.class.getResourceAsStream("/billing/sl-tours-logo.jpg")) {
            return new PdfRenderer.Logo(in.readAllBytes(), "image/jpeg");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SettingsView view(CompanySettings s) {
        List<SequenceView> sequences = jdbc.query("select kind, next_number, confirmed, updated_by, updated_at from document_sequences order by kind",
            (rs, i) -> new SequenceView(rs.getString(1), rs.getLong(2), rs.getBoolean(3),
                numbers.highestUsed(NumberAllocator.Series.valueOf(rs.getString(1))), rs.getString(4), rs.getObject(5, OffsetDateTime.class)));
        return new SettingsView(s.getVersion(), s.getCompanyName(), s.getTagline(), s.getPhone(), s.getWhatsapp(), s.getEmail(), s.getWebsite(),
            s.getAddress(), s.isAddressNeedsReview(), s.getLogoMediaId() == null ? "/assets/sl-tours-logo-original.jpg" : "/api/v1/admin/media/" + s.getLogoMediaId(),
            s.getLogoMediaId() != null, s.getBankAccountName(), s.getBankName(), s.getBankAccountNumber(), s.getBankAccountType(),
            s.getBankBranchCode(), s.getBankSwiftCode(), s.getPaymentInstructions(), s.getInvoiceTerms(), s.getQuotationTerms(), s.getCurrency(),
            s.isTaxEnabled(), s.getTaxLabel(), s.getTaxRateBasisPoints(), s.getTaxNumber(), s.getDefaultDueDays(), s.getQuotationValidDays(),
            sequences, s.getUpdatedBy(), s.getUpdatedAt());
    }

    static String label(NumberAllocator.Series series) {
        return switch (series) { case INVOICE -> "Invoice"; case QUOTATION -> "Quotation"; case RECEIPT -> "Receipt"; };
    }
    private static String bankSummary(CompanySettings s) {
        String number = s.getBankAccountNumber();
        String masked = number == null ? "none" : "****" + number.substring(Math.max(0, number.length() - 4));
        return String.join("/", Objects.toString(s.getBankAccountName(), ""), Objects.toString(s.getBankName(), ""), masked,
            Objects.toString(s.getBankAccountType(), ""), Objects.toString(s.getBankBranchCode(), ""), Objects.toString(s.getBankSwiftCode(), ""));
    }
    private static String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
