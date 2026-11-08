package com.lazzariniingenieria.clubmanagementapi.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CourtMigrationScriptTest {

    private static final String CREATE_COURT_SCRIPT = "db/migration/V14__create_court_table.sql";

    @Test
    void shouldLinkEveryCourtToItsClub() throws IOException {
        String script = readNormalized(CREATE_COURT_SCRIPT);

        assertThat(script).contains("constraint fk_court_club foreign key (club_id) references club(id)");
    }

    @Test
    void shouldRejectCourtsThatDoNotCloseAfterTheyOpen() throws IOException {
        String script = readNormalized(CREATE_COURT_SCRIPT);

        assertThat(script).contains("constraint ck_court_closing_after_opening check (closing_time > opening_time)");
    }

    @Test
    void shouldKeepCourtNamesUniquePerClubIgnoringCase() throws IOException {
        String script = readNormalized(CREATE_COURT_SCRIPT);

        assertThat(script).contains("create unique index uk_court_club_name on court (club_id, lower(name))");
    }

    @Test
    void shouldCreateCourtsAsActiveByDefault() throws IOException {
        String script = readNormalized(CREATE_COURT_SCRIPT);

        assertThat(script).contains("active boolean not null default true");
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
