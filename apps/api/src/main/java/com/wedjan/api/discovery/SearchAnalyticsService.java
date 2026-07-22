package com.wedjan.api.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedjan.api.common.Uuidv7;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SearchAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(SearchAnalyticsService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SearchAnalyticsService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Async
    public void record(String query, Map<String, Object> filters, int count, UUID accountId,
            String sessionId, long durationMs) {
        try {
            jdbc.update("""
                    INSERT INTO search_events(id,query,filters,results_count,account_id,session_id,duration_ms)
                    VALUES (?,?,CAST(? AS jsonb),?,?,?,?)
                    """, Uuidv7.next(), query, objectMapper.writeValueAsString(filters), count,
                    accountId, sessionId, Math.min(durationMs, Integer.MAX_VALUE));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Could not record search analytics", ex);
        }
    }
}
