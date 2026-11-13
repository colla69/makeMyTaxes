package io.github.colla69.makemytaxes.app.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** A generated Lohnsteuerbescheinigung with made-up values in the layout of the sample 2022. */
public final class MadeUpCertificate {

  private static final float LEFT_X = 55;
  private static final float RIGHT_X = 300;
  private static final float AMOUNT_X = 520;

  private static final String PERIOD = "01.01.-30.06.";

  /**
   * Each row after line 1: left column text, right column text, amount. A row shares one height.
   */
  private static final List<String[]> ROWS =
      List.of(
          row("Datum: 14.02.2023", "3. Bruttoarbeitslohn einschl. Sachbezüge", "24.000,00"),
          row("Steuerklasse/Faktor", "4. Einbehaltene Lohnsteuer von 3.", "3.100,00"),
          row("3", "5. Einbehaltener Solidaritätszuschlag von 3.", ""),
          row("Steuerfreier Jahresbetrag", "6. Einbehaltene Kirchensteuer des Arbeitnehmers", ""),
          row("1.200,00", "17. Steuerfreie Arbeitgeberleistungen", "240,00"),
          row("", "18. Pauschal mit 15 % besteuerte Leistungen", ""),
          row("", "22. Arbeitgeber- a) gesetzliche RV", "2.232,00"),
          row("", "anteil b) berufsständische", ""),
          row("", "23. Arbeitnehmer- a) gesetzliche RV", "2.232,00"),
          row("", "anteil b) berufsständische", "40,00"),
          row("Anschrift und Steuernummer des Arbeitgebers:", "25. Arbeitnehmerbeiträge zur KV", "1.752,00"),
          row("Beispiel Werkstatt GmbH", "26. Arbeitnehmerbeiträge zur PV", "366,00"),
          row("Musterweg 1, 00000 Musterstadt", "27. Arbeitnehmerbeiträge zur AV", "288,00"));

  private MadeUpCertificate() {}

  private static String[] row(String left, String right, String amount) {
    return new String[] {left, right, amount};
  }

  /** The certificate for the year, as the bytes of a PDF with text. */
  public static byte[] pdf(String year) {
    return pdf(year, PERIOD, Map.of());
  }

  /** The certificate for the year, with the given text for the period of line 1. */
  public static byte[] pdf(String year, String period) {
    return pdf(year, period, Map.of());
  }

  /**
   * A corrected certificate for the year: each amount in the map replaces the amount of the row
   * whose right column starts with its key, such as "3." for the gross wage. An empty amount
   * leaves the row without a value.
   */
  public static byte[] pdf(String year, Map<String, String> amounts) {
    return pdf(year, PERIOD, amounts);
  }

  private static byte[] pdf(String year, String period, Map<String, String> amounts) {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        text(content, font, 14, LEFT_X, 800, "Ausdruck der elektronischen Lohnsteuerbescheinigung für " + year);
        text(content, font, 8, LEFT_X, 740, "Korrektur/Stornierung");
        text(content, font, 8, RIGHT_X, 740, "1. Bescheinigungszeitraum " + period);
        float y = 718;
        for (String[] row : ROWS) {
          text(content, font, 8, LEFT_X, y, row[0]);
          text(content, font, 8, RIGHT_X, y, row[1]);
          text(content, font, 8, AMOUNT_X, y, amountOf(row, amounts));
          y -= 22;
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String amountOf(String[] row, Map<String, String> amounts) {
    return amounts.entrySet().stream()
        .filter(amount -> row[1].startsWith(amount.getKey() + " "))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(row[2]);
  }

  private static void text(
      PDPageContentStream content, PDType1Font font, float size, float x, float y, String text)
      throws IOException {
    if (text.isEmpty()) {
      return;
    }
    content.beginText();
    content.setFont(font, size);
    content.newLineAtOffset(x, y);
    content.showText(text);
    content.endText();
  }
}
