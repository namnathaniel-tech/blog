# Implementation plan

Work **one phase at a time, in order**. Each phase is sized for one focused session.
A phase is done only when every acceptance criterion is met **and** the verification
commands pass. Then tick the box, commit, and stop for owner review.

## Status

- [x] **Phase 0** — Skeleton (done by Opus during design)
- [x] **Phase 1** — Admin auth: email OTP + recovery codes + sessions  ⚠️ owner review + `/security-review`
- [ ] **Phase 2** — Media upload + storage
- [ ] **Phase 3** — Posts: model, admin CRUD, TipTap editor, sanitization
- [ ] **Phase 4** — Public reading: post list/page, SEO tags, sitemap, RSS, theme
- [ ] **Phase 5** — First deploy (Railway + Neon + R2 + Resend + domain)  👤 owner does account setup
- [ ] **Phase 6** — Projects + tags
- [ ] **Phase 7** — Search
- [ ] **Phase 8** — Guests, nicknames, likes, views
- [ ] **Phase 9** — Comments, moderation, bans  ⚠️ `/security-review`
- [ ] **Phase 10** — Polish: a11y pass, performance, error pages, README

## Standard verification (run at the end of every phase)

```bash
cd backend && ./mvnw verify                  # all tests incl. Testcontainers (Docker running)
cd frontend && npm run typecheck && npm run lint && npm test && npm run build
docker build -t blog .                        # from repo root (from Phase 4 on)
```
Plus the phase-specific manual check. If anything fails, the phase is not done.

---

## Phase 0 — Skeleton ✅

Delivered: Spring Boot 4.1.2/Java 25 (Maven), Vite/React 19/TS/Tailwind 4/shadcn, React Router,
TanStack Query, `api()` client with CSRF + ProblemDetail errors, `SecurityConfig` (admin-only
`/api/admin/**`, SPA CSRF, CSP), `GlobalExceptionHandler`, `BlogProperties`, `GET /api/public/meta`,
`SpaIndexController` + `SeoRenderer` (SEO marker injection), Flyway V1, compose (Postgres 17 +
Mailpit), Dockerfile, CI. Tests: `SeoRendererTest`, `MetaControllerTest`, `BackendApplicationTests`
(Testcontainers), `client.test.ts`.

Verified 2026-11-05 with OrbStack: `./mvnw verify` (8 tests, incl. Testcontainers) ✅,
frontend typecheck/lint/test/build ✅, `docker build` + smoke test (SPA served with SEO tags,
`/api/public/meta` responds) ✅.

---

## Phase 1 — Admin auth

**Goal:** Owner can log in with an emailed code or a recovery code; `/api/admin/**` works.

Backend
1. Add deps: `spring-session-jdbc`, `bucket4j_jdk17-core` (or current Bucket4j artifact) — check latest versions.
2. `V2__auth.sql`: `admin_login_code`, `recovery_code`, Spring Session JDBC tables (copy
   Postgres schema from the spring-session-jdbc jar: `org/springframework/session/jdbc/schema-postgresql.sql`).
   Set `spring.session.jdbc.initialize-schema: never`.
3. `common/Hashing` (ipHash), `config/RateLimiter` (small wrapper around Bucket4j keyed buckets).
4. `auth/OtpService`, `auth/RecoveryCodeService`, `auth/AdminMailer` (@Async, `JavaMailSender`),
   `auth/AuthController` exactly per ARCHITECTURE §OTP login. Enable `@EnableAsync`.
5. `BlogProperties`: add `authorName`.
6. Logout: `POST /api/auth/logout` invalidates session, clears context.
7. `GET /api/admin/recovery-codes` → `{remaining}`; `POST` → `{codes: [...]}` (replaces all).

Frontend
8. `api/auth.ts` hooks. Pages: `pages/admin/LoginPage.tsx` (email step → code step →
   "use a recovery code"), `pages/admin/AdminLayout.tsx` (guard: if `meta.admin` false →
   redirect to `/admin/login`; header with logout), `pages/admin/DashboardPage.tsx` (placeholder),
   `pages/admin/SettingsPage.tsx` (recovery codes). Add shadcn components as needed
   (`npx shadcn@latest add input-otp label alert dialog sonner`).
9. Add routes to `router.tsx` **and** `SpaIndexController` (`/admin/**` already mapped).

Acceptance criteria (each needs a test unless marked manual)
- [x] Request for admin email stores exactly one active hashed code and sends one mail.
- [x] Request for any other email returns the identical 202 body and sends no mail.
- [x] Correct code → 204, session cookie set, `GET /api/public/meta` reports `admin: true`.
- [x] Wrong code 5× → code dead; correct code afterwards fails.
- [x] Expired code fails; consumed code can't be reused; requesting a new code kills the old one.
- [x] Recovery code logs in once; same code fails the second time; regenerate invalidates old set.
- [x] `/api/admin/**` → 401 anonymous, 200 for admin. POST without CSRF → 403.
- [x] 4th OTP request within 15 min from one IP → 429.
- [x] Session survives backend restart (Spring Session JDBC) — integration test or manual.
- [x] Manual: full login in browser with Mailpit (`http://localhost:8025`).

Then: owner reviews; run `/security-review`.

**Done 2026-11-05 (Opus).** Notes for later phases:
- Spring Session ignores `server.servlet.session.*`: timeout is `spring.session.timeout`, and the cookie
  is configured in `config/SessionCookieConfig` (HttpOnly, SameSite=Lax, Secure per profile, 30-day Max-Age).
  Both are asserted in `AuthFlowIntegrationTest`.
- Mail health indicator disabled so SMTP outages don't fail Railway health checks.
- `IP_HASH_SALT` and `BLOG_AUTHOR_NAME` were needed now (pulled forward from Phase 8).
- Rate limits live in `common/Limits`; `RateLimiter.reset()` exists for tests.
- Compose Postgres is on host port **55432** (owner has Postgres.app on 5432).
- Dev admin email defaults to `admin@example.com`; codes land in Mailpit (http://localhost:8025).

---

## Phase 2 — Media

**Goal:** Admin can upload images that are validated, stripped of metadata, stored, and served.

1. Deps: `com.twelvemonkeys.imageio:imageio-webp` (+ `imageio-jpeg`) for reading WebP/robust JPEG;
   `software.amazon.awssdk:s3` for R2. Check latest versions.
2. `V3__media.sql`.
3. `media/MediaStorage` interface: `put(key, bytes, contentType)`, `delete(key)`, `publicUrl(key)`.
   `LocalDiskStorage` (root `./data/media`, served at `/media/**` via a `ResourceHandler`, dev
   only) and `R2Storage` (S3 client, endpoint `https://<account>.r2.cloudflarestorage.com`,
   region `auto`, path-style). Choose with `STORAGE_DRIVER` via `@ConditionalOnProperty`.
4. `media/ImageProcessor`: detect type by magic bytes; reject others (415); decode → downscale
   (long edge ≤ 2400) → re-encode (JPEG q=0.85 / PNG; WebP input re-encoded as JPEG unless
   alpha → PNG); GIF stored as-is. Returns bytes, width, height, contentType.
5. `POST /api/admin/media` (multipart `file`, `alt`), `GET /api/admin/media?page`,
   `PATCH /api/admin/media/{id}` (alt), `DELETE /api/admin/media/{id}` (409 if referenced — wire
   reference check in Phase 3).
6. Frontend: `api/media.ts` with upload progress (use `XMLHttpRequest` inside `api/` for progress
   only — still include CSRF header), simple `/admin/media` library page.

Acceptance
- [ ] JPEG with EXIF GPS uploaded → stored file has **no** EXIF (test with a fixture image).
- [ ] 5000px image → stored ≤ 2400px long edge, dimensions recorded.
- [ ] `.png`-named text file / SVG / PDF → 415. 11 MB → 413 ProblemDetail.
- [ ] Storage key is random; original filename not used.
- [ ] Non-admin upload → 401.
- [ ] Manual: upload in browser, image visible at returned URL.

---

## Phase 3 — Posts & editor

**Goal:** Admin can write, autosave, preview, publish, unpublish and delete rich posts.

Backend
1. Add `org.jsoup:jsoup`. `V4__posts.sql`.
2. `post/HtmlSanitizer` per ARCHITECTURE §HTML sanitization + text extraction + reading time
   (words / 200, min 1). **Write the XSS test suite first.**
3. Slug util: lowercase, ASCII-fold, `[a-z0-9-]`, collapse dashes, max 80; uniqueness check → 409.
4. `AdminPostController`: list (all statuses), get, create, update (`contentJson`, `contentHtml`,
   title, slug, excerpt, coverMediaId, commentsEnabled), publish, unpublish, delete.
   Server recomputes `content_html` (sanitized), `content_text`, excerpt fallback, reading time.
5. Media delete returns 409 when referenced as a cover or in any post HTML (search `content_html`
   for the media URL).

Frontend
6. `npm i @tiptap/react @tiptap/pm @tiptap/starter-kit @tiptap/extension-link @tiptap/extension-image
   @tiptap/extension-code-block-lowlight @tiptap/extension-placeholder @tiptap/extension-youtube lowlight`
   (match versions across all @tiptap packages).
7. `components/editor/`: `PostEditor.tsx`, `Toolbar.tsx`, `useImageUpload.ts` (button, paste,
   drop → upload → insert with alt prompt), `extensions.ts` (single source of the extension list —
   must match the sanitizer allowlist).
8. Pages: `/admin/posts` (table), `/admin/posts/new`, `/admin/posts/:id/edit` (editor + side
   panel + autosave + unsaved-changes guard + preview dialog).

Acceptance
- [ ] XSS suite passes (script tags, event handlers, `javascript:`/`data:` URLs, foreign iframes,
      foreign image hosts, style attributes — all stripped).
- [ ] Round trip: create → edit → reload shows identical editor content.
- [ ] Publish sets `published_at` once; unpublish → DRAFT; re-publish keeps original date.
- [ ] Duplicate slug → 409 with a field error shown under the slug input.
- [ ] Autosave fires after edits (Vitest with fake timers) and the indicator updates.
- [ ] Manual: write a post with headings, code block, 2 images (one pasted), a YouTube embed.

---

## Phase 4 — Public reading & SEO

**Goal:** Visitors can read published posts; previews and crawlers get proper metadata.

1. `PublicPostController`: list (published, paginated), get by slug (404 for drafts unless admin).
2. `SpaIndexController`: `/posts/{slug}` → real `SeoTags` (title, excerpt, canonical, cover image,
   `article`); missing/draft → **404 status** with SPA body. Add `/tags/**`, `/projects/**` mappings.
3. `SitemapController` (`/sitemap.xml`: home, posts, projects), `RssController` (`/rss.xml`, RSS 2.0,
   20 latest, full sanitized HTML in `content:encoded`), `robots.txt` (disallow `/admin`, `/api`).
4. Frontend: `HomePage` (post cards, pagination via `?page=`), `PostPage` (prose styling with
   `@tailwindcss/typography`, highlight.js theme for light/dark, `loading="lazy"` images),
   dark-mode toggle (class strategy, persisted in localStorage), header search box (wired in P7).
5. Ensure the real `frontend/dist/index.html` still contains the SEO markers after build (test in CI:
   a tiny script or a Vitest test that reads `dist/index.html`).

Acceptance
- [ ] `curl -s localhost:8080/posts/<slug>` (prod image) contains the post's `og:title`/`og:image`.
- [ ] Draft slug → 404 for anonymous, 200 + banner for admin.
- [ ] sitemap/rss validate (well-formed XML; test parses them).
- [ ] Lighthouse (manual) on post page: Performance ≥ 90, Accessibility ≥ 95, SEO ≥ 95.

---

## Phase 5 — First deploy 👤

Owner creates accounts; agent writes the runbook `docs/DEPLOY.md` and fixes whatever breaks.

1. Neon project (Postgres 17) → JDBC URL with `sslmode=require`.
2. Cloudflare R2 bucket + API token + public custom domain `media.<domain>`; set CSP `img-src`
   to `'self' https://media.<domain>`.
3. Resend: add domain, add DNS records (SPF, DKIM), create SMTP key. Verify domain.
4. Railway: new service from GitHub repo (Dockerfile), env vars from `.env.example`, health check
   `/actuator/health/readiness`, custom domain `<domain>` (or `blog.<domain>`).
5. Smoke test: login via email, publish a post with an image, share link in Discord → preview shows.

Acceptance: owner logs in on production and publishes a post; link preview works.

---

## Phase 6 — Projects & tags
Per SPEC F4/F5. `V5__projects_tags.sql` (+ `post.project_id`). Admin CRUD pages, tag inline
creation in post editor (combobox), public `/projects`, `/projects/:slug`, `/tags/:slug`,
SEO tags for both. Acceptance: CRUD tests; deleting a project with posts → 409 (or detach — ask owner);
deleting a tag removes `post_tag` rows; public pages only show published posts.

## Phase 7 — Search
Per D-011 / SPEC F6. `V6__search.sql`. `GET /api/public/search`. Frontend `/search` with debounced
input synced to `?q=`. Acceptance (Testcontainers): title match ranks above body match; stemming
("building" finds "build"); `ts_headline` snippet contains `<mark>` (sanitize! only `<mark>` allowed);
drafts never returned; tag/project filters work.

## Phase 8 — Guests, likes, views
Per D-006/D-007/D-012, SPEC F7/F8. `V7__guests_engagement.sql`. `GuestCookieFilter`, `CurrentGuest`,
`NicknamePolicy` (unit-test heavily: reserved names incl. `"Ad min"`, `"ADMIN_1"`, owner name with
dots, profanity, length, charset), `/api/guest/me`, like toggle, views endpoint.
Acceptance: tampered cookie → new identity; like twice → still 1; unlike → 0; same viewer same day →
1 view; different day → 2; nickname display `Nick · #2104`; reserved/profane names rejected with
specific messages.

## Phase 9 — Comments & moderation
Per D-008, SPEC F9. `V8__comments.sql`. Acceptance: honeypot filled → 201-looking response but not
stored (don't tip off bots); >2 links → 400; 4th comment in a minute → 429; banned guest/IP → 403;
edit after 15 min → 403; delete own → soft delete; can't edit/delete others' (403); author comment
gets badge; closed post rejects new comments (403) but lists existing; replies only one level deep
(reply to a reply → 400). Run `/security-review`.

## Phase 10 — Polish
Error boundary + friendly 404/500 pages; loading skeletons; empty states; a11y pass (axe in
Vitest for key components); image `width/height` everywhere; `Cache-Control` on public GETs;
repo `README.md` with screenshots + architecture summary (it's a portfolio piece).
