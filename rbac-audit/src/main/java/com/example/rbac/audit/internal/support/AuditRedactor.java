package com.example.rbac.audit.internal.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Defense-in-depth redaction for audit snapshots (01 FR-L10). RBAC services
 * already build whitelisted snapshots, but a future caller can accidentally put
 * a credential into an audit map; the recorder must not persist it.
 */
public final class AuditRedactor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwordhash", "oldpassword", "newpassword",
            "accesstoken", "refreshtoken", "token", "secret", "authorization",
            "jwt", "key");

    private AuditRedactor() {
    }

    public static String redact(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode redacted = redactNode(root);
            return MAPPER.writeValueAsString(redacted);
        } catch (Exception ex) {
            // Callers are expected to pass JSON. Never fail an audit write just
            // because a malformed snapshot cannot be parsed.
            return json;
        }
    }

    private static JsonNode redactNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode copy = MAPPER.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    copy.put(field.getKey(), "***");
                } else {
                    copy.set(field.getKey(), redactNode(field.getValue()));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = MAPPER.createArrayNode();
            node.forEach(value -> copy.add(redactNode(value)));
            return copy;
        }
        return node;
    }

    private static boolean isSensitive(String key) {
        return key != null && SENSITIVE_KEYS.contains(key.toLowerCase());
    }
}
