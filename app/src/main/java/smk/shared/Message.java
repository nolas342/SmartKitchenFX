package smk.shared;

/**
 * Minimal wire message used between client and server.
 * Format is a single-line JSON with a fixed set of fields so we can parse
 * without bringing an external JSON library.
 */
public class Message {
    public enum MessageType { REQUEST, REPLY, RELEASE, ORDER, READY, START, DONE, LOG }

    private MessageType type;
    private String client;
    private String dish;
    private int ts;
    private int lamport;
    private String text;

    public Message() {}

    public Message(MessageType type, String client, String dish, int ts, int lamport, String text) {
        this.type = type;
        this.client = client;
        this.dish = dish;
        this.ts = ts;
        this.lamport = lamport;
        this.text = text;
    }

    public MessageType getType() { return type; }
    public String getClient() { return client; }
    public String getDish() { return dish; }
    public int getTs() { return ts; }
    public int getLamport() { return lamport; }
    public String getText() { return text; }

    public void setType(MessageType type) { this.type = type; }
    public void setClient(String client) { this.client = client; }
    public void setDish(String dish) { this.dish = dish; }
    public void setTs(int ts) { this.ts = ts; }
    public void setLamport(int lamport) { this.lamport = lamport; }
    public void setText(String text) { this.text = text; }

    /** Serialize to a tiny JSON string; fields not used are omitted. */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        append(sb, "type", type.name());
        if (client != null) append(sb, "client", client);
        if (dish != null) append(sb, "dish", dish);
        if (ts != 0) append(sb, "ts", ts);
        if (lamport != 0) append(sb, "lamport", lamport);
        if (text != null) append(sb, "text", text);
        // remove last comma if present
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        sb.append('}');
        return sb.toString();
    }

    private static void append(StringBuilder sb, String key, String val) {
        sb.append('"').append(key).append('"').append(':')
          .append('"').append(escape(val)).append('"').append(',');
    }

    private static void append(StringBuilder sb, String key, int val) {
        sb.append('"').append(key).append('"').append(':').append(val).append(',');
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Extremely small parser for the fixed shape we produce in toJson().
     * It does not aim to be a general JSON parser but is fine for this controlled format.
     */
    public static Message fromJson(String json) {
        Message m = new Message();
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1); // drop braces
        }
        String[] parts = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String part : parts) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            String key = strip(kv[0]);
            String valRaw = kv[1].trim();
            switch (key) {
                case "type" -> {
                    try { m.type = MessageType.valueOf(strip(valRaw)); } catch (Exception ignored) {}
                }
                case "client" -> m.client = strip(valRaw);
                case "dish" -> m.dish = strip(valRaw);
                case "ts" -> m.ts = parseInt(valRaw);
                case "lamport" -> m.lamport = parseInt(valRaw);
                case "text" -> m.text = strip(valRaw);
                default -> {}
            }
        }
        return m;
    }

    private static String strip(String s) {
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); }
        catch (Exception e) { return 0; }
    }
}
