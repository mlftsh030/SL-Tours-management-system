package com.sltours.assistant.service;
import com.sltours.assistant.model.Conversation;
import java.util.*;
import java.util.regex.*;

/** Money and time figures: formatting for tool results and extraction for the reply checks. */
public final class Facts {
    private Facts() {}
    static final Pattern AMOUNT = Pattern.compile("R\\s?(\\d{1,3}(?:[ ,\\u00a0]\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?(?![\\d])");
    static final Pattern TIME = Pattern.compile("(?<![\\d:])([01]?\\d|2[0-3])[:h]([0-5]\\d)(?![\\d])");

    public static String rand(long cents) {
        return String.format(Locale.ROOT, "R%,.2f", cents / 100.0).replace(',', ' ');
    }
    static List<Long> amounts(String text) {
        List<Long> found = new ArrayList<>();
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            long rands = Long.parseLong(m.group(1).replaceAll("[ ,\\u00a0]", ""));
            String c = m.group(2) == null ? "0" : (m.group(2).length() == 1 ? m.group(2) + "0" : m.group(2));
            found.add(rands * 100 + Long.parseLong(c));
        }
        return found;
    }
    static List<String> times(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = TIME.matcher(text);
        while (m.find()) found.add(String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(m.group(1)), m.group(2)));
        return found;
    }
    /** Remembers figures that came from tools so the reply may repeat them. */
    static void absorb(Conversation c, String text) {
        c.allowedAmountsCents.addAll(amounts(text));
        c.allowedTimes.addAll(times(text));
        if (c.toolText.length() < 400_000) c.toolText.append('\n').append(text.toLowerCase(Locale.ROOT));
    }
}
