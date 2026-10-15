package com.sltours.assistant.provider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sltours.activities.dto.ActivityDtos.ActivityCard;
import com.sltours.activities.service.ActivityService;
import com.sltours.assistant.model.ChatMessage;
import com.sltours.assistant.model.ChatMessage.*;
import com.sltours.assistant.service.AssistantService;
import com.sltours.assistant.service.Narrator;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * DEVELOPMENT MOCK, not a language model. Follows simple keyword rules so the tool loop, limits, checks and enquiry flow
 * can be exercised without credentials. Allowed only with the dev or test profile, and labelled as a mock in the UI.
 * Its only shortcut is matching activity titles for swaps through the same public listing the search tool uses.
 */
public class MockChatModel implements ChatModel {
    private static final Pattern INJECTION = Pattern.compile("(?i)(ignore (all |any |the )?(previous |prior |above )?instructions|system prompt|developer mode"
        + "|\\badmin\\b|drivers? (list|details|records|names|numbers)|list (the|all) drivers|inspection|password|api key|customer (list|data|details|records)|booking records|other (visitors|customers))");
    private static final Pattern SWAP = Pattern.compile("(?i)(?:swap|replace|change)\\s+(?:the\\s+)?(.+?)\\s+(?:with|for)\\s+(?:the\\s+)?(.+?)(?:\\s+in option\\s+([abc]))?[.!?]?$");
    private static final Pattern REMOVE = Pattern.compile("(?i)(?:remove|drop|skip)\\s+(?:the\\s+)?(.+?)(?:\\s+from option\\s+([abc]))?[.!?]?$");
    private static final Pattern SEND = Pattern.compile("(?i)\\b(send|submit|forward|enquire|enquiry|book)\\b");
    private static final Pattern OPTION = Pattern.compile("(?i)option\\s+([abc])\\b");
    private final ObjectMapper json;
    private final ActivityService activities;
    private final Clock clock;
    private final java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();

    public MockChatModel(ObjectMapper json, ActivityService activities, Clock clock) { this.json = json; this.activities = activities; this.clock = clock; }
    @Override public String name() { return "development-mock"; }

    @Override
    public Response send(Request request) {
        ChatMessage last = request.messages().get(request.messages().size() - 1);
        boolean toolResults = last.blocks().stream().anyMatch(ToolResult.class::isInstance);
        ChatMessage reply = toolResults ? afterTools(request.messages(), last) : onVisitor(request.system(), request.messages(), last.text());
        return new Response(reply, reply.toolUses().isEmpty() ? "end_turn" : "tool_use", 0, 0);
    }

    private ChatMessage onVisitor(String system, List<ChatMessage> history, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (INJECTION.matcher(text).find())
            return ChatMessage.assistant("I can't help with that. I can only use SL Tours' public activity information and day planner to help plan a day in Cape Town.");
        if (lower.matches(".*\\b(weather|flight|visa|hotel|restaurant booking|currency|exchange rate|crime|safe to)\\b.*"))
            return ChatMessage.assistant("I can only help plan a day out in Cape Town using SL Tours' checked activity information and day planner. "
                + "I don't have reliable information about that. Please contact SL Tours or an official source.");
        List<JsonNode> options = latestOptions(system, history);
        Matcher swap = SWAP.matcher(text.trim());
        if (!options.isEmpty() && swap.find()) {
            String option = swap.group(3) != null ? swap.group(3).toUpperCase(Locale.ROOT) : optionContaining(options, swap.group(1));
            String remove = stopSlug(options, option, swap.group(1));
            String add = catalogueSlug(swap.group(2));
            if (option == null || remove == null) return ChatMessage.assistant("I couldn't find \"" + swap.group(1) + "\" in the options. Which stop should I replace?");
            if (add == null) return ChatMessage.assistant("I couldn't find \"" + swap.group(2) + "\" among the published activities.");
            return tool("swap_activity", json.createObjectNode().put("optionId", option).put("remove", remove).put("add", add));
        }
        Matcher remove = REMOVE.matcher(text.trim());
        if (!options.isEmpty() && remove.find()) {
            String option = remove.group(2) != null ? remove.group(2).toUpperCase(Locale.ROOT) : optionContaining(options, remove.group(1));
            String slug = stopSlug(options, option, remove.group(1));
            if (option != null && slug != null) return tool("swap_activity", json.createObjectNode().put("optionId", option).put("remove", slug));
        }
        Matcher optionMention = OPTION.matcher(text);
        if (!options.isEmpty() && SEND.matcher(text).find())
            return tool("offer_enquiry", json.createObjectNode().put("optionId", optionMention.find() ? optionMention.group(1).toUpperCase(Locale.ROOT) : "A"));
        ObjectNode details = extract(lower);
        if (!details.isEmpty()) return tool("update_trip_details", details);
        if (!options.isEmpty() && optionMention.find()) {
            String id = optionMention.group(1).toUpperCase(Locale.ROOT);
            return options.stream().filter(o -> o.path("id").asText().equals(id)).findFirst()
                .map(o -> ChatMessage.assistant(Narrator.option(o))).orElse(ChatMessage.assistant("There is no option " + id + "."));
        }
        return ChatMessage.assistant("I can help you plan a day in Cape Town. Tell me the date, your start and finish times, how many adults and children "
            + "(with ages), where you're resident, where you're starting from, whether you'll use your own car or an SL Tours vehicle, what you enjoy, "
            + "and your budget (per person or for the group).");
    }

    private ChatMessage afterTools(List<ChatMessage> history, ChatMessage results) {
        ChatMessage call = history.get(history.size() - 2);
        StringBuilder text = new StringBuilder();
        for (Block b : results.blocks()) {
            if (!(b instanceof ToolResult r)) continue;
            String name = call.toolUses().stream().filter(u -> u.id().equals(r.toolUseId())).map(ToolUse::name).findFirst().orElse("");
            JsonNode out = read(r.content());
            if (r.error()) { text.append("Sorry, I couldn't do that: ").append(out.path("error").asText()); continue; }
            switch (name) {
                case "update_trip_details" -> {
                    if (out.has("notSaved")) text.append("I couldn't use: ").append(join(out.get("notSaved"))).append(". ");
                    if (out.has("recalculatedOptions")) {
                        List<JsonNode> options = new ArrayList<>();
                        out.get("recalculatedOptions").forEach(options::add);
                        text.append("I've recalculated your options with the new details.\n\n").append(Narrator.options(options));
                    } else if (out.path("missing").size() > 0) {
                        text.append("Thanks. To plan your day I still need: ").append(join(out.get("missing"))).append(".");
                    } else return tool("plan_options", json.createObjectNode());
                }
                case "plan_options" -> {
                    List<JsonNode> options = new ArrayList<>();
                    out.path("options").forEach(options::add);
                    text.append(Narrator.options(options)).append("\n\nYou can ask me to swap an activity, or to send an option to SL Tours.");
                }
                case "swap_activity" -> {
                    if (out.has("added") && !out.get("added").asText().equals("yes")) text.append("That activity couldn't be added: ").append(out.get("added").asText()).append("\n\n");
                    text.append("Here is the updated plan.\n\n").append(Narrator.option(out.path("option")));
                }
                case "offer_enquiry" -> text.append("I've opened a form for ").append(out.path("formShownFor").asText())
                    .append(". Check the plan and add your contact details to send it to SL Tours. Nothing is sent until you confirm.");
                default -> text.append("Done.");
            }
        }
        return ChatMessage.assistant(text.toString().trim());
    }

    private ObjectNode extract(String t) {
        ObjectNode d = json.createObjectNode();
        LocalDate today = LocalDate.now(clock);
        Matcher date = Pattern.compile("\\b(20\\d\\d-\\d\\d-\\d\\d)\\b").matcher(t);
        if (date.find()) d.put("date", date.group(1));
        else if (t.contains("tomorrow")) d.put("date", today.plusDays(1).toString());
        Matcher time = Pattern.compile("(?:from\\s+)?(\\d{1,2})(?::(\\d\\d))?\\s*(am|pm)?\\s*(?:to|until|till|-)\\s*(\\d{1,2})(?::(\\d\\d))?\\s*(am|pm)?").matcher(t);
        while (time.find()) {
            if (time.group(3) == null && time.group(6) == null && time.group(2) == null) continue; // e.g. part of a date
            d.put("startTime", hhmm(time.group(1), time.group(2), time.group(3) == null ? "am" : time.group(3)));
            d.put("endTime", hhmm(time.group(4), time.group(5), time.group(6) == null ? "pm" : time.group(6)));
            break;
        }
        Matcher adults = Pattern.compile("(\\d+)\\s+adults?").matcher(t);
        if (adults.find()) d.put("adults", Integer.parseInt(adults.group(1)));
        Matcher kids = Pattern.compile("(?:child(?:ren)?|kids?)\\s+(?:aged?\\s+)?(\\d+(?:\\s*(?:,|and)\\s*\\d+)*)").matcher(t);
        if (kids.find()) {
            var ages = d.putArray("childAges");
            for (String a : kids.group(1).split("\\s*(?:,|and)\\s*")) ages.add(Integer.parseInt(a.trim()));
        } else if (t.matches(".*\\b(no (children|kids)|just adults|adults only)\\b.*")) d.put("noChildren", true);
        if (t.matches(".*\\b(south african|sa residents?|locals?)\\b.*")) d.put("residency", "SA_RESIDENT");
        else if (t.contains("sadc")) d.put("residency", "SADC");
        else if (t.matches(".*\\b(international|overseas|foreign|tourists from)\\b.*")) d.put("residency", "INTERNATIONAL");
        String[][] areas = {{"waterfront", "WATERFRONT"}, {"city centre", "CITY_CENTRE"}, {"city center", "CITY_CENTRE"}, {"camps bay", "ATLANTIC_SEABOARD"},
            {"sea point", "ATLANTIC_SEABOARD"}, {"constantia", "CONSTANTIA"}, {"hout bay", "HOUT_BAY_CHAPMANS"}, {"muizenberg", "FALSE_BAY"},
            {"simon's town", "FALSE_BAY"}, {"century city", "NORTHERN_SUBURBS"}, {"observatory", "SOUTHERN_SUBURBS"}, {"newlands", "SOUTHERN_SUBURBS"}};
        for (String[] a : areas) if (Pattern.compile("\\b(from|in|at|near) (the )?" + Pattern.quote(a[0])).matcher(t).find()) { d.put("startArea", a[1]); break; }
        if (t.matches(".*\\b(own car|our car|rental|hire car|drive ourselves|we'll drive|we will drive)\\b.*")) d.put("transport", "OWN_VEHICLE");
        else if (t.matches(".*\\b(with a driver|sl tours vehicle|chauffeur|private vehicle|your vehicle)\\b.*")) d.put("transport", "SL_TOURS_PRIVATE_VEHICLE");
        String[][] interests = {{"nature", "NATURE"}, {"penguin", "WILDLIFE"}, {"animal", "WILDLIFE"}, {"aquarium", "WILDLIFE"}, {"museum", "MUSEUM_HISTORY"},
            {"history", "MUSEUM_HISTORY"}, {"art", "ART_CULTURE"}, {"wine", "WINE"}, {"market", "FOOD_MARKET"}, {"beach", "BEACH"}, {"hik", "HIKING"},
            {"scenic", "SCENIC_DRIVE"}, {"family", "FAMILY_LEISURE"}, {"kart", "GO_KARTING"}, {"skating", "ICE_SKATING"}, {"cinema", "CINEMA"}, {"movie", "CINEMA"}};
        var chosen = new LinkedHashSet<String>();
        for (String[] i : interests) if (Pattern.compile("\\b" + i[0]).matcher(t).find()) chosen.add(i[1]);
        if (!chosen.isEmpty()) { var arr = d.putArray("interests"); chosen.forEach(arr::add); }
        else if (t.matches(".*\\b(anything|no preference|surprise us)\\b.*")) d.put("anyInterests", true);
        if (t.matches(".*\\bno budget\\b.*")) d.put("budgetBasis", "NO_BUDGET");
        Matcher budget = Pattern.compile("budget(?: is| of)?\\s*(?:about\\s+)?r\\s?(\\d[\\d ,]*)(\\s*(?:per person|each|pp))?").matcher(t);
        if (budget.find()) {
            d.put("budgetRand", Integer.parseInt(budget.group(1).replaceAll("[ ,]", "")));
            d.put("budgetBasis", budget.group(2) != null ? "PER_PERSON" : "WHOLE_GROUP");
        }
        Matcher food = Pattern.compile("r\\s?(\\d+)\\s*(?:per person|each|pp)?\\s*for food").matcher(t);
        if (food.find()) d.put("foodPerPersonRand", Integer.parseInt(food.group(1)));
        return d;
    }

    private static String hhmm(String h, String m, String ampm) {
        int hour = Integer.parseInt(h) % 12 + ("pm".equals(ampm) ? 12 : 0);
        return String.format(Locale.ROOT, "%02d:%s", hour, m == null ? "00" : m);
    }

    /** Full option results from the history where still readable, otherwise the current option list in the system prompt. */
    private List<JsonNode> latestOptions(String system, List<ChatMessage> history) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        int marker = system.indexOf(AssistantService.CURRENT_OPTIONS);
        if (marker >= 0) {
            String rest = system.substring(marker + AssistantService.CURRENT_OPTIONS.length());
            JsonNode current = read(rest.substring(0, rest.indexOf('\n') < 0 ? rest.length() : rest.indexOf('\n')));
            current.forEach(o -> byId.put(o.path("id").asText(), o));
        }
        Map<String, JsonNode> full = new LinkedHashMap<>();
        for (ChatMessage m : history) for (Block b : m.blocks()) {
            if (!(b instanceof ToolResult r) || r.error()) continue;
            JsonNode out = read(r.content());
            if (out.has("options")) { full.clear(); out.get("options").forEach(o -> full.put(o.path("id").asText(), o)); }
            if (out.has("recalculatedOptions")) out.get("recalculatedOptions").forEach(o -> full.put(o.path("id").asText(), o));
            if (out.has("option")) full.put(out.get("option").path("id").asText(), out.get("option"));
        }
        if (byId.isEmpty()) return List.copyOf(full.values());
        // The system prompt is authoritative for which options and stops exist; full results add the figures when still current.
        byId.replaceAll((id, o) -> full.containsKey(id) && full.get(id).path("stops").toString().contains(o.path("stops").path(0).path("slug").asText("~")) ? full.get(id) : o);
        return List.copyOf(byId.values());
    }
    private static String optionContaining(List<JsonNode> options, String phrase) {
        return options.stream().filter(o -> stopSlug(List.of(o), o.path("id").asText(), phrase) != null).map(o -> o.path("id").asText()).findFirst().orElse(null);
    }
    private static String stopSlug(List<JsonNode> options, String optionId, String phrase) {
        if (optionId == null) return null;
        for (JsonNode o : options) {
            if (!o.path("id").asText().equals(optionId)) continue;
            for (JsonNode s : o.path("stops")) if (s.has("slug") && matches(s.path("title").asText(), phrase)) return s.path("slug").asText();
        }
        return null;
    }
    private String catalogueSlug(String phrase) {
        return activities.list(null, null, null).stream().filter(a -> matches(a.title(), phrase)).map(ActivityCard::slug).findFirst().orElse(null);
    }
    private static boolean matches(String title, String phrase) {
        String t = title.toLowerCase(Locale.ROOT), p = phrase.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").trim();
        if (p.isEmpty()) return false;
        if (t.contains(p)) return true;
        return Arrays.stream(p.split("\\s+")).filter(w -> w.length() > 3).anyMatch(t::contains);
    }
    private ChatMessage tool(String name, ObjectNode input) {
        return new ChatMessage(ChatMessage.Role.ASSISTANT, List.of(new ToolUse("mock_" + ids.incrementAndGet(), name, input)));
    }
    private JsonNode read(String content) {
        try { return json.readTree(content); } catch (Exception e) { return json.createObjectNode(); }
    }
    private static String join(JsonNode array) {
        List<String> items = new ArrayList<>();
        array.forEach(n -> items.add(n.asText()));
        return String.join("; ", items);
    }
}
