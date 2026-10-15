package com.sltours.assistant.model;
import com.sltours.activities.dto.ItineraryDtos.Transport;
import com.sltours.activities.model.ActivityEnums.*;
import java.time.*;
import java.util.*;

/** The details the assistant collects before it may call the planner. Only validated values are stored. */
public class TripBrief {
    public enum BudgetBasis { WHOLE_GROUP, PER_PERSON, NO_BUDGET }
    public LocalDate date;
    public LocalTime start, end;
    public Integer adults;
    public List<Integer> childAges = new ArrayList<>();
    public boolean childrenAnswered;
    public Residency residency;
    public Area startArea;
    public Transport transport;
    public List<Category> interests = new ArrayList<>();
    public boolean interestsAnswered;
    public BudgetBasis budgetBasis;
    public Integer budgetCents;
    public Integer foodPerPersonCents;
    public int stops = 3;

    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (date == null) missing.add("date");
        if (start == null || end == null) missing.add("available time (start and finish)");
        if (adults == null) missing.add("number of adults");
        if (!childrenAnswered) missing.add("children's ages, or confirmation that no children are coming");
        if (residency == null) missing.add("where the travellers are resident (prices differ)");
        if (startArea == null) missing.add("starting area");
        if (transport == null) missing.add("transport preference (own car or SL Tours vehicle with driver)");
        if (!interestsAnswered) missing.add("interests (or 'anything')");
        if (budgetBasis == null || (budgetBasis != BudgetBasis.NO_BUDGET && budgetCents == null)) missing.add("budget and whether it is per person or for the whole group (or no budget)");
        return missing;
    }
    public int travellers() { return (adults == null ? 0 : adults) + childAges.size(); }
    /** The budget as the planner needs it: one total for the group. */
    public Integer totalBudgetCents() {
        if (budgetBasis == null || budgetBasis == BudgetBasis.NO_BUDGET || budgetCents == null) return null;
        return budgetBasis == BudgetBasis.PER_PERSON ? Math.multiplyExact(budgetCents, Math.max(1, travellers())) : budgetCents;
    }
}
