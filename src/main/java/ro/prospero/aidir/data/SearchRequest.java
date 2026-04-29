package ro.prospero.aidir.data;

public record SearchRequest(
        String queryText,
        String docType,
        int limit
) {}
