# Product spec — Project Blog

A personal blog where **one author** (the owner) writes rich, image-heavy posts about their
projects, and **anonymous readers** can browse, search, like and comment.

Read with: `DECISIONS.md` (why), `ARCHITECTURE.md` (how), `PLAN.md` (order of work).

## Roles

| Role | Who | How identified | Can |
|---|---|---|---|
| Admin / Author | The owner only | Email OTP → session (D-004) | Everything below + write/publish/delete posts, projects, tags, media; moderate comments; ban |
| Guest | Every other visitor | Signed `bg_guest` cookie, `Guest##########` (D-006) | Read, search, like, comment, edit/delete own comments, set nickname |

There is no other role. There is no registration.

## Features

### F1. Admin login
- `/admin/login`: enter email → "If that address is allowed, a code has been sent." (always).
- Enter 6-digit code → redirected to `/admin`. Code: 10-min expiry, single-use, 5 attempts max.
- "Use a recovery code instead" link → enter one recovery code → logged in; that code is burned.
- `/admin/settings`: generate recovery codes (shows 10 codes once, with copy/download; warns that
  regenerating invalidates the old ones), and shows how many unused codes remain.
- Logout button in admin header. Session lasts 30 days.

### F2. Writing posts (admin)
- `/admin/posts`: table of all posts (title, status, project, published date, views, likes, comments).
- `/admin/posts/new`, `/admin/posts/:id/edit`: TipTap editor with toolbar — H2/H3/H4, bold, italic,
  strike, inline code, link, bullet/ordered list, blockquote, code block (language select),
  horizontal rule, image, YouTube embed, undo/redo.
- Images: toolbar button, drag-and-drop, or paste → uploads → inserted with editable alt text.
  Upload progress shown; failures shown inline.
- Side panel fields: title, slug (auto from title, editable, unique), excerpt (optional; auto
  from first ~200 chars of text if empty), cover image, tags (create inline), project (optional),
  comments enabled toggle.
- Status: **Draft** or **Published**. Publish sets `published_at` (first time only; editable).
  Unpublish returns to draft. Delete requires typing the slug to confirm.
- Autosave draft every 10 s while dirty (PUT) + "Saved · 12:01" indicator; warn on navigation with
  unsaved changes.
- Preview: renders the sanitized HTML exactly as the public page would.

### F3. Reading posts (public)
- `/`: published posts, newest first, paginated (10/page): cover, title, excerpt, date, tags,
  reading time, like/comment counts.
- `/posts/:slug`: title, date, reading time, tags, project link, cover, content (sanitized HTML,
  syntax-highlighted code, responsive images, lazy-loaded), like button, view count, comments.
- Drafts are never visible publicly (404), even by guessing the slug. Admin sees a "Draft preview"
  banner instead.
- Per-post SEO/OG tags (D-002). `sitemap.xml`, `rss.xml`, `robots.txt`.

### F4. Projects
- Admin CRUD: name, slug, summary, description (same TipTap editor, simpler toolbar OK),
  status (Active / Paused / Completed / Archived), tech stack (list of strings), repo URL,
  live URL, cover image, sort order.
- `/projects`: grid of projects (cover, name, status badge, stack chips, summary).
- `/projects/:slug`: project details + its published posts (newest first).

### F5. Tags
- Tags are created inline from the post editor; admin can rename/delete on `/admin/tags`.
- `/tags/:slug`: published posts with that tag. Tag chips on posts link here.

### F6. Search
- Search box in header → `/search?q=...`: ranked results with highlighted snippet, paginated.
- Filters: tag, project. Empty query shows nothing (not all posts).

### F7. Guest identity
- Every visitor gets a guest ID automatically; shown in a small "You are Guest2819392104 · set
  nickname" menu in the header.
- Nickname rules per D-007. Errors are specific ("That name is reserved", "Letters, numbers,
  spaces, _ . - only").

### F8. Likes and views
- Like button (heart + count) toggles; optimistic UI.
- View count shown on post page and admin table; deduped per D-012.

### F9. Comments
- Under each post when enabled: newest-first top-level comments with one level of replies,
  paginated (20 top-level per page).
- Form: textarea (2000 chars, counter), hidden honeypot. Shows "Commenting as Nick · #2104".
- Own comments: Edit (15-min window) and Delete. Deleted comments with replies show
  "[deleted]"; without replies they disappear.
- Author comments show the author's name + **Author** badge.
- Admin (`/admin/comments`): latest comments across posts, delete, ban guest, ban IP hash,
  unban list. Per-post "close comments" toggle (existing comments stay visible, form hidden).
- Banned guests/IPs: write endpoints return 403 "You can't comment right now." Reading unaffected.

## Non-functional requirements
- **Security:** see ARCHITECTURE §Security. All HTML sanitized server-side; CSP header; CSRF; no
  raw IPs stored; secrets only via env vars.
- **Performance:** post page LCP < 2.5 s on 4G; images lazy-loaded with width/height set.
  Public GET endpoints send `Cache-Control: public, max-age=60` where safe.
- **Accessibility:** shadcn/radix primitives; every image has alt text (editor enforces non-empty
  alt or explicit "decorative"); keyboard-navigable editor toolbar; color contrast AA.
- **Responsive:** mobile-first; reading width ~70ch.
- **Dark mode:** follows system, with a toggle (shadcn theme tokens).
- **Privacy:** no third-party trackers or fonts from CDNs. YouTube embeds via youtube-nocookie.

## Out of scope (don't build unless the owner asks)
Multiple authors · reader accounts/OAuth · newsletters · i18n · scheduled publishing ·
post revisions history · analytics dashboards · WebSockets/live comments · native apps.
