# club-management-api

Backend REST API for a neighborhood sports club management system: members, family
groups, payments, courts, recurring slots, activities, and bookings.

## Tech Stack

- **Java** (latest LTS) + **Spring Boot** (latest stable)
- **PostgreSQL**
- **Lombok**, **MapStruct**
- Hosted on **Render**

## Architecture

Standard layered architecture: `controller` → `service` → `repository`, with `dto`
and `mapper` (MapStruct) layers decoupling the API contract from persistence
entities.

```
src/main/java/.../
  controller/
  service/
  repository/
  entity/
  dto/
  mapper/
  config/
  exception/
```

## Core Domain (current design)

Nine tables: `club`, `family_group`, `member`, `user_account`, `payment`, `court`,
`court_block`, `recurring_slot`, `reservation`. Built multi-club from day one, even
though only one club runs in production today.

- `club_id` lives only on root tables (`club`, `family_group`, `member`,
  `user_account`, `court`); leaf tables are scoped indirectly through
  `member_id`/`court_id`.
- Feature rollout is per club: `club.courts_enabled` and
  `club.member_app_enabled` gate the courts and member-app stages without a
  separate release or redeploy.
- Three roles on `user_account.role`: `SUPER_ADMIN` (represents the club
  itself, one per club, seeded directly in the DB, never has a `member_id`),
  `ADMIN` (optionally also a member, via a nullable `member_id`), and
  `MEMBER`. Only a `SUPER_ADMIN` can create `ADMIN` accounts.
- `recurring_slot` unifies recurring member slots and club activities (Single
  Table Inheritance, discriminated by `reservation.source IN ('MEMBER','RECURRING','ACTIVITY')`).
- Booking non-overlap is enforced by a Postgres `EXCLUDE USING gist`
  constraint on `reservation`, not just application code.
- No scheduled jobs: slot/reservation generation is atomic and one-shot, bounded
  by a mandatory `valid_until` (max one year).

## Instructions for AI Assistants (Claude Code, Copilot, etc.)

**[CLAUDE.md](CLAUDE.md) is the authoritative, actively maintained source** for
coding rules, architecture, testing standards, and validated domain decisions
in this repository. It is not duplicated here on purpose, to avoid the two
files drifting out of sync — read it before making changes.

## Getting Started

Localhost is used only to run tests — the API is never run locally against a
persistent database. The PostgreSQL database lives only on Render, and the
API is deployed and tested there.

```bash
./mvnw clean verify
```

Runtime configuration (see `application.properties` / Render dashboard):

- `DATABASE_URL`
- `SPRING_PROFILES_ACTIVE`
