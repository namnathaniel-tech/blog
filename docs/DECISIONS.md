# Decision log

These were decided with the owner. **Do not reverse them silently.** If a task seems to
require changing one, stop and ask the owner; if they agree, add a new entry that
supersedes the old one (never edit history).

Format: decision · why · rejected alternatives.

---

### D-001 React (Vite SPA) + Spring Boot, one deployable
- **Why:** The blog is also a portfolio piece showing full-stack Java + React. It needs a real
  backend (rich editor, image upload, auth, comments, likes, search).
- Spring Boot serves the built SPA from `classpath:static/` → one Docker image, one service,
  same-origin cookies, no CORS.
- **Rejected:** Static site generator (no admin editor/comments); Next.js + Spring (two servers,
  overlapping responsibilities); separate frontend host (CORS + cross-site cookies).

### D-002 SEO via server-side meta injection, not SSR
- `SpaIndexController` returns `index.html` with per-route `<title>`, description, canonical,
  Open Graph + Twitter tags between the `<!-- SEO:START -->`/`<!-- SEO:END -->` markers.
  Content itself is still client-rendered.
- **Why:** Link previews (LinkedIn/Discord/X) don't run JS. Google does. This gets 95% of SSR's value
  at ~5% of the cost.
- Also required: `sitemap.xml`, `rss.xml`, `robots.txt` served by Spring.
- Unknown post slugs return **HTTP 404** (with the SPA body), not 200.

### D-003 Stack versions
- Java 25 (LTS), Spring Boot 4.1.x, Maven wrapper. Postgres 17 (Neon in prod).
- React 19, Vite 8, TypeScript 7, React Router 8 (data router, `react-router` package),
  TanStack Query 5, Tailwind 4, shadcn/ui (radix-nova preset), Vitest.
- Do not add a state-management library (Redux/Zustand). Server state = TanStack Query; UI state = React.

### D-004 Single admin, email OTP login, no passwords
- Exactly one admin: whoever controls `ADMIN_EMAIL`. No users table, no signup, no password.
- Flow: request code → 6-digit code emailed → verify → session cookie.
- Implemented as a **small custom `AuthController` + `OtpService`** that programmatically
  establishes a Spring Security `SecurityContext` in the HTTP session.
  - Rejected Spring Security's built-in One-Time-Token login: it's built around magic links/
    form login, and bending it to a JSON 6-digit-code API is more obscure than ~150 lines of
    explicit, well-tested code.
- Non-admin emails get the **identical** response (no account enumeration); email is sent async
  so timing doesn't leak either.
- Email delivery: **Resend via SMTP** (owner has a domain). Dev uses **Mailpit** (compose).
- **Backup: 10 single-use recovery codes**, generated from the admin settings page, shown once,
  stored hashed. Regenerating invalidates the old set.

### D-005 Sessions, not JWT; persisted in Postgres
- HttpOnly, Secure, SameSite=Lax session cookie, 30-day timeout. Session id rotated on login.
- **Spring Session JDBC** so redeploys don't log the admin out.
- CSRF: Spring Security `csrf.spa()` (XSRF-TOKEN cookie → `X-XSRF-TOKEN` header). Already wired in
  `frontend/src/api/client.ts`.

### D-006 Readers are anonymous guests with server-assigned IDs
- ID format: `Guest` + 10 random digits (first digit non-zero), e.g. `Guest2819392104`.
- Stored in a **signed** cookie `bg_guest` = `<id>.<base64url(HMAC-SHA256(GUEST_COOKIE_SECRET, id))>`,
  HttpOnly, Secure, SameSite=Lax, 2-year max-age. Tampered/unsigned cookies are ignored and replaced.
- Cookie issued on first API call; the DB row (`guest`) is created **lazily** on the first write
  (like/comment/nickname) so bots don't fill the table.
- Clearing cookies / new device ⇒ new identity. Accepted trade-off.
- No analytics/tracking cookies anywhere (keeps us out of consent-banner territory).

### D-007 Optional guest nicknames, impersonation-resistant
- Guest may set a nickname; displayed as **`Nickname · #2104`** (last 4 digits of the guest ID).
  No nickname ⇒ display the full guest ID.
- Allowed chars: ASCII letters, digits, space, `_ . -`; 2–24 chars; trimmed; collapsed spaces.
  ASCII-only deliberately eliminates homoglyph impersonation (e.g. Cyrillic "а").
- Rejected if, after lowercasing and removing non-alphanumerics, it contains a reserved term:
  `admin, administrator, author, owner, moderator, mod, staff, official, system`, the site name,
  and every entry in `BLOG_RESERVED_NAMES` (owner's name goes here).
- Basic profanity list (`backend/src/main/resources/moderation/blocked-words.txt`).
- Only the admin gets the **Author** badge; badge is derived from `comment.is_author`, never from a name.

### D-008 Comments: plain text, auto-publish, delete-after
- Comments publish immediately. Admin can delete any comment, ban a guest ID or IP hash, and
  close comments per post.
- **Plain text only** (stored raw, rendered by React → auto-escaped). Client linkifies URLs with
  `rel="nofollow ugc noopener noreferrer"`. Max 2000 chars; more than 2 links ⇒ rejected.
- Anti-spam: honeypot field, rate limits (see ARCHITECTURE §Rate limits), bans.
- Guests may edit their own comment for 15 minutes and delete it any time (soft delete).
- Admin comments are posted as the author (`is_author=true`, display `BLOG_AUTHOR_NAME`).
- Threading: **one level of replies** (reply to a top-level comment only).

### D-009 Rich text editor: TipTap; store JSON + sanitized HTML
- Editor stores TipTap **JSON** (source of truth for re-editing) and the client sends
  `generateHTML()` output too. Server **sanitizes the HTML with jsoup** using an allowlist that
  matches the enabled TipTap extensions, and derives `content_text` (plain text for search/excerpt).
- Public pages render the server-sanitized HTML. Never render unsanitized HTML anywhere.
- Extensions: StarterKit (headings 2–4, lists, blockquote, code, hr), Link, Image, CodeBlockLowlight,
  Placeholder, Youtube (youtube-nocookie only). Nothing else without an owner decision.
- Rejected: Markdown-only editor (owner wants WYSIWYG with images); OWASP sanitizer (jsoup does
  sanitizing + text extraction in one dependency).

### D-010 Images: storage abstraction, R2 in prod, metadata stripped
- `MediaStorage` interface; `LocalDiskStorage` (dev, serves `/media/**` from `./data/media`) and
  `R2Storage` (prod, S3-compatible via AWS SDK v2, public via custom domain `R2_PUBLIC_BASE_URL`).
- Upload validation by **magic bytes** (not extension/Content-Type): JPEG, PNG, WebP, GIF. Max 10 MB.
- **Strip EXIF/metadata** (phone photos carry GPS location) by decoding & re-encoding; downscale to
  max 2400px on the long edge. Animated GIFs are stored as-is (size-capped) — document this limitation.
- Storage keys are random (`yyyy/MM/<uuid>.<ext>`), never user filenames.

### D-011 Search: Postgres full-text search
- Generated `tsvector` column (title weight A, excerpt B, body C), GIN index, `websearch_to_tsquery`,
  `ts_rank`, `ts_headline` for snippets. Rejected: Elasticsearch/Meilisearch (overkill).

### D-012 Views and likes without accounts
- Likes: one per guest per post (PK `(post_id, guest_id)`), toggle on/off.
- Views: counted when the SPA calls `POST /api/public/posts/{slug}/views` (bots without JS don't
  count); deduped per post per viewer per UTC day using `sha256(IP_HASH_SALT + guest id or IP+UA)`.
  Denormalized counters on `post`.
- Raw IPs are never stored — only salted hashes.

### D-013 Hosting
- App: **Railway** (Docker image from repo, ~$5/mo). Render is an acceptable equivalent.
- DB: **Neon** Postgres (free tier). Images: **Cloudflare R2** (free tier, zero egress).
- Email: **Resend** SMTP. Domain: owner's own; DNS on Cloudflare recommended.
- App must stay 12-factor: all config via env vars, no local disk state in prod.
- Single instance assumed (in-memory rate limiter). If ever scaled out, move rate limits to Postgres.

### D-014 Local dev uses Docker (OrbStack)
- `compose.yaml` (Postgres + Mailpit) auto-started by Spring Boot's docker-compose support.
- Integration tests use **Testcontainers** with real Postgres. **No H2** — FTS, jsonb and generated
  columns are Postgres-specific.

### D-015 API conventions
- JSON under `/api`. Public: `/api/public/**`, auth: `/api/auth/**`, guest actions:
  `/api/guest/**` and public endpoints, admin: `/api/admin/**` (ROLE_ADMIN).
- Errors: RFC 9457 `ProblemDetail` (+ `errors` map for validation). Never return stack traces.
- DTOs are Java `record`s; never expose JPA entities from controllers.
- Pagination: `?page=0&size=10` → `{ items, page, size, totalItems, totalPages }`.
- Timestamps: ISO-8601 UTC (`Instant`).
