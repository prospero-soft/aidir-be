package ro.prospero.aidir.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why a deal was turned down. The vendor sees it on the deal, so it is required. */
public record RejectDealRequest(@NotBlank @Size(max = 1000) String reason) {
}
