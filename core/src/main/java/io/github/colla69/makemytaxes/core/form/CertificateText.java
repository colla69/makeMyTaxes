package io.github.colla69.makemytaxes.core.form;

/**
 * The text of the first page of a printed Lohnsteuerbescheinigung. The left column holds the
 * employer, the tax class and some amounts without a line number. The right column holds the
 * numbered lines. The two columns are read apart, so that an amount of one column is never taken
 * for a line of the other.
 *
 * @param page the text of the whole page, for the title with the year
 * @param leftColumn the text of the left half of the page
 * @param rightColumn the text of the right half of the page
 */
public record CertificateText(String page, String leftColumn, String rightColumn) {

  /** The text of a document without text, such as a scan. */
  public static final CertificateText EMPTY = new CertificateText("", "", "");
}
