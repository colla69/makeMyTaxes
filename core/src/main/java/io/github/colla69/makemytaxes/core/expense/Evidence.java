package io.github.colla69.makemytaxes.core.expense;

import io.github.colla69.makemytaxes.core.document.DocumentRef;

/**
 * What proves an expense. A linked document or a stated basis is evidence. A stated basis says
 * where the amount comes from when no receipt exists by nature, for example a day count or a
 * distance. The receipt note only says where a receipt is, so it is not evidence.
 *
 * @param receiptNote a note such as a file name or a folder
 * @param document the linked document, or null if the expense has no document
 * @param statedBasis the stated basis, or an empty text
 */
public record Evidence(String receiptNote, DocumentRef document, String statedBasis) {

  public boolean isPresent() {
    return document != null || hasStatedBasis();
  }

  public boolean hasStatedBasis() {
    return statedBasis != null && !statedBasis.isBlank();
  }

  public boolean hasReceiptNote() {
    return receiptNote != null && !receiptNote.isBlank();
  }

  /** The file name of the linked document, or null. */
  public String documentName() {
    return document == null ? null : document.fileName();
  }
}
