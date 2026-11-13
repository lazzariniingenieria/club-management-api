package com.lazzariniingenieria.clubmanagementapi.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RecurringSlotMigrationScriptTest {

    private static final String CREATE_RECURRING_SLOT_SCRIPT = "db/migration/V17__create_recurring_slot_table.sql";

    @Test
    void shouldStoreTheDayOfWeekAsAnIsoNumber() throws IOException {
        String script = readNormalized(CREATE_RECURRING_SLOT_SCRIPT);

        assertThat(script).contains("check (day_of_week between 1 and 7)");
    }

    @Test
    void shouldCapTheValidityAtOneYear() throws IOException {
        String script = readNormalized(CREATE_RECURRING_SLOT_SCRIPT);

        assertThat(script).contains("check ( valid_until > valid_from and valid_until <= valid_from + interval '1 year' )");
    }

    @Test
    void shouldRequireExactlyOneOfMemberOrDescription() throws IOException {
        String script = readNormalized(CREATE_RECURRING_SLOT_SCRIPT);

        assertThat(script).contains("check ((member_id is null) <> (description is null))");
    }

    @Test
    void shouldKeepTheDeactivationFieldsConsistentWithActive() throws IOException {
        String script = readNormalized(CREATE_RECURRING_SLOT_SCRIPT);

        assertThat(script).contains("check ((deactivated_at is null) = (deactivated_by_user_id is null))");
        assertThat(script).contains("check (active = (deactivated_at is null))");
    }

    @Test
    void shouldReplaceTheReservationMemberCheckWithAStrictActivityRule() throws IOException {
        String script = readNormalized(CREATE_RECURRING_SLOT_SCRIPT);

        assertThat(script).contains("alter table reservation drop constraint ck_reservation_member_or_activity;");
        assertThat(script).contains("check ((member_id is null) = (source = 'activity'))");
    }

    @Test
    void shouldLinkEveryNonMemberReservationToARecurringSlot() throws IOException {
        String script = readNormalized(CREATE_RECURRING_SLOT_SCRIPT);

        assertThat(script).contains("foreign key (recurring_slot_id) references recurring_slot(id)");
        assertThat(script).contains("check ((recurring_slot_id is null) = (source = 'member'))");
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
