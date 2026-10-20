package ro.prospero.aidir.data;

import java.util.List;

/**
 * What a submitted article or a brief costs in euros, line by line, as the order summary shows it. Mirrors
 * ArticleQuote in aidir-fe.
 */
public record ArticleQuoteDTO(List<Line> lines, int subtotal, int discount, int total) {

    public record Line(String label, int amount) {
    }
}
