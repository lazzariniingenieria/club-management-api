# CLAUDE.md — Backend (Spring Boot + PostgreSQL)

## Role
Act as a senior Java/Spring developer with 20 years of experience. Code must be simple, high-quality, and scalable. Favor clarity over cleverness.

## Stack
- Java 25 (LTS)
- Spring Boot 4.1.x, Spring Framework 7.x
- PostgreSQL (only on Railway — production and test environments, never local, see Workflow section)
- Flyway for schema migrations (`src/main/resources/db/migration`)
- Maven (always use the wrapper `mvnw` / `mvnw.cmd`, never a global Maven install)
- Lombok, MapStruct

## Architecture
- Controller → Service → Repository. No business logic in the controller or the repository.
- DTOs for everything that crosses the controller boundary. Never expose JPA entities directly.
- MapStruct for entity ↔ DTO mapping. Lombok to reduce boilerplate (`@Getter`, `@Builder`, etc.) — avoid overusing `@Data` on JPA entities due to known `equals`/`hashCode`/`toString` issues with Hibernate proxies.
- Multi-tenancy: `club_id` lives only on root tables — `club`, `family_group`, `member`, `user_account`, `court` — the ones a club creates directly. Leaf tables (`payment`, `court_block`, `recurring_slot`, `reservation`) don't repeat the column; they're already scoped to the right club through `member_id` or `court_id`. Never omit the club filter when querying a root table, even while the MVP only has one active club.

## Code rules
- Everything in English: variable, method, and class names, DB columns, table names.
- Single-purpose functions, ≤20 lines, ≤3 parameters. Exceptions only when justified with a brief comment explaining why.
- No comments except justified exceptions. Code should read on its own, through descriptive names.
- Fully descriptive names, no cryptic abbreviations (`socioId` → `memberId`, not `mId`).
- Always log important errors with enough context to debug without reproducing locally (SLF4J, appropriate level, never `e.printStackTrace()`).
- Monetary amounts are always `NUMERIC`, never `FLOAT`/`DOUBLE` — precision loss on money is not acceptable.
- Avoid chaining calls (`a.getB().getC().doSomething()`, stream pipelines with several links, fluent builders spread across one expression). Break each step into a descriptive, named variable instead — the variable name documents intent where the chain would hide it.
- Always leave a blank line immediately before a `return` statement, unless it's the only statement in the method body.
- Don't pre-validate every DB uniqueness/FK constraint in the service layer just because the DB enforces it. Only add a dedicated existence check + specific exception for conflicts a user will realistically cause and needs a clear message for (e.g. `dni` already taken by a typo). Rare edge cases (e.g. linking to a `member_id` already claimed by another `user_account`) can rely on the generic `DataIntegrityViolationException` → 409 handler instead — the DB still enforces the invariant, the code just doesn't pay for checking it twice.

## Testing
- No Testcontainers and no local database in the test suite. Automated tests are unit-level: business logic tested with JUnit and Mockito, repositories and controllers tested against mocked collaborators, not a real database.
- Any behavior that depends on actual Postgres semantics (constraints, discriminator checks, cascades) is verified manually against the test environment after each deploy, not through an automated integration suite.
- One test, one reason to fail. Descriptive test names (`shouldRejectReservationWhenCourtIsBlocked`, not `test1`).
- Write quality tests for all non-trivial business logic.
- Target 95% line coverage minimum. Coverage is a floor, not the goal — tests must be robust (real assertions on behavior and edge cases), never written just to move the number; padding coverage with trivial or assertion-free tests is worse than leaving it uncovered.

## Development workflow
- **Localhost is used only to run tests.** The full API is never run locally against a persistent database.
- Two hosted environments on Railway, both Postgres-backed: **production** (deploys from `main`) and **test** (deploys from `develop`). No local Postgres, ever.
- The API is always tested remotely against the test environment — never `localhost:8080` as a manual testing environment.
- Schema changes are versioned Flyway migrations under `src/main/resources/db/migration` (`V1__..sql`, `V2__..sql`, ...), applied automatically on boot against whichever environment's database the running instance points to. Migrations are immutable once applied to any environment — fix a wrong one forward with a new migration, never edit or delete an applied one.
- Before deploying: run the full unit test suite locally (`mvnw clean verify`).
- After finishing any task, the next step is always a self quality review of everything just changed, looking for improvements (bugs, missed edge cases, simplification, consistency with existing patterns) — not just confirming the build and tests pass.

## Domain context
Neighborhood club management system, built for multi-club from day one. Nine core tables: `club`, `family_group`, `member`, `user_account`, `payment`, `court`, `court_block`, `recurring_slot`, `reservation`.

Feature rollout: `club.courts_enabled` and `club.member_app_enabled` are boolean columns that gate the courts/booking stage and the member self-service app per club, without separate releases or redeploys. Code for a stage can be built and merged ahead of time behind its flag.

Roles: `user_account.role` is one of `SUPER_ADMIN`, `ADMIN`, `MEMBER`.
- `SUPER_ADMIN` represents the club itself — exactly one per club, seeded directly in the database when the club is created, `member_id` always null (`CHECK (role <> 'SUPER_ADMIN' OR member_id IS NULL)`, since it isn't a physical person). Only a `SUPER_ADMIN` can create `ADMIN` accounts for its own club; an `ADMIN` cannot create another `ADMIN` or a `SUPER_ADMIN`.
- `ADMIN` may optionally carry a `member_id` if the admin is also a club member — same account, same session, for both admin actions and their own payments/bookings.
- `dni` (not `national_id`) is the deliberate, team-chosen name for the national-ID field on `user_account`/`member` — "DNI" is the term everyone on this project actually uses, so it stays untranslated even though most other columns use full English names.

Design decisions already validated — do not reopen without strong justification:
- `user_account.member_id` nullable + `role` column: models an admin who is also a member without an extra table. The role is never derived from whether `member_id` is null — an admin-member needs `role='ADMIN'` and a `member_id` at the same time, which a derived role would make impossible.
- `family_group` + `payment.paid_by_member_id`: lets any family member pay another member's fee. A shared family-wide fee is explicitly out of scope; a future discount is just a smaller `amount`, not a schema change.
- `recurring_slot` unifies recurring member slots and club activities (classes) via a nullable `member_id` + `description`. Discriminator: `reservation.source IN ('MEMBER','RECURRING','ACTIVITY')` — intentional Single Table Inheritance, not Class Table Inheritance (the added complexity isn't justified for this team size).
- Postgres `EXCLUDE USING gist` on `reservation` is the single source of truth for booking non-overlap, enforced at the database engine level, not only in application code. `court` deliberately has no "slot duration" column — it doesn't participate in any real validation.
- No scheduled jobs: `recurring_slot` generation is atomic and one-shot, bounded by `valid_until` (maximum one year) — every occurrence is inserted in the same transaction that creates the slot, so a conflict is caught immediately instead of discovered later by a background job.
- Multi-row operations that must be all-or-nothing (paying several months or several family members in one submission; generating every occurrence of a `recurring_slot`) run inside a single transaction.
- `court_block` is exclusively for non-recurring events (maintenance, one-off events); anything recurring — member slots and club activities alike — lives in `recurring_slot` instead.
- Login is DNI + password only, no external identity provider. `dni` is unique per club, not globally, so login must also ask which club the account belongs to. Password reset is a manual action by an `ADMIN` or `SUPER_ADMIN`, never a self-service email flow.
- Delinquency status is never a stored column — it's computed by comparing `payment.period_covered` against the current date, so it can't drift out of sync.
- `club` and the `club_id` FKs are kept even though the MVP has a single club: retrofitting multi-tenancy onto live production data is far more expensive than building it in from day one.
- Deactivating a `user_account` ("dar de baja" an admin) is modeled as `active` (boolean, default `true`), never a row deletion — it preserves history and is reversible. Login must exclude inactive accounts (rejected the same way as wrong credentials, so an attacker can't distinguish the two cases).

NULL semantics: a nullable column here is never "missing data" — it's part of the record's meaning.
- **Simple optional** (e.g. `member.phone`, `court.type`, `payment.paid_by_member_id`): free-standing, doesn't affect other columns.
- **Exclusive pair**: a `CHECK` constraint requires at least one of two columns to be set (e.g. `recurring_slot.member_id` ⟺ `description`; `reservation.member_id` ⟺ `source = 'ACTIVITY'`). NULL here is one of two valid variants of the row, not an absence. The DB `CHECK` is the last line of defense — the real validation, with a user-facing error message, belongs in the Service layer.
