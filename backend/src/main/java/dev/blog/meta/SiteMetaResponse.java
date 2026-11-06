package dev.blog.meta;

/** Mirrors frontend/src/api/meta.ts#SiteMeta. Change both together. */
public record SiteMetaResponse(String siteName, String version, boolean admin) {
}
