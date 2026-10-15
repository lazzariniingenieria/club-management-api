package com.lazzariniingenieria.clubmanagementapi.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ClubMigrationScriptTest {

    private static final String CREATE_CLUB_SCRIPT = "db/migration/V9__create_club_table.sql";
    private static final String CLUB_FOREIGN_KEYS_SCRIPT = "db/migration/V10__add_club_foreign_keys.sql";

    @Test
    void shouldSeedClubOneEvenWhenTheDatabaseHasNoMembersYet() throws IOException {
        String script = readNormalized(CREATE_CLUB_SCRIPT);

        assertThat(script).contains("insert into club (id, name) select referenced.club_id");
        assertThat(script).contains("union select 1) as referenced");
    }

    @Test
    void shouldSeedEveryClubIdAlreadyReferencedByMembersAndAccounts() throws IOException {
        String script = readNormalized(CREATE_CLUB_SCRIPT);

        assertThat(script).contains("select club_id from member");
        assertThat(script).contains("select club_id from user_account");
    }

    @Test
    void shouldMoveIdentitySequencePastTheSeededIds() throws IOException {
        String script = readNormalized(CREATE_CLUB_SCRIPT);

        assertThat(script).contains("select setval(pg_get_serial_sequence('club', 'id'), (select max(id) from club))");
    }

    @Test
    void shouldLinkMembersToClubInTheMigrationAfterTheSeed() throws IOException {
        String script = readNormalized(CLUB_FOREIGN_KEYS_SCRIPT);

        assertThat(script).contains("alter table member add constraint fk_member_club foreign key (club_id) references club(id)");
    }

    @Test
    void shouldLinkUserAccountsToClubInTheMigrationAfterTheSeed() throws IOException {
        String script = readNormalized(CLUB_FOREIGN_KEYS_SCRIPT);

        String expectedConstraint = "alter table user_account add constraint fk_user_account_club "
                + "foreign key (club_id) references club(id)";

        assertThat(script).contains(expectedConstraint);
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
