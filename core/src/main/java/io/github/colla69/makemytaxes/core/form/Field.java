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
}
