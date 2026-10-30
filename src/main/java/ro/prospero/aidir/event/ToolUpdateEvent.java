package ro.prospero.aidir.event;

import ro.prospero.aidir.data.ToolDTO;

/** A listed tool whose searchable fields changed, so its index document is replaced rather than added. */
public record ToolUpdateEvent(ToolDTO tool) {
}
