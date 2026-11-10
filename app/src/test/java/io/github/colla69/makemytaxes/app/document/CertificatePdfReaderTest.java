package io.github.colla69.makemytaxes.app.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificate2022;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

/** Each PDF here is generated with made-up values in the layout of the official sample 2022. */
class CertificatePdfReaderTest {

  private static final float LEFT_X = 55;
  private static final float RIGHT_X = 300;
  private static final float AMOUNT_X = 520;

  /** Each row: left column text, right column text, amount. A row shares one height. */
  private static final List<String[]> ROWS =
      List.of(
          row("Korrektur/Stornierung", "1. Bescheinigungszeitraum", "01.01.-30.06."),
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

  private static String[] row(String left, String right, String amount) {
    return new String[] {left, right, amount};
  }

  @Test
  void readsTheValuesOfAGeneratedCertificate() throws IOException {
    CertificateValues values = CertificatePdfReader.read(certificate("2022"));

    assertThat(values.found())
        .containsEntry("employerName", "Beispiel Werkstatt GmbH")
        .containsEntry("taxClass", "3")
        .containsEntry("employedFrom", "01.01.2022")
        .containsEntry("employedTo", "30.06.2022")
        .containsEntry("grossWage", "24.000,00")
        .containsEntry("wageTax", "3.100,00")
        .containsEntry("employerCommute", "240,00")
        .containsEntry("pensionEmployer", "2.232,00")
        .containsEntry("pensionEmployee", "2.272,00")
        .containsEntry("healthEmployee", "1.752,00")
        .containsEntry("careEmployee", "366,00")
        .containsEntry("unemploymentEmployee", "288,00")
        .doesNotContainValue("1.200,00");
    assertThat(values.valueOrNotFound("solidarity")).isEqualTo("nicht gefunden");
  }

  @Test
  void aCertificateOfAnotherYearGivesNotFoundForEachField() throws IOException {
    assertNothingFound(CertificatePdfReader.read(certificate("2023")));
  }

  @Test
  void aScanWithoutTextGivesNotFoundForEachField() throws IOException {
    assertNothingFound(CertificatePdfReader.read(scan()));
  }

  @Test
  void aFileThatIsNotAPdfGivesNotFoundForEachField() {
    assertNothingFound(
        CertificatePdfReader.read("not a pdf".getBytes(StandardCharsets.US_ASCII)));
  }

  private static void assertNothingFound(CertificateValues values) {
    for (String fieldId : WageTaxCertificate2022.fieldIds()) {
      assertThat(values.valueOrNotFound(fieldId)).isEqualTo("nicht gefunden");
    }
  }

  private static byte[] certificate(String year) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        text(content, font, 14, LEFT_X, 800, "Ausdruck der elektronischen Lohnsteuerbescheinigung für " + year);
        float y = 740;
        for (String[] row : ROWS) {
          text(content, font, 8, LEFT_X, y, row[0]);
          text(content, font, 8, RIGHT_X, y, row[1]);
          text(content, font, 8, AMOUNT_X, y, row[2]);
          y -= 22;
        }
      }
      return bytesOf(document);
    }
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

  private static byte[] scan() throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDImageXObject image =
          LosslessFactory.createFromImage(
              document, new BufferedImage(200, 280, BufferedImage.TYPE_INT_RGB));
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
      }
      return bytesOf(document);
    }
  }

  private static byte[] bytesOf(PDDocument document) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    document.save(out);
    return out.toByteArray();
  }
}
