package io.github.colla69.makemytaxes.core.form;

import io.github.colla69.makemytaxes.core.document.DocumentRef;
import io.github.colla69.makemytaxes.core.expense.Evidence;

/** Where a value on the review comes from. The label is the German text that the user sees. */
public sealed interface Source {

  String label();

  /** The source of an expense. A document comes first, because it is the stronger proof. */
  static Source of(Evidence evidence) {
    if (evidence.document() != null) {
      return new FromDocument(evidence.document());
    }
    return evidence.hasStatedBasis()
        ? new StatedBasis(evidence.statedBasis())
        : new Missing(evidence.receiptNote());
  }

  /** The document of the value, or null if the value has none. */
  default DocumentRef document() {
    return null;
  }

  /**
   * A saved answer of a form. Only the storage knows whether it comes from a document, so the app
   * replaces this source before it shows the line.
   */
  record Answer(String fieldId) implements Source {
    @Override
    public String label() {
      return "eigene Angabe";
    }
  }

  record FromDocument(DocumentRef document) implements Source {
    @Override
    public String label() {
      return "Beleg: " + document.fileName();
    }
  }

  record StatedBasis(String text) implements Source {
    @Override
    public String label() {
      return "Grundlage: " + text;
    }
  }

  record OwnStatement() implements Source {
    @Override
    public String label() {
      return "eigene Angabe";
    }
  }

  /** The sum of the lines above it. */
  record Sum() implements Source {
    @Override
    public String label() {
      return "Summe der Zeilen darüber";
    }
  }

  /**
   * An expense with no evidence. The receipt note only says where a receipt is, so it is not
   * evidence, but it stays visible.
   */
  record Missing(String receiptNote) implements Source {
    @Override
    public String label() {
      return receiptNote == null || receiptNote.isBlank()
          ? "Beleg fehlt"
          : "Hinweis: " + receiptNote + ", Beleg fehlt";
    }
  }
}
