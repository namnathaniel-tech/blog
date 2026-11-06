# Architecture

## Overview

```
Browser ──HTTPS──▶ Railway: one container (Spring Boot 4, Java 25)
                    ├─ /assets/**, /favicon.svg ...  static SPA build (classpath:static)
                    ├─ /, /posts/{slug}, ...         SpaIndexController → index.html + SEO tags
                    ├─ /sitemap.xml /rss.xml /robots.txt
                    ├─ /media/**                     LocalDiskStorage (dev only)
                    └─ /api/**                       JSON REST
                          │
                          ├──▶ Neon Postgres 17 (data, Spring Session, Flyway)
                          ├──▶ Cloudflare R2 (images; served directly from media.<domain>)
                          └──▶ Resend SMTP (OTP emails)
```

Dev: Vite (`:5173`) proxies `/api` and `/media` to Spring (`:8080`). Postgres + Mailpit via
`backend/compose.yaml`, started automatically by Spring Boot.

## Repository layout

```
blog/
├─ CLAUDE.md                 ← start here
├─ Dockerfile                ← builds frontend, bakes into backend jar
├─ .github/workflows/ci.yml
├─ docs/  SPEC.md ARCHITECTURE.md DECISIONS.md PLAN.md
├─ backend/                  Spring Boot (Maven)
│  ├─ compose.yaml
│  └─ src/main/java/dev/blog/
│     ├─ config/      SecurityConfig, BlogProperties, (later) RateLimitConfig, StorageConfig
│     ├─ common/      GlobalExceptionHandler, NotFoundException, PageResponse, (later) Hashing
│     ├─ web/         SpaIndexController, SeoRenderer, SeoTags, (later) SitemapController, RssController
│     ├─ meta/        MetaController
│     ├─ auth/        (P1) AuthController, OtpService, RecoveryCodeService, AdminMailer
│     ├─ media/       (P2) MediaController, MediaService, MediaStorage, LocalDiskStorage, R2Storage, ImageProcessor
│     ├─ post/        (P3) Post entity/repo/service, PublicPostController, AdminPostController, HtmlSanitizer
│     ├─ project/     (P6)
│     ├─ tag/         (P6)
│     ├─ search/      (P7)
│     ├─ guest/       (P8) GuestCookieFilter, GuestService, NicknamePolicy, CurrentGuest
│     ├─ engagement/  (P8) likes + views
│     └─ comment/     (P9) comments + bans
└─ frontend/                 Vite + React
   └─ src/
      ├─ api/          client.ts (only fetch caller) + one file per resource with hooks
      ├─ components/ui shadcn (generated — edit sparingly)
      ├─ components/   app components grouped by feature (layout/, editor/, post/, comments/ …)
      ├─ pages/        route components; admin pages under pages/admin/
      ├─ lib/          utils
      └─ router.tsx
```

**Package-by-feature.** Each feature package holds its entity, repository, service, controllers,
and DTO records. Controllers are thin; business rules live in services; services are tested.

## Data model (target)

Flyway migrations in `backend/src/main/resources/db/migration`, one per phase, `V{n}__{name}.sql`.
IDs: `bigint generated always as identity` unless noted. All timestamps `timestamptz`.

```sql
-- P1 auth
admin_login_code(id, code_hash text, created_at, expires_at, attempts int default 0,
                 consumed_at null, requester_ip_hash text)
recovery_code(id, code_hash text, created_at, used_at null)
-- + Spring Session JDBC tables (copy schema-postgresql.sql from spring-session-jdbc into a migration)

-- P2 media
media(id, storage_key text unique, url text, content_type text, width int, height int,
      bytes bigint, alt text, created_at)

-- P3 posts
post(id, slug text unique, title text, excerpt text, content_json jsonb, content_html text,
     content_text text, cover_media_id fk media null, status text check (DRAFT|PUBLISHED),
     published_at null, comments_enabled bool default true, project_id fk null (added P6),
     view_count bigint default 0, like_count bigint default 0, comment_count int default 0,
     reading_minutes int, created_at, updated_at)
  index (status, published_at desc)

-- P6 projects + tags
project(id, slug unique, name, summary, description_json jsonb, description_html text,
        status text check (ACTIVE|PAUSED|COMPLETED|ARCHIVED), stack text[], repo_url, live_url,
        cover_media_id fk null, sort_order int, created_at, updated_at)
tag(id, slug unique, name unique)
post_tag(post_id fk, tag_id fk, primary key(post_id, tag_id))

-- P7 search
alter table post add column search_vector tsvector generated always as (
  setweight(to_tsvector('english', coalesce(title,'')), 'A') ||
  setweight(to_tsvector('english', coalesce(excerpt,'')), 'B') ||
  setweight(to_tsvector('english', coalesce(content_text,'')), 'C')) stored;
create index on post using gin(search_vector);

-- P8 guests + engagement
guest(id, public_id text unique /* Guest########## */, nickname text null,
      created_at, last_seen_at, banned_at null)
post_like(post_id fk, guest_id fk, created_at, primary key(post_id, guest_id))
post_view(post_id fk, viewer_hash text, day date, primary key(post_id, viewer_hash, day))

-- P9 comments
comment(id, post_id fk, parent_id fk comment null, guest_id fk null, is_author bool,
        body text, ip_hash text, created_at, edited_at null, deleted_at null)
  index (post_id, parent_id, created_at desc)
ban(id, kind text check (GUEST|IP), value text, reason text, created_at, unique(kind, value))
```

Counters (`view_count`, `like_count`, `comment_count`) are updated in the same transaction as the
row insert/delete (`update post set like_count = like_count + 1 ...`).

## API (target)

Public / guest (no login; guest cookie auto-issued):

| Method | Path | Notes |
|---|---|---|
| GET | `/api/public/meta` | ✅ P0. siteName, version, admin flag |
| GET | `/api/public/posts?page&size&tag&project` | published only |
| GET | `/api/public/posts/{slug}` | 404 if missing/draft (admin may see drafts) |
| POST | `/api/public/posts/{slug}/views` | 204, dedupe per D-012 |
| PUT / DELETE | `/api/public/posts/{slug}/like` | returns `{liked, likeCount}` |
| GET | `/api/public/projects`, `/api/public/projects/{slug}` | |
| GET | `/api/public/tags`, `/api/public/tags/{slug}/posts` | |
| GET | `/api/public/search?q&tag&project&page` | |
| GET | `/api/public/posts/{slug}/comments?page` | top-level + replies |
| POST | `/api/public/posts/{slug}/comments` | `{body, parentId?, website(honeypot)}` |
| PATCH / DELETE | `/api/guest/comments/{id}` | own comments only |
| GET / PUT | `/api/guest/me` | `{publicId, nickname, displayName}`; PUT sets nickname |

Auth:

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/otp/request` | `{email}` → always 202 |
| POST | `/api/auth/otp/verify` | `{email, code}` → 204 + session, or 401 |
| POST | `/api/auth/recovery` | `{code}` → 204 + session, or 401 |
| POST | `/api/auth/logout` | 204 |

Admin (`ROLE_ADMIN`): `/api/admin/posts` (CRUD + `/publish`, `/unpublish`), `/api/admin/media`
(multipart upload, list, delete, patch alt), `/api/admin/projects`, `/api/admin/tags`,
`/api/admin/comments` (list, delete), `/api/admin/bans` (list, create, delete),
`/api/admin/recovery-codes` (POST generate → codes once; GET → `{remaining}`).

## Security

### OTP login (P1)
1. `POST /otp/request {email}`: normalize (trim, lowercase). Rate-limit by IP hash. If it equals
   `blog.admin-email`: invalidate previous unconsumed codes, generate 6 digits via `SecureRandom`,
   store **BCrypt hash** + `expires_at = now + 10m`, send email **asynchronously** (`@Async`).
   Always return 202 with the same body.
2. `POST /otp/verify {email, code}`: find newest unconsumed, unexpired code for admin email.
   If none or email ≠ admin → 401. If `attempts >= 5` → 401 and treat as consumed. Compare with
   `PasswordEncoder.matches`; on failure `attempts++` → 401. On success: set `consumed_at`,
   `request.changeSessionId()`, create `UsernamePasswordAuthenticationToken(adminEmail, null,
   ROLE_ADMIN)`, save via `SecurityContextRepository` (`HttpSessionSecurityContextRepository`).
3. Recovery codes: 10 codes, format `XXXX-XXXX-XXXX` from Crockford base32 alphabet via
   `SecureRandom`; stored BCrypt-hashed; input normalized (uppercase, strip spaces/dashes).
   Verify checks all unused codes; on match mark `used_at`.
4. Email body: code, expiry, "if you didn't request this, ignore it", requester's approximate time.
   No link (avoids email-scanner prefetch issues).

### Guest cookie (P8)
`GuestCookieFilter` (OncePerRequestFilter, `/api/**` only): read `bg_guest`; verify HMAC with
`MessageDigest.isEqual`; if missing/invalid, generate new id + set cookie. Expose via a request
attribute and a `CurrentGuest` argument resolver. DB row created lazily by `GuestService.ensure()`.

### HTML sanitization (P3)
`HtmlSanitizer` (jsoup `Safelist`): allow `p h2 h3 h4 strong em s code pre blockquote ul ol li
hr br a img figure figcaption iframe span`; `a[href]` http/https/mailto only, add
`rel="noopener noreferrer nofollow"` for external; `img[src]` only from own media base URL(s)
+ `alt,width,height`; `iframe[src]` only `https://www.youtube-nocookie.com/embed/...`;
`code/span[class]` only `language-*`/`hljs-*`. Write tests with known XSS payloads
(`<script>`, `onerror=`, `javascript:` URLs, `<svg onload>`, `<iframe src=evil>`, data: URLs).

### Rate limits (Bucket4j, in-memory, keyed by IP hash / guest id)
| Action | Limit |
|---|---|
| OTP request | 3 / 15 min per IP, 10 / hour global for admin email |
| OTP / recovery verify | 10 / 15 min per IP |
| Comment create | 3 / min and 20 / day per guest; 30 / day per IP |
| Like toggle | 30 / min per guest |
| Nickname change | 5 / hour per guest |
| Media upload (admin) | none |
Exceeded ⇒ 429 ProblemDetail with `Retry-After`.

### IP hashing
`Hashing.ipHash(ip) = base64url(sha256(IP_HASH_SALT + ip))`. Client IP from
`request.getRemoteAddr()` (correct because `server.forward-headers-strategy=framework`).

### Headers
CSP (in `SecurityConfig`; widen `img-src` to the R2 domain only, not `https:`, once known),
`X-Content-Type-Options`, `Referrer-Policy: strict-origin-when-cross-origin`, HSTS in prod.

## Configuration

All via env vars (see `.env.example`). Mapped in `application.yaml` → `BlogProperties`.

| Env var | Phase | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 0 | `prod` in production; dev is default |
| `PORT` | 0 | injected by Railway |
| `BLOG_SITE_NAME`, `BLOG_SITE_DESCRIPTION`, `BLOG_BASE_URL` | 0 | branding, canonical URLs |
| `ADMIN_EMAIL` | 0/1 | the only admin |
| `JDBC_DATABASE_URL/USERNAME/PASSWORD` | 0 | Neon |
| `MAIL_HOST/PORT/USERNAME/PASSWORD`, `MAIL_FROM` | 1 | Resend SMTP |
| `BLOG_AUTHOR_NAME` | 1 | author display name |
| `STORAGE_DRIVER` (`local`/`r2`), `R2_*`, `R2_PUBLIC_BASE_URL` | 2 | images |
| `BLOG_RESERVED_NAMES` | 8 | comma-separated, e.g. owner's names |
| `GUEST_COOKIE_SECRET` | 8 | ≥ 32 bytes base64; rotating it resets all guest identities |
| `IP_HASH_SALT` | 8 | random string |

Dev defaults must work with zero env vars set.

## Testing strategy

| Layer | Tool | Needs Docker |
|---|---|---|
| Pure logic (sanitizer, nickname policy, SEO renderer, OTP generation) | JUnit 5 + AssertJ | no |
| Controllers + security rules | `@WebMvcTest` + `@Import(SecurityConfig.class)` + `@MockitoBean` services | no |
| Repositories, migrations, FTS, full flows | `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` | **yes** |
| Mail | assert via a mocked `JavaMailSender` or `spring-boot-starter-mail-test` support | no |
| Frontend logic/components | Vitest + Testing Library | no |

Every phase's acceptance criteria map to tests. Security rules (who can call what) **must** have
WebMvc tests, including the negative cases.
