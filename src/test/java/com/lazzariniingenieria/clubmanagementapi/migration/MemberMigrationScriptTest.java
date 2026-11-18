package com.lazzariniingenieria.clubmanagementapi.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MemberMigrationScriptTest {

    private static final String OPTIONAL_DNI_SCRIPT = "db/migration/V18__make_member_dni_optional.sql";
    private static final String UNIQUE_FULL_NAME_SCRIPT = "db/migration/V19__add_unique_member_full_name_index.sql";

    @Test
    void shouldLetAMemberBeStoredWithoutDni() throws IOException {
        String script = readNormalized(OPTIONAL_DNI_SCRIPT);

        assertThat(script).contains("alter table member alter column dni drop not null");
    }

    @Test
    void shouldKeepRejectingARepeatedDniWhenItBecomesOptional() throws IOException {
        String script = readNormalized(OPTIONAL_DNI_SCRIPT);

        assertThat(script).doesNotContain("drop constraint");
    }

    @Test
    void shouldKeepMemberFullNamesUniquePerClubIgnoringCase() throws IOException {
        String script = readNormalized(UNIQUE_FULL_NAME_SCRIPT);

        String expectedIndex = "create unique index uk_member_club_full_name on member "
                + "(club_id, lower(first_name), lower(last_name))";

        assertThat(script).contains(expectedIndex);
    }

    @Test
    void shouldCountInactiveMembersWhenCheckingTheFullName() throws IOException {
        String script = readNormalized(UNIQUE_FULL_NAME_SCRIPT);

        assertThat(script).doesNotContain(" where ");
    }

    @Test
    void shouldTellApartFullNamesThatOnlyDifferInAccents() throws IOException {
        String script = readNormalized(UNIQUE_FULL_NAME_SCRIPT);

        assertThat(script).doesNotContain("unaccent");
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
