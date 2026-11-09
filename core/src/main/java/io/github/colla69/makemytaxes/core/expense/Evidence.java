package io.github.colla69.makemytaxes.core.expense;

/**
 * What proves an expense. A linked document or a stated basis is evidence. A stated basis says
 * where the amount comes from when no receipt exists by nature, for example a day count or a
 * distance. The receipt note only says where a receipt is, so it is not evidence.
 *
 * @param receiptNote a note such as a file name or a folder
 * @param documentName the name of the linked document, or null if the expense has no document
 * @param statedBasis the stated basis, or an empty text
 */
public record Evidence(String receiptNote, String documentName, String statedBasis) {

  public boolean isPresent() {
    return documentName != null || hasStatedBasis();
  }

  public boolean hasStatedBasis() {
    return statedBasis != null && !statedBasis.isBlank();
  }

  public boolean hasReceiptNote() {
    return receiptNote != null && !receiptNote.isBlank();
  }

  /** A document comes first, because it is the stronger proof of the two. */
  public String label() {
    if (documentName != null) {
      return "Beleg: " + documentName;
    }
    return hasStatedBasis() ? "Grundlage: " + statedBasis : "Beleg fehlt";
  }
}
