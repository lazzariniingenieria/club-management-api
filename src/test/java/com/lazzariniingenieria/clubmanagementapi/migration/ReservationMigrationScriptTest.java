package com.lazzariniingenieria.clubmanagementapi.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ReservationMigrationScriptTest {

    private static final String CREATE_RESERVATION_SCRIPT = "db/migration/V16__create_reservation_table.sql";

    @Test
    void shouldEnableBtreeGistBeforeTheExclusionConstraintNeedsIt() throws IOException {
        String script = readNormalized(CREATE_RESERVATION_SCRIPT);

        assertThat(script).startsWith("create extension if not exists btree_gist;");
    }

    @Test
    void shouldRejectOverlappingConfirmedReservationsOfTheSameCourt() throws IOException {
        String script = readNormalized(CREATE_RESERVATION_SCRIPT);

        String expectedConstraint = "constraint ex_reservation_no_overlap exclude using gist ( court_id with =, "
                + "tsrange(date + start_time, date + end_time) with && ) where (status = 'confirmed')";

        assertThat(script).contains(expectedConstraint);
    }

    @Test
    void shouldRequireAMemberUnlessTheReservationIsAnActivity() throws IOException {
        String script = readNormalized(CREATE_RESERVATION_SCRIPT);

        assertThat(script).contains("check (member_id is not null or source = 'activity')");
    }

    @Test
    void shouldKeepCancellationFieldsConsistentWithTheStatus() throws IOException {
        String script = readNormalized(CREATE_RESERVATION_SCRIPT);

        assertThat(script).contains("check ((cancelled_at is null) = (cancelled_by_user_id is null))");
        assertThat(script).contains("check ((status = 'cancelled') = (cancelled_at is not null))");
    }

    @Test
    void shouldRejectReservationsThatDoNotEndAfterTheyStart() throws IOException {
        String script = readNormalized(CREATE_RESERVATION_SCRIPT);

        assertThat(script).contains("constraint ck_reservation_ends_after_starts check (end_time > start_time)");
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
