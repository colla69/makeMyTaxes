package io.github.colla69.makemytaxes.core.form;

/**
 * One question of a form.
 *
 * @param line the line on the form, or the legal source of a question that is on no form
 * @param help a short hint, or an empty text
 */
public record Field(
    String id, String section, String line, String label, Kind kind, boolean required, String help) {

  public static Field required(String id, String section, String line, String label, Kind kind) {
    return new Field(id, section, line, label, kind, true, "");
  }

  public static Field optional(String id, String section, String line, String label, Kind kind) {
    return new Field(id, section, line, label, kind, false, "");
  }

  public Field withHelp(String text) {
    return new Field(id, section, line, label, kind, required, text);
  }

  /** The same field for one entry of a list, such as one employer. */
  Field inEntry(String entryId, String entrySection) {
    return new Field(entryId, entrySection, line, label, kind, required, help);
  }

  /**
   * The ID of the hint text. A field of a list entry has the ID {@code <entry>-<field>}, and all
   * entries share the hint of the field.
   */
  public String hintId() {
    return id.substring(id.indexOf('-') + 1);
  }
}
