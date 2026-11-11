package io.github.colla69.makemytaxes.core.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The layout comes from the official sample "Ausdruck der elektronischen Lohnsteuerbescheinigung
 * für 2022", print mark 3.21, https://www.elster.de/eportal/attachments/help_lstb_2022/Muster_LSTB_2022.pdf.
 *
 * <p>The sums come from the official forms on https://www.formulare-bfinv.de, Steuerformulare >
 * Einkommensteuer > Einkommensteuer 2022:
 *
 * <ul>
 *   <li>Anlage Vorsorgeaufwand 2022, form 082, mark 2022AnlVor241NET, Juli 2022: line 4 takes
 *       "Arbeitnehmeranteil lt. Nr. 23 a/b" in one box (300), and line 9 takes "Arbeitgeberanteil /
 *       -zuschuss lt. Nr. 22 a/b" in one box (304). So the reader adds a and b.
 *   <li>Anlage N 2022, form 055, mark 2022AnlN032NET, Juli 2022: line 39 takes Nr. 17 in box 290
 *       (steuerfrei ersetzt) and Nr. 18 in box 295 (pauschal besteuert). So the reader does not
 *       add them, and gives "nicht gefunden" if both lines have an amount.
 * </ul>
 *
 * <p>All values are made up.
 */
class WageTaxCertificateReader2022Test {

  private static final String PAGE =
      """
      Ausdruck der elektronischen Lohnsteuerbescheinigung für 2022
      Nachstehende Daten wurden maschinell an die Finanzverwaltung übertragen.
      """;

  private static final String LEFT =
      """
      Korrektur/Stornierung
      Datum: 14.02.2023
      Identifikationsnummer: 00 000 000 000
      Dem Lohnsteuerabzug wurden im letzten Lohnzahlungszeitraum
      zugrunde gelegt:
      Steuerklasse/Faktor
      1
      Zahl der Kinderfreibeträge
      0,5
      Steuerfreier Jahresbetrag
      1.200,00
      Anschrift und Steuernummer des Arbeitgebers:
      Beispiel Werkstatt GmbH
      Musterweg 1, 00000 Musterstadt
      """;

  private static String right(String... numberedLines) {
    return """
        vom - bis
        1. Bescheinigungszeitraum 01.03.-31.12.
        2. Zeiträume ohne Anspruch auf Arbeitslohn
        Großbuchstaben (S, M, F, FR)
        EUR Ct
        3. Bruttoarbeitslohn einschl. Sachbezüge ohne 9. 30.000,00
        und 10.
        4. Einbehaltene Lohnsteuer von 3. 4.321,00
        5. Einbehaltener Solidaritätszuschlag von 3.
        6. Einbehaltene Kirchensteuer des Arbeitnehmers 210,50
        von 3.
        7. Einbehaltene Kirchensteuer des Ehegatten/Lebenspartners
        von 3. (nur bei Konfessionsverschiedenheit)
        """
        + String.join("\n", numberedLines)
        + "\n";
  }

  private static final String[] SOCIAL =
      """
      15. (Saison-)Kurzarbeitergeld, Zuschuss zum Mutterschaftsgeld,
      Verdienstausfallentschädigung (Infektionsschutzgesetz),
      Aufstockungsbetrag und Altersteilzeitzuschlag 150,00
      17. Steuerfreie Arbeitgeberleistungen, die auf die 300,00
      Entfernungspauschale anzurechnen sind
      18. Pauschal mit 15 % besteuerte Arbeitgeberleistungen für
      Fahrten zwischen Wohnung und erster Tätigkeitsstätte
      22. Arbeitgeber- a) zur gesetzlichen Rentenversicherung 2.790,00
      anteil/ b) an berufsständische Versorgungs-
      -zuschuss einrichtungen 100,00
      23. Arbeitnehmer- a) zur gesetzlichen Rentenversicherung 2.790,00
      anteil b) an berufsständische Versorgungs- 50,00
      einrichtungen
      24. Steuerfreie a) zur gesetzlichen Krankenversicherung
      Arbeitgeber- b) zur privaten Krankenversicherung 1.000,00
      zuschüsse c) zur gesetzlichen Pflegeversicherung 120,00
      25. Arbeitnehmerbeiträge zur gesetzlichen Krankenversicherung
      26. Arbeitnehmerbeiträge zur sozialen Pflegeversicherung 455,25
      27. Arbeitnehmerbeiträge zur Arbeitslosenversicherung 360,00
      28. Beiträge zur privaten Kranken- und Pflege-Pflichtversicherung
      oder Mindestvorsorgepauschale 1.900,00
      """
          .lines()
          .toArray(String[]::new);

  private static CertificateValues read(String right) {
    return WageTaxCertificateReader2022.read(new CertificateText(PAGE, LEFT, right));
  }

  @Test
  @DisplayName("Sample of the Lohnsteuerbescheinigung 2022: each field gets the value of its line")
  void readsEachFieldFromItsLine() {
    CertificateValues values = read(right(SOCIAL));

    assertThat(values.found())
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("employerName", "Beispiel Werkstatt GmbH"),
                Map.entry("taxClass", "1"),
                Map.entry("employedFrom", "01.03.2022"),
                Map.entry("employedTo", "31.12.2022"),
                Map.entry("grossWage", "30.000,00"),
                Map.entry("wageTax", "4.321,00"),
                Map.entry("churchTax", "210,50"),
                Map.entry("shortTimeWork", "150,00"),
                Map.entry("employerCommute", "300,00"),
                Map.entry("pensionEmployer", "2.890,00"),
                Map.entry("pensionEmployee", "2.840,00"),
                Map.entry("healthAllowancePrivate", "1.000,00"),
                Map.entry("careAllowance", "120,00"),
                Map.entry("careEmployee", "455,25"),
                Map.entry("unemploymentEmployee", "360,00")));
  }

  @Test
  @DisplayName(
      "Sample of the Lohnsteuerbescheinigung 2023, print mark 3.22: the same lines, read for 2023")
  void theLayoutOf2023ReadsACertificateFor2023() {
    CertificateText text =
        new CertificateText(PAGE.replace("für 2022", "für 2023"), LEFT, right(SOCIAL));

    CertificateValues values = WageTaxCertificate2023.LAYOUT.read(text);

    assertThat(values.found())
        .containsEntry("employedFrom", "01.03.2023")
        .containsEntry("employedTo", "31.12.2023")
        .containsEntry("grossWage", "30.000,00")
        .containsEntry("pensionEmployer", "2.890,00");
  }

  @Test
  void theLayoutOf2023ReadsNothingFromACertificateFor2022() {
    CertificateValues values =
        WageTaxCertificate2023.LAYOUT.read(new CertificateText(PAGE, LEFT, right(SOCIAL)));

    assertThat(values.found()).isEmpty();
  }

  @Test
  void aFieldWithoutAnAmountIsNotFound() {
    CertificateValues values = read(right(SOCIAL));

    assertThat(values.valueOrNotFound("solidarity")).isEqualTo("nicht gefunden");
    assertThat(values.valueOrNotFound("healthEmployee")).isEqualTo("nicht gefunden");
    assertThat(values.valueOrNotFound("healthAllowanceStatutory")).isEqualTo("nicht gefunden");
  }

  @Test
  void anAmountOfTheLeftColumnIsNotTakenForANumberedLine() {
    assertThat(read(right(SOCIAL)).found()).doesNotContainValue("1.200,00");
  }

  @Test
  @DisplayName("Anlage N 2022, line 39: lines 17 and 18 go in two boxes, so they are not added")
  void commuteWithAmountsOnLines17And18IsNotFound() {
    String[] lines = SOCIAL.clone();
    for (int i = 0; i < lines.length; i++) {
      lines[i] = lines[i].replace("erster Tätigkeitsstätte", "erster Tätigkeitsstätte 80,00");
    }

    assertThat(read(right(lines)).value("employerCommute")).isEmpty();
  }

  @Test
  void commuteOnlyOnLine18IsRead() {
    String[] lines = SOCIAL.clone();
    for (int i = 0; i < lines.length; i++) {
      lines[i] =
          lines[i]
              .replace("auf die 300,00", "auf die")
              .replace("erster Tätigkeitsstätte", "erster Tätigkeitsstätte 80,00");
    }

    assertThat(read(right(lines)).value("employerCommute")).hasValue("80,00");
  }

  @Test
  void aNegativeAmountIsNotFound() {
    String text = right(SOCIAL).replace("4.321,00", "-4.321,00").replace("360,00", "360,00-");

    CertificateValues values = read(text);

    assertThat(values.value("wageTax")).isEmpty();
    assertThat(values.value("unemploymentEmployee")).isEmpty();
  }

  @Test
  void aNegativePartMakesTheSumNotFound() {
    assertThat(read(right(SOCIAL).replace(" 50,00", " -50,00")).value("pensionEmployee")).isEmpty();
  }

  @Test
  void aLineWithTwoAmountsIsNotFound() {
    String text = right(SOCIAL).replace("und 10.", "und 10. 1.000,00");

    assertThat(read(text).value("grossWage")).isEmpty();
  }

  @Test
  void aTextLineWithTwoAmountsIsNotFound() {
    String text = right(SOCIAL).replace("von 3. 4.321,00", "von 3. 1.000,00 4.321,00");

    assertThat(read(text).value("wageTax")).isEmpty();
  }

  @Test
  void aCertificateOfAnotherYearGivesNothing() {
    String page = PAGE.replace("für 2022", "für 2023");

    CertificateValues values =
        WageTaxCertificateReader2022.read(new CertificateText(page, LEFT, right(SOCIAL)));

    assertThat(values.found()).isEmpty();
  }

  @Test
  void aPeriodWithTheYearOfAnotherYearIsNotFound() {
    String text = right(SOCIAL).replace("01.03.-31.12.", "01.03.2021-31.12.2021");

    CertificateValues values = read(text);

    assertThat(values.value("employedFrom")).isEmpty();
    assertThat(values.value("employedTo")).isEmpty();
  }

  @Test
  void aTaxClassWithAFactorGivesTheClass() {
    String left = LEFT.replace("Steuerklasse/Faktor\n1", "Steuerklasse/Faktor\n4 / 0,950");

    CertificateValues values =
        WageTaxCertificateReader2022.read(new CertificateText(PAGE, left, right(SOCIAL)));

    assertThat(values.value("taxClass")).hasValue("4");
  }

  @Test
  void aDocumentWithoutTextGivesNotFoundForEachField() {
    CertificateValues values = WageTaxCertificateReader2022.read(CertificateText.EMPTY);

    for (String fieldId : WageTaxCertificate2022.LAYOUT.fieldIds()) {
      assertThat(values.valueOrNotFound(fieldId)).isEqualTo("nicht gefunden");
    }
  }
}
