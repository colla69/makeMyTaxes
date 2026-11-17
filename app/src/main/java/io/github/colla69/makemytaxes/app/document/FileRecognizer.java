package io.github.colla69.makemytaxes.app.document;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.Recognition;

/**
 * Tells what an uploaded file is from its text, on this machine with Apache PDFBox. An image or a
 * scan has no text, so the app cannot tell what it is. There is no OCR.
 */
public final class FileRecognizer {

  private FileRecognizer() {}

  public static Recognition recognize(byte[] bytes, String mediaType, TaxYear taxYear) {
    if (!mediaType.equals("application/pdf")) {
      return new Recognition.Unknown();
    }
    return Recognition.of(
        CertificatePdfReader.textOf(bytes), PayslipPdfReader.textOf(bytes), taxYear);
  }
}
