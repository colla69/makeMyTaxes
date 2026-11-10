package io.github.colla69.makemytaxes.app.document;

import io.github.colla69.makemytaxes.core.form.CertificateText;
import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificateReader2022;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

/**
 * Reads the text of a stored PDF on this machine with Apache PDFBox and finds the values of a
 * Lohnsteuerbescheinigung 2022 in it. It reads the first page only. A scan has no text, so each
 * field is not found. There is no OCR.
 */
public final class CertificatePdfReader {

  /**
   * On the official sample, the text of the left column ends at about 0.47 of the page width, and
   * the table of numbered lines starts at about 0.52. The split lies in this gap.
   */
  private static final double COLUMN_SPLIT = 0.49;

  private CertificatePdfReader() {}

  public static CertificateValues read(byte[] pdf) {
    return WageTaxCertificateReader2022.read(textOf(pdf));
  }

  /** The text of the first page, or no text if the file is not a readable PDF. */
  static CertificateText textOf(byte[] pdf) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      if (document.getNumberOfPages() == 0) {
        return CertificateText.EMPTY;
      }
      return textOf(document);
    } catch (IOException e) {
      return CertificateText.EMPTY;
    }
  }

  private static CertificateText textOf(PDDocument document) throws IOException {
    PDFTextStripper stripper = new PDFTextStripper();
    stripper.setSortByPosition(true);
    stripper.setStartPage(1);
    stripper.setEndPage(1);
    String page = stripper.getText(document);

    PDPage first = document.getPage(0);
    PDRectangle box = first.getCropBox();
    double split = box.getWidth() * COLUMN_SPLIT;
    PDFTextStripperByArea columns = new PDFTextStripperByArea();
    columns.setSortByPosition(true);
    columns.addRegion("left", new Rectangle2D.Double(0, 0, split, box.getHeight()));
    columns.addRegion(
        "right", new Rectangle2D.Double(split, 0, box.getWidth() - split, box.getHeight()));
    columns.extractRegions(first);
    return new CertificateText(
        page, columns.getTextForRegion("left"), columns.getTextForRegion("right"));
  }
}
