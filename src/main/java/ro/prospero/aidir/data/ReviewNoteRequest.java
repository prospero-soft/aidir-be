package ro.prospero.aidir.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What a reviewer tells the vendor: the compliance issues an article is sent back with, or why a brief was
 * declined. The vendor acts on it, so it is required.
 */
public record ReviewNoteRequest(@NotBlank @Size(max = 2000) String note) {
}
