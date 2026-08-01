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
`court_block`, `recurring_slot`, `booking`.

- Multi-tenant via `club_id` on all tenant-scoped tables.
- `user_account.member_id` is nullable: an admin may or may not also be a member.
- `recurring_slot` unifies recurring member slots and club activities (Single
  Table Inheritance, discriminated by `booking.source IN ('MEMBER','RECURRING','ACTIVITY')`).
- No scheduled jobs: slot/booking generation is atomic and one-shot, bounded
  by a mandatory `valid_until` (max one year).

## Instructions for AI Assistants (Claude Code, Copilot, etc.)

When writing or modifying code in this repository, follow these rules strictly:

1. **Act as a senior backend developer (20+ years of experience).** Code must be
   simple, high-quality, and scalable — never clever for the sake of being clever.
2. **Functions are single-purpose.** Max ~20 lines and max 3 parameters, unless
   there is a clearly justified exception (explain it if you make one).
3. **Everything in English.** Variable names, method names, DB columns, table
   names, DTOs — no Spanish anywhere in code, even though product/domain
   discussions happen in Spanish.
4. **No comments**, except to justify a genuinely non-obvious decision (e.g. a
   workaround for a library bug). The code itself must be self-explanatory.
5. **Code should read like a story.** Use full, descriptive names for
   variables, methods, and classes. Prefer clarity over brevity.
6. **Always write tests** for new logic where feasible — meaningful tests that
   would catch a real regression, not tests for the sake of coverage.
7. **Layering is mandatory:** controller → service → repository. Controllers
   only orchestrate; business logic lives in services; DB access only in
   repositories. Use DTOs at the controller boundary, MapStruct for mapping,
   Lombok to remove boilerplate.
8. **Always use the latest stable versions** of dependencies in `pom.xml`
   unless there's a compatibility reason not to — state that reason if so.
9. **Log important errors clearly**, with enough context to debug in
   production (never swallow exceptions silently).
10. Before implementing, if a request is ambiguous or introduces meaningful
    complexity (e.g. a new scheduled job, a new nullable FK, a new table),
    surface the trade-off instead of silently picking one — this team
    validates design before writing code.

## Getting Started

```bash
./mvnw spring-boot:run
```

Environment variables (see `application.yml` / Render dashboard):

- `DATABASE_URL`
- `SPRING_PROFILES_ACTIVE`
