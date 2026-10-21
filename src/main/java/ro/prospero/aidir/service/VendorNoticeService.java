package ro.prospero.aidir.service;

import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.VendorNoticeDTO;

import java.util.Comparator;
import java.util.List;

/**
 * Everything the vendor is to act on, gathered from every {@link VendorNoticeSource}, newest first. A feature
 * with something to ask of vendors adds a source rather than changing this.
 */
@Service
public class VendorNoticeService {
    private final List<VendorNoticeSource> sources;

    public VendorNoticeService(List<VendorNoticeSource> sources) {
        this.sources = sources;
    }

    public List<VendorNoticeDTO> notices(long accountId) {
        return sources.stream()
                      .flatMap(source -> source.notices(accountId).stream())
                      .sorted(Comparator.comparing(VendorNoticeDTO::since).reversed())
                      .toList();
    }
}
