package ro.prospero.aidir.data;

/** The directory's filters. {@link #param()} names both the browse parameter and the filter's facet. */
public enum ToolFilter {
    CATEGORY("category"),
    PRICING("pricing"),
    PRICE_RANGE("priceRange"),
    PLATFORM("platform"),
    TARGET_USER("targetUser"),
    LANGUAGE("language"),
    AI_TECHNOLOGY("aiTechnology"),
    DEPLOYMENT("deployment"),
    INTEGRATION("integration"),
    INDUSTRY("industry");

    private final String param;

    ToolFilter(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }
}
