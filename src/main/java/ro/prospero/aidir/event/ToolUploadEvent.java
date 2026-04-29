package ro.prospero.aidir.event;

import ro.prospero.aidir.data.ToolDTO;

public record ToolUploadEvent(ToolDTO tool) {
}
