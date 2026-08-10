# CLAUDE.md — Backend (Spring Boot + PostgreSQL)

## Role
Act as a senior Java/Spring developer with 20 years of experience. Code must be simple, high-quality, and scalable. Favor clarity over cleverness.

## Stack
- Java 25 (LTS)
- Spring Boot 4.1.x, Spring Framework 7.x
- PostgreSQL (only on Render — never local, see Workflow section)
- Maven (always use the wrapper `mvnw` / `mvnw.cmd`, never a global Maven install)
- Lombok, MapStruct

## Architecture
- Controller → Service → Repository. No business logic in the controller or the repository.
- DTOs for everything that crosses the controller boundary. Never expose JPA entities directly.
- MapStruct for entity ↔ DTO mapping. Lombok to reduce boilerplate (`@Getter`, `@Builder`, etc.) — avoid overusing `@Data` on JPA entities due to known `equals`/`hashCode`/`toString` issues with Hibernate proxies.
- Multi-tenancy: every relevant entity carries `club_id`. Never omit the club filter in queries, even while the MVP only has one active club.

## Code rules
- Everything in English: variable, method, and class names, DB columns, table names.
- Single-purpose functions, ≤20 lines, ≤3 parameters. Exceptions only when justified with a brief comment explaining why.
- No comments except justified exceptions. Code should read on its own, through descriptive names.
- Fully descriptive names, no cryptic abbreviations (`socioId` → `memberId`, not `mId`).
- Always log important errors with enough context to debug without reproducing locally (SLF4J, appropriate level, never `e.printStackTrace()`).
- `pom.xml`: always keep the latest stable versions of all dependencies.

## Testing
- No Testcontainers and no local database in the test suite. Automated tests are unit-level: business logic tested with JUnit and Mockito, repositories and controllers tested against mocked collaborators, not a real database.
- Any behavior that depends on actual Postgres semantics (constraints, discriminator checks, cascades) is verified manually against the Render environment after each deploy, not through an automated integration suite.
- One test, one reason to fail. Descriptive test names (`shouldRejectReservationWhenCourtIsBlocked`, not `test1`).
- Write quality tests for all non-trivial business logic.

## Development workflow
- **Localhost is used only to run tests.** The full API is never run locally against a persistent database.
- The PostgreSQL database lives only on Render.
- The API is deployed on Render and always tested remotely — never `localhost:8080` as a manual testing environment.
- Before deploying: run the full unit test suite locally (`mvnw clean verify`).

## Domain context
Neighborhood club management system. Nine core tables: `club`, `family_group`, `member`, `user_account`, `payment`, `court`, `court_block`, `recurring_slot`, `reservation`.

Design decisions already validated — do not reopen without strong justification:
- `user_account.member_id` nullable + `role` column: models an admin who is also a member without an extra table.
- `family_group` + `payment.paid_by_member_id`: lets any family member pay another member's fee. A shared family-wide fee is explicitly out of scope.
- `recurring_slot` unifies recurring member slots and club activities (classes) via a nullable `member_id` + `description`. Discriminator: `reservation.origin IN ('MEMBER','RECURRING','ACTIVITY')` — intentional Single Table Inheritance, not Class Table Inheritance (the added complexity isn't justified for this team size).
- No scheduled jobs: reservation generation is atomic, one-shot, bounded by `valid_until` (maximum one year).
- `court_block` is exclusively for non-recurring events (maintenance, etc.).
- `club` and the `club_id` FKs are kept even though the MVP has a single club: retrofitting multi-tenancy onto live production data is far more expensive than building it in from day one.
