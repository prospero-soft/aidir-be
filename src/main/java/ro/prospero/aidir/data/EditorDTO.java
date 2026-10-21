package ro.prospero.aidir.data;

/** An admin account a brief can be assigned to. Mirrors Editor in aidir-fe (src/types/articleBrief.ts). */
public record EditorDTO(long id, String name, String email) {
}
