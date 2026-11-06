package dev.blog.web;

/**
 * Per-page metadata injected into index.html for crawlers and link-preview bots.
 * All fields are plain text; SeoRenderer HTML-escapes them.
 *
 * @param canonicalUrl absolute URL of the page
 * @param imageUrl     absolute URL of the preview image, or null
 * @param type         Open Graph type: "website" or "article"
 */
public record SeoTags(String title, String description, String canonicalUrl, String imageUrl, String type) {
}
