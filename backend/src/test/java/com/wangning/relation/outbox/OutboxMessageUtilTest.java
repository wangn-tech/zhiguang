package com.wangning.relation.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMessageUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExtractValidOutboxRowsFromCanalInsert() {
        String message = """
                {
                  "table": "outbox",
                  "type": "INSERT",
                  "data": [
                    {"id": "101", "payload": "{\\"type\\":\\"FollowCreated\\"}"},
                    {"id": "102", "payload": "{\\"type\\":\\"FollowCanceled\\"}"}
                  ]
                }
                """;

        List<CanalOutboxMessage> messages = OutboxMessageUtil.extractMessages(objectMapper, message);

        assertThat(messages).containsExactly(
                new CanalOutboxMessage(101L, "{\"type\":\"FollowCreated\"}"),
                new CanalOutboxMessage(102L, "{\"type\":\"FollowCanceled\"}")
        );
    }

    @Test
    void shouldIgnoreNonOutboxAndMalformedRows() {
        assertThat(OutboxMessageUtil.extractMessages(objectMapper, """
                {"table":"users","type":"INSERT","data":[]}
                """)).isEmpty();
        assertThat(OutboxMessageUtil.extractMessages(objectMapper, """
                {"table":"outbox","type":"DELETE","data":[]}
                """)).isEmpty();
        assertThat(OutboxMessageUtil.extractMessages(objectMapper, """
                {"table":"outbox","type":"INSERT","data":[{"id":"0","payload":""}]}
                """)).isEmpty();
        assertThat(OutboxMessageUtil.extractMessages(objectMapper, "not-json")).isEmpty();
    }
}
