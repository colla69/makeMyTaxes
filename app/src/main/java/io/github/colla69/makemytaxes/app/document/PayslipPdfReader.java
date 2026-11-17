package io.github.colla69.makemytaxes.app.document;

import io.github.colla69.makemytaxes.core.document.PayslipReader;
import io.github.colla69.makemytaxes.core.document.PayslipContent;
import java.io.IOException;
import java.time.YearMonth;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads the text of a stored payslip on this machine with Apache PDFBox and finds its amounts. A
 * scan or an image has no text, so no amount is found. There is no OCR.
 */
public final class PayslipPdfReader {

  private PayslipPdfReader() {}

  public static PayslipContent read(byte[] pdf, YearMonth month) {
    return PayslipReader.readContent(textOf(pdf), month);
  }

  /** The text that both the recognition and the payslip table read, so that they agree. */
  static String textOf(byte[] pdf) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      return stripper.getText(document);
    } catch (IOException e) {
      return "";
    }
  }
}
