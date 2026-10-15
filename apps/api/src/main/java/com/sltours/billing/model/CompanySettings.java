package com.sltours.billing.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** The single row of company, banking, terms and tax settings used for new documents. Issued documents keep their own copy. */
@Entity
@Table(name = "company_settings")
public class CompanySettings {
    @Id private Short id = 1;
    @Version private long version;
    @Column(nullable = false, length = 160) private String companyName;
    @Column(length = 240) private String tagline;
    @Column(length = 60) private String phone;
    @Column(length = 60) private String whatsapp;
    @Column(length = 180) private String email;
    @Column(length = 180) private String website;
    @Column(nullable = false, length = 400) private String address;
    @Column(nullable = false) private boolean addressNeedsReview;
    private UUID logoMediaId;
    @Column(length = 160) private String bankAccountName;
    @Column(length = 120) private String bankName;
    @Column(length = 40) private String bankAccountNumber;
    @Column(length = 60) private String bankAccountType;
    @Column(length = 20) private String bankBranchCode;
    @Column(length = 20) private String bankSwiftCode;
    @Column(length = 500) private String paymentInstructions;
    @Column(nullable = false, columnDefinition = "text") private String invoiceTerms;
    @Column(nullable = false, columnDefinition = "text") private String quotationTerms;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false) private boolean taxEnabled;
    @Column(length = 20) private String taxLabel;
    private Integer taxRateBasisPoints;
    @Column(length = 40) private String taxNumber;
    private Integer defaultDueDays;
    private Integer quotationValidDays;
    @Column(nullable = false, length = 180) private String updatedBy;
    @Column(nullable = false) private OffsetDateTime updatedAt;

    public long getVersion() { return version; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { companyName = v; }
    public String getTagline() { return tagline; }
    public void setTagline(String v) { tagline = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String v) { whatsapp = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getWebsite() { return website; }
    public void setWebsite(String v) { website = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v; }
    public boolean isAddressNeedsReview() { return addressNeedsReview; }
    public void setAddressNeedsReview(boolean v) { addressNeedsReview = v; }
    public UUID getLogoMediaId() { return logoMediaId; }
    public void setLogoMediaId(UUID v) { logoMediaId = v; }
    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String v) { bankAccountName = v; }
    public String getBankName() { return bankName; }
    public void setBankName(String v) { bankName = v; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String v) { bankAccountNumber = v; }
    public String getBankAccountType() { return bankAccountType; }
    public void setBankAccountType(String v) { bankAccountType = v; }
    public String getBankBranchCode() { return bankBranchCode; }
    public void setBankBranchCode(String v) { bankBranchCode = v; }
    public String getBankSwiftCode() { return bankSwiftCode; }
    public void setBankSwiftCode(String v) { bankSwiftCode = v; }
    public String getPaymentInstructions() { return paymentInstructions; }
    public void setPaymentInstructions(String v) { paymentInstructions = v; }
    public String getInvoiceTerms() { return invoiceTerms; }
    public void setInvoiceTerms(String v) { invoiceTerms = v; }
    public String getQuotationTerms() { return quotationTerms; }
    public void setQuotationTerms(String v) { quotationTerms = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public boolean isTaxEnabled() { return taxEnabled; }
    public void setTaxEnabled(boolean v) { taxEnabled = v; }
    public String getTaxLabel() { return taxLabel; }
    public void setTaxLabel(String v) { taxLabel = v; }
    public Integer getTaxRateBasisPoints() { return taxRateBasisPoints; }
    public void setTaxRateBasisPoints(Integer v) { taxRateBasisPoints = v; }
    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String v) { taxNumber = v; }
    public Integer getDefaultDueDays() { return defaultDueDays; }
    public void setDefaultDueDays(Integer v) { defaultDueDays = v; }
    public Integer getQuotationValidDays() { return quotationValidDays; }
    public void setQuotationValidDays(Integer v) { quotationValidDays = v; }
    public String getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch(String actor) { updatedBy = actor; updatedAt = OffsetDateTime.now(); }
}
