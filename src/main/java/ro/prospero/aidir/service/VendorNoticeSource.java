package ro.prospero.aidir.service;

import ro.prospero.aidir.data.VendorNoticeDTO;

import java.util.List;

/** One feature's share of the vendor's notices. Each is a component, and {@link VendorNoticeService} asks them all. */
public interface VendorNoticeSource {
    List<VendorNoticeDTO> notices(long accountId);
}
