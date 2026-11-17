package io.github.colla69.makemytaxes.app.document;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.document.Recognition;
import io.github.colla69.makemytaxes.core.form.CertificateText;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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
    return Recognition.of(CertificatePdfReader.textOf(bytes), textOf(bytes), taxYear);
  }

  private static String textOf(byte[] pdf) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      return stripper.getText(document);
    } catch (IOException e) {
      return "";
    }
  }
}
