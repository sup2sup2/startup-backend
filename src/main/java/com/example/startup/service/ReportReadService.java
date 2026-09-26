package com.example.startup.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportReadService {
    private final ReadQueryExecutor readQueryExecutor;

    public ReportReadService(ReadQueryExecutor readQueryExecutor) {
        this.readQueryExecutor = readQueryExecutor;
    }

    public List<ReportItem> findLatest(int limit) {
        return readQueryExecutor.execute(jdbc -> queryLatest(jdbc, null, limit));
    }

    public ReportPage findPage(Long beforeId, int size) {
        List<ReportItem> fetched = readQueryExecutor.execute(
                jdbc -> queryLatest(jdbc, beforeId, size + 1));
        boolean hasNext = fetched.size() > size;
        List<ReportItem> items = hasNext
                ? new ArrayList<>(fetched.subList(0, size))
                : fetched;
        Long nextCursor = hasNext && !items.isEmpty()
                ? items.get(items.size() - 1).id()
                : null;
        return new ReportPage(items, nextCursor, hasNext, readQueryExecutor.isReplicaConfigured());
    }

    public long countSince(LocalDateTime since) {
        return readQueryExecutor.execute(jdbc -> {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM report WHERE created_at >= ?", Long.class, since);
            return count == null ? 0L : count;
        });
    }

    private List<ReportItem> queryLatest(JdbcTemplate jdbc, Long beforeId, int limit) {
        String select = "SELECT id, latitude, longitude, image_url, description, created_at "
                + "FROM report ";
        String order = "ORDER BY id DESC LIMIT ?";
        if (beforeId == null) {
            return jdbc.query(select + order, (rs, rowNum) -> new ReportItem(
                    rs.getLong("id"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getString("image_url"),
                    rs.getString("description"),
                    rs.getTimestamp("created_at").toLocalDateTime()), limit);
        }
        return jdbc.query(select + "WHERE id < ? " + order, (rs, rowNum) -> new ReportItem(
                rs.getLong("id"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("image_url"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime()), beforeId, limit);
    }

    public record ReportItem(Long id, Double latitude, Double longitude,
            String imageUrl, String description, LocalDateTime createdAt) {
    }

    public record ReportPage(List<ReportItem> items, Long nextCursor, boolean hasNext,
            boolean replicaConfigured) {
    }
}
