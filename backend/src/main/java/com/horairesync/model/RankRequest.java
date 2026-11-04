package com.horairesync.model;

import java.util.List;

/**
 * Sections to score against preferences.
 *
 * @param sections    candidate sections (usually all sections of one course)
 * @param preferences user preferences
 * @param chosen      sections already chosen for other courses, to flag conflicts
 */
public record RankRequest(List<Section> sections, Preferences preferences, List<SelectedSection> chosen) {

    public RankRequest {
        sections = sections == null ? List.of() : List.copyOf(sections);
        preferences = preferences == null ? Preferences.none() : preferences;
        chosen = chosen == null ? List.of() : List.copyOf(chosen);
    }
}
