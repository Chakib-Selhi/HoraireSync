package com.horairesync.http;

import org.jsoup.nodes.Document;

/**
 * Gives access to remote HTML pages.
 *
 * <p>Adapters depend on this interface only, so tests replace it with fixtures and never touch the network.
 */
@FunctionalInterface
public interface HtmlSource {

    /**
     * Returns the parsed page at {@code url}.
     *
     * @throws com.horairesync.error.HoraireSyncException with {@code SOURCE_UNAVAILABLE} when the page cannot be read
     */
    Document get(String url);
}
