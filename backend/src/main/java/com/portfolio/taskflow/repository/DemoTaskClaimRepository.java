package com.portfolio.taskflow.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DemoTaskClaimRepository {

    private final JdbcClient jdbc;

    DemoTaskClaimRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isPending(String email) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM task_demo_claim
                    WHERE recipient_email = :email AND claimed_at IS NULL
                )
                """).param("email", email).query(Boolean.class).single();
    }

    public void assign(String email, long ownerId) {
        List<Long> pendingIds = jdbc.sql("""
                SELECT task_id FROM task_demo_claim
                WHERE recipient_email = :email AND claimed_at IS NULL
                ORDER BY task_id FOR UPDATE
                """).param("email", email).query(Long.class).list();
        if (pendingIds.isEmpty()) {
            return;
        }

        jdbc.sql("""
                UPDATE task_item SET owner_id = :ownerId
                WHERE owner_id IS NULL AND id IN (:taskIds)
                """).param("ownerId", ownerId).param("taskIds", pendingIds).update();

        jdbc.sql("""
                UPDATE task_demo_claim SET claimed_at = CURRENT_TIMESTAMP
                WHERE task_id IN (:taskIds)
                """).param("taskIds", pendingIds).update();
    }
}
