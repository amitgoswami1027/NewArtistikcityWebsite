package com.artistikcity.web;

/**
 * The old blog pages ({@code /blog/posts}, {@code /blog/post/{slug}}) moved to Studio Stories: see
 * {@link StudioStoriesController}, which publishes every story written in Admin > Content > Studio Stories at
 * {@code /studio-stories} and redirects the old addresses there (301). Kept empty so older checkouts of this file
 * don't map the same addresses twice.
 */
final class BlogController {

    private BlogController() {
    }
}
