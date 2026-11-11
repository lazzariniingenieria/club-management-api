package com.lazzariniingenieria.clubmanagementapi.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CourtBlockMigrationScriptTest {

    private static final String CREATE_COURT_BLOCK_SCRIPT = "db/migration/V15__create_court_block_table.sql";

    @Test
    void shouldLinkEveryBlockToItsCourt() throws IOException {
        String script = readNormalized(CREATE_COURT_BLOCK_SCRIPT);

        assertThat(script).contains("constraint fk_court_block_court foreign key (court_id) references court(id)");
    }

    @Test
    void shouldRejectBlocksThatDoNotEndAfterTheyStart() throws IOException {
        String script = readNormalized(CREATE_COURT_BLOCK_SCRIPT);

        assertThat(script).contains("constraint ck_court_block_ends_after_starts check (ends_at > starts_at)");
    }

    @Test
    void shouldStoreTheRangeAsLocalTimestampsOfTheClub() throws IOException {
        String script = readNormalized(CREATE_COURT_BLOCK_SCRIPT);

        assertThat(script).contains("starts_at timestamp not null");
        assertThat(script).contains("ends_at timestamp not null");
    }

    @Test
    void shouldAlwaysRecordWhoBlockedTheCourt() throws IOException {
        String script = readNormalized(CREATE_COURT_BLOCK_SCRIPT);

        assertThat(script).contains("created_by_user_id bigint not null");
    }

    private String readNormalized(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            String script = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            String withoutComments = script.replaceAll("--[^\\n]*", "");
            String collapsedWhitespace = withoutComments.replaceAll("\\s+", " ");

            return collapsedWhitespace.toLowerCase(Locale.ROOT);
        }
    }
}
