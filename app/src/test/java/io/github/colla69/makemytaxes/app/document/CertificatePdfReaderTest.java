package io.github.colla69.makemytaxes.app.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificate2022;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

/** Each PDF here is generated with made-up values in the layout of the official sample 2022. */
class CertificatePdfReaderTest {

  @Test
  void readsTheValuesOfAGeneratedCertificate() {
    CertificateValues values = CertificatePdfReader.read(MadeUpCertificate.pdf("2022"));

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
  void aCertificateOfAnotherYearGivesNotFoundForEachField() {
    assertNothingFound(CertificatePdfReader.read(MadeUpCertificate.pdf("2023")));
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
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }
}
