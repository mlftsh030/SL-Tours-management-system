package com.sltours.assistant.service;
import com.sltours.assistant.model.Conversation;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic checks on the model's reply before the visitor sees it. The model may only repeat amounts and times that
 * came from the tools or from the visitor's own details, may not mention discounts the catalogue does not contain, and
 * may not claim bookings, availability or a sent enquiry. A failing reply is replaced, so a hallucinated figure never
 * reaches the visitor.
 */
public final class ReplyGuard {
    private ReplyGuard() {}
    private static final Pattern DISCOUNT = Pattern.compile("(?i)\\b(discount(ed)?|\\d+\\s?% off|percent off|promo(tion(al)?)?( code)?|special offer|coupon|voucher|cheaper online)\\b");
    private static final Pattern COMMITMENT = Pattern.compile("(?i)(guarantee[ds]?|reserved for you|booked for you|i('ve| have) (booked|reserved)"
        + "|(is|are|has been|have been) (now )?(booked|reserved)|availability (is )?confirmed|confirmed availability|(seats?|spaces?|tickets?|places?) (is |are )?(still )?available)");
    private static final Pattern SENT = Pattern.compile("(?i)(i('ve| have) (sent|submitted|forwarded)|(has|have) been (sent|submitted|forwarded)|enquiry (is|was) (sent|submitted))");

    public static List<String> problems(String reply, Conversation c) {
        List<String> problems = new ArrayList<>();
        for (long amount : Facts.amounts(reply))
            if (!c.allowedAmountsCents.contains(amount)) problems.add("amount not from tools: " + Facts.rand(amount));
        for (String time : Facts.times(reply))
            if (!c.allowedTimes.contains(time)) problems.add("time not from tools: " + time);
        var discount = DISCOUNT.matcher(reply);
        while (discount.find())
            if (!c.toolText.toString().contains(discount.group(1).toLowerCase(Locale.ROOT).replaceAll("\\d+\\s?", "")))
                problems.add("discount not in catalogue");
        if (COMMITMENT.matcher(reply).find()) problems.add("booking or availability claim");
        if (c.submittedReference == null && SENT.matcher(reply).find()) problems.add("claims the plan was sent");
        return problems;
    }
}
