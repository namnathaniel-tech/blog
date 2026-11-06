# Project Blog — agent guide

Personal blog: **one admin author** (email-OTP login) writes rich posts with images about their
projects; **anonymous guests** (`Guest##########`, optional nickname) read, search, like, comment.
React SPA + Spring Boot, shipped as one Docker image.

## Read before doing anything
1. `docs/PLAN.md` — find the first unchecked phase. That is your task. Do only that phase.
2. `docs/SPEC.md` — what the feature must do.
3. `docs/ARCHITECTURE.md` — data model, API shapes, security details.
4. `docs/DECISIONS.md` — settled decisions. **Do not contradict them.** If one blocks you, stop
   and ask the owner.

## Commands
```bash
# Backend (needs Docker running — OrbStack — for compose + Testcontainers)
cd backend
./mvnw spring-boot:run                # :8080, auto-starts Postgres + Mailpit via compose.yaml
./mvnw verify                         # all tests
./mvnw test -Dtest=SomeTest           # one test class
# Mail UI in dev: http://localhost:8025
# OrbStack: Testcontainers finds Docker via ~/.testcontainers.properties
#   (docker.host=unix:///Users/hooray/.orbstack/run/docker.sock) - already set on this machine.

# Frontend
cd frontend
npm run dev                           # :5173, proxies /api and /media to :8080
npm run typecheck && npm run lint && npm test && npm run build
npx shadcn@latest add <component>     # add UI primitives (don't hand-write them)

# Whole app as production image
docker build -t blog .
```

## Workflow per phase
1. Run the standard verification first to confirm a green baseline.
2. Plan the phase (list files you'll touch) before editing.
3. Implement in small steps; write tests alongside (security & sanitizer tests **first**).
4. Run the standard verification (PLAN.md). All green, every acceptance box satisfied.
5. Tick the phase in `docs/PLAN.md`, update docs if you made a (owner-approved) change,
   commit `feat(phase-N): <summary>`, then **stop** and summarize for the owner:
   what was built, how to try it manually, anything deferred or uncertain.

If the spec is ambiguous: choose the simplest option consistent with DECISIONS.md, note it in
your summary, and add it to DECISIONS.md only after the owner agrees.

## Conventions

**Backend (`backend/src/main/java/dev/blog/`)**
- Package-by-feature (`auth/`, `post/`, `media/` …). Entity, repository, service, controller,
  DTOs live together. Cross-cutting stuff in `config/`, `common/`, `web/`.
- Controllers thin; logic in `@Service`; `@Transactional` on service methods.
- DTOs are `record`s. Never return JPA entities from controllers. Validate input with
  `jakarta.validation` annotations + `@Valid`.
- Errors: throw `NotFoundException` / `ResponseStatusException`; `GlobalExceptionHandler` turns
  them into ProblemDetail. No try/catch that swallows errors.
- Schema changes **only** through new Flyway migrations `V{n}__{desc}.sql`. Never edit a
  committed migration. `ddl-auto` stays `validate`.
- New config → add to `BlogProperties` + `application.yaml` (with a dev default) + `.env.example`
  + ARCHITECTURE §Configuration. Never `@Value`, never hard-code secrets.
- Spring Boot 4 notes: test slice annotations moved, e.g.
  `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`; Jackson is 3.x (`tools.jackson`);
  use `@MockitoBean` (not `@MockBean`). Spring Session JDBC ignores `server.servlet.session.*`
  (use `spring.session.timeout`; cookie flags live in `config/SessionCookieConfig`).
- Integration tests: `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)`;
  mock `JavaMailSender` with `@MockitoBean`; call `RateLimiter.reset()` in `@BeforeEach`.
  See `auth/AuthFlowIntegrationTest` as the template.

**Frontend (`frontend/src/`)**
- All HTTP goes through `api()` in `api/client.ts`. One file per resource in `api/` exporting
  types + TanStack Query hooks + a `xxxKeys` query-key factory. TS types mirror Java DTO records —
  change both together.
- Pages in `pages/` (admin under `pages/admin/`); feature components in `components/<feature>/`;
  shadcn primitives in `components/ui/` (generated).
- Tailwind utility classes + shadcn tokens (`text-muted-foreground`, `bg-card` …). No CSS files
  besides `index.css`. Use `cn()` from `lib/utils`.
- New public route ⇒ add to `router.tsx` **and** `SpaIndexController` (backend), else a hard refresh 404s.
- Never use `dangerouslySetInnerHTML` except for server-sanitized post/project HTML and search
  `ts_headline` snippets — in a single component `components/post/SanitizedHtml.tsx`.

## Security rules (non-negotiable)
- `/api/admin/**` is admin-only; every new endpoint gets a WebMvc test for anonymous (401/403)
  and allowed access.
- All rich HTML passes through `HtmlSanitizer` server-side before storage. Comments are plain text.
- Never store raw IPs (use `Hashing.ipHash`). Never log OTP codes, recovery codes, cookies, or emails
  other than the configured admin's at DEBUG.
- Auth responses must not reveal whether an email is the admin's.
- Don't weaken CSP, CSRF, or cookie flags to make something work — fix the cause.

## Don't
- Add dependencies not named in the plan without saying why in your summary.
- Use H2, Lombok, Redux/Zustand, axios, MUI, or a different router.
- Reorganize folders or rename packages.
- Start the next phase without the owner's go-ahead.
