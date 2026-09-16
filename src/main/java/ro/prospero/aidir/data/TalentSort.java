package ro.prospero.aidir.data;

/**
 * How a page of the talent directory is ordered.
 *
 * <p>There is no RELEVANCE: the Lucene index is tool-shaped ({@code search/LuceneToolFields}), so talent
 * text matching is a SQL {@code ILIKE} with nothing to score against. A ranked order would have to invent
 * its ranking.
 *
 * <p>There is no EXPERIENCE either, though the directory offers it. Years of experience is not a column -
 * {@code talent_experience.period} is free text like "2021 - Present" - so there is nothing to sort on
 * until that is parsed or captured.
 */
public enum TalentSort {
    NEWEST,
    NAME
}
