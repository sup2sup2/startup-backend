package com.example.startup.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadQueryExecutorTests {
    @Test
    void retriesOnPrimaryWhenReplicaFails() {
        JdbcTemplate primary = mock(JdbcTemplate.class);
        JdbcTemplate replica = mock(JdbcTemplate.class);
        when(replica.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("replica unavailable"));
        when(primary.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        ReadQueryExecutor executor = new ReadQueryExecutor(primary, replica);

        Integer result = executor.execute(
                jdbc -> jdbc.queryForObject("SELECT 1", Integer.class));

        assertEquals(1, result);
        verify(replica).queryForObject("SELECT 1", Integer.class);
        verify(primary).queryForObject("SELECT 1", Integer.class);
    }
}
