package io.github.colla69.makemytaxes.core.form;

/** One value to enter in Mein ELSTER, with its source. */
public record ReviewLine(String section, String line, String label, String value, Source source) {

  /** The same line with another source. */
  public ReviewLine withSource(Source newSource) {
    return new ReviewLine(section, line, label, value, newSource);
  }
}
