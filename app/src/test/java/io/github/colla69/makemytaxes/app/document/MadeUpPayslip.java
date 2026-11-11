package io.github.colla69.makemytaxes.app.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** A generated monthly payslip with made-up values. */
public final class MadeUpPayslip {

  private MadeUpPayslip() {}

  /**
   * The payslip for a month, as the bytes of a PDF with text.
   *
   * @param monthAndYear the month as the payslip prints it, such as "Januar 2022"
   * @param amounts the taxable gross pay and the wage tax, such as "4.000,00" and "500,00"
   */
  public static byte[] pdf(String monthAndYear, String... amounts) {
    List<String> lines =
        List.of(
            "Abrechnung der Brutto/Netto-Bezüge für " + monthAndYear,
            "Beispiel Werkstatt GmbH, Musterweg 1, 00000 Musterstadt",
            "Gesamt-Brutto " + amounts[0],
            "Steuer-Brutto " + amounts[0],
            "Lohnsteuer " + amounts[1],
            "Kirchensteuer 0,00");
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        float y = 800;
        for (String line : lines) {
          content.beginText();
          content.setFont(font, 10);
          content.newLineAtOffset(55, y);
          content.showText(line);
          content.endText();
          y -= 20;
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
