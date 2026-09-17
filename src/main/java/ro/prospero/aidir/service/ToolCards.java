package ro.prospero.aidir.service;

import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.JsonbArrays;
import ro.prospero.aidir.data.ToolCardDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolRecord;

import java.util.List;
import java.util.Map;

/** Builds directory cards out of tool rows, for the directory itself and for the alternatives strip on a details page. */
@Component
public class ToolCards {

    private final ToolService toolService;

    public ToolCards(ToolService toolService) {
        this.toolService = toolService;
    }

    /** Logos are read for the whole set in one query, not one per row. */
    public List<ToolCardDTO> toCards(List<ToolRecord> rows) {
        List<Long> ids = rows.stream().map(ToolRecord::getId).toList();
        Map<Long, String> logoUrls = toolService.findLogoUrls(ids);

        return rows.stream()
                   .map(row -> new ToolCardDTO(row.getId(),
                                               row.getName(),
                                               row.getShortDescription(),
                                               row.getUrl(),
                                               row.getPricing(),
                                               JsonbArrays.readStringArray(row.getCategories()),
                                               JsonbArrays.readStringArray(row.getTags()),
                                               logoUrls.get(row.getId()),
                                               row.getApprovedAt()))
                   .toList();
    }
}
