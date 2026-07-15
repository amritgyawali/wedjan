package com.wedjan.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Typed accessor over the app_config table — the single mechanism for
 * feature flags and tunables (no env-only flags, per convention).
 */
@Service
public class AppConfigService {

    private static final Logger log = LoggerFactory.getLogger(AppConfigService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AppConfigService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return read(key).map(JsonNode::asBoolean).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return read(key).map(JsonNode::asInt).orElse(defaultValue);
    }

    public String getString(String key, String defaultValue) {
        return read(key).map(node -> node.isTextual() ? node.asText() : node.toString())
                .orElse(defaultValue);
    }

    private Optional<JsonNode> read(String key) {
        try {
            String raw = jdbcTemplate.queryForObject(
                    "select value from app_config where key = ? and deleted_at is null",
                    String.class, key);
            return raw == null ? Optional.empty() : Optional.of(objectMapper.readTree(raw));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to read app_config key {}", key, e);
            return Optional.empty();
        }
    }
}
