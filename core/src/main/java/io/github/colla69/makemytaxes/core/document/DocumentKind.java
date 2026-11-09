package io.github.colla69.makemytaxes.core.document;

/** What a document of a return is. The label is the German name that the user sees. */
public enum DocumentKind {
  WAGE_TAX_CERTIFICATE("Lohnsteuerbescheinigung"),
  INVOICE_OR_RECEIPT("Rechnung/Quittung"),
  CERTIFICATE("Bescheinigung"),
  OTHER("Sonstiges");

  private final String label;

  DocumentKind(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
