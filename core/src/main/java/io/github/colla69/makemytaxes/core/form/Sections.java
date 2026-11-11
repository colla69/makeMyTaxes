package io.github.colla69.makemytaxes.core.form;

import static io.github.colla69.makemytaxes.core.form.Field.optional;
import static io.github.colla69.makemytaxes.core.form.Field.required;

import io.github.colla69.makemytaxes.core.GermanFormats;
import io.github.colla69.makemytaxes.core.year.YearPack;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The sections of the forms. A section holds what does not depend on the year: the fields, their
 * labels, their kinds and their rules. A {@link YearDefinition} picks the sections of each form and gives the
 * lines.
 */
public final class Sections {

  private static final String GENERAL_TITLE = "Allgemeine Angaben";
  private static final String WORK_TITLE = "Werbungskosten";
  private static final String PRIVATE_TITLE = "Private Kranken- und Pflegeversicherung";

  /** Hauptvordruck: the tax office and the person. */
  public static final Section GENERAL =
      Section.once(
          optional("taxNumber", GENERAL_TITLE, "", "Steuernummer", Kind.TEXT)
              .withHelp(
                  "Lassen Sie das Feld leer, wenn dies Ihre erste Erklärung ist und Sie keine"
                      + " Steuernummer haben."),
          required("taxOffice", GENERAL_TITLE, "", "An das Finanzamt", Kind.TEXT),
          required("taxId", GENERAL_TITLE, "", "Identifikationsnummer (IdNr.)", Kind.TAX_ID),
          required("birthDate", GENERAL_TITLE, "", "Geburtsdatum", Kind.DATE),
          required("lastName", GENERAL_TITLE, "", "Name", Kind.TEXT),
          required("firstName", GENERAL_TITLE, "", "Vorname", Kind.TEXT),
          required("street", GENERAL_TITLE, "", "Straße", Kind.TEXT),
          required("houseNumber", GENERAL_TITLE, "", "Hausnummer", Kind.TEXT),
          required("postalCode", GENERAL_TITLE, "", "Postleitzahl", Kind.TEXT),
          required("city", GENERAL_TITLE, "", "Wohnort", Kind.TEXT),
          required("occupation", GENERAL_TITLE, "", "Ausgeübter Beruf", Kind.TEXT));

  /** Hauptvordruck: the bank account for a refund. */
  public static final Section BANK =
      Section.once(required("iban", "Bankverbindung", "", "IBAN", Kind.IBAN));

  /** Hauptvordruck: benefits that replace income. */
  public static final Section BENEFITS =
      Section.once(
          optional(
                  "benefits",
                  "Einkommensersatzleistungen",
                  "",
                  "Einkommensersatzleistungen (EUR)",
                  Kind.MONEY)
              .withHelp("Zum Beispiel Arbeitslosengeld, Elterngeld oder Krankengeld."));

  /** Hauptvordruck: notes for the tax office. */
  public static final Section NOTES =
      Section.once(
          optional(
                  "notes",
                  "Ergänzende Angaben zur Steuererklärung",
                  "",
                  "Ergänzende Angaben",
                  Kind.TEXT)
              .withHelp("Angaben für das Finanzamt, für die der Vordruck kein anderes Feld hat."));

  /**
   * Anlage N: the wage values of one Lohnsteuerbescheinigung. The name and the period have no line
   * on Anlage N.
   */
  public static final Section EMPLOYER =
      Section.perEmployer(
              required("employerName", "", "", "Name des Arbeitgebers", Kind.TEXT),
              optional("employedFrom", "", "", "Dauer des Dienstverhältnisses: vom", Kind.DATE),
              optional("employedTo", "", "", "Dauer des Dienstverhältnisses: bis", Kind.DATE),
              required("taxClass", "", "", "Steuerklasse", Kind.WHOLE_NUMBER),
              required("grossWage", "", "", "Bruttoarbeitslohn (EUR)", Kind.MONEY),
              required("wageTax", "", "", "Lohnsteuer (EUR)", Kind.MONEY),
              optional("solidarity", "", "", "Solidaritätszuschlag (EUR)", Kind.MONEY),
              optional("churchTax", "", "", "Kirchensteuer des Arbeitnehmers (EUR)", Kind.MONEY),
              optional(
                  "shortTimeWork",
                  "",
                  "",
                  "Kurzarbeitergeld, Zuschuss zum Mutterschaftsgeld, Verdienstausfallentschädigung,"
                      + " Aufstockungsbetrag (EUR)",
                  Kind.MONEY),
              optional("employerCommute", "", "", "Arbeitgeberleistungen (EUR)", Kind.MONEY)
                  .withHelp(
                      "Die Summe der steuerfreien und der pauschal besteuerten"
                          + " Arbeitgeberleistungen für Fahrten zur Arbeit."))
          .withCheck(Sections::checkPeriod);

  /** Anlage N of 2022: the workplace, the way to work and the home office. */
  public static final Section WORK = work("", HomeOfficeDay.help(2022));

  /**
   * Anlage N from 2023, for a year without known lines: the same fields, with the home office days
   * of § 4(5) no. 6c EStG.
   */
  public static final Section WORK_FROM_2023 = work("", HomeOfficeDay.help(2023));

  /**
   * Anlage N 2024, line 61: the home office days of § 4(5) sentence 1 no. 6c sentence 1 EStG,
   * versions 30.06.2024 and 31.12.2024. A day counts if the work was mostly at home and the first
   * place of work was not visited. The exception of sentence 2, for a person who permanently has no
   * other workplace, is line 62 of Anlage N 2024, which the app does not have yet. Sentence 3 rules
   * out the day allowance next to a deduction under no. 6b (line 60) and for a home whose costs
   * count under no. 6a or § 9(1) sentence 3 no. 5 (double household). Lines 34 to 36
   * split the distance by the means of transport (2024AnlN032NET).
   */
  public static final Section WORK_2024 =
      work(
          "Tragen Sie die Kilometer in die Zeile des Verkehrsmittels ein: mit eigenem oder"
              + " überlassenem Kraftwagen in Anlage N Zeile 34, mit einer Sammelbeförderung des"
              + " Arbeitgebers in Zeile 35, mit öffentlichen Verkehrsmitteln, Motorrad, Fahrrad oder"
              + " Ähnlichem, zu Fuß oder als Mitfahrer einer Fahrgemeinschaft in Zeile 36. Haben"
              + " Sie mehrere Verkehrsmittel genutzt, teilen Sie die"
              + " Kilometer auf diese Zeilen auf.",
          "Nur wenn Ihnen ein anderer Arbeitsplatz zur Verfügung steht (Zeile 61): Tage, an"
              + " denen Sie ganz oder überwiegend zu Hause gearbeitet und keine erste"
              + " Tätigkeitsstätte aufgesucht haben. Steht Ihnen dauerhaft kein anderer"
              + " Arbeitsplatz zur Verfügung, gilt Zeile 62, die die App noch nicht unterstützt."
              + " Das Finanzamt berechnet den Betrag. Ziehen Sie ein häusliches Arbeitszimmer ab"
              + " (Zeile 60), gibt es für denselben Zeitraum keine Tagespauschale. Das gilt auch,"
              + " soweit Sie für diese Wohnung Unterkunftskosten einer doppelten Haushaltsführung"
              + " abziehen können.");

  /**
   * Anlage N 2025, line 58: the home office day of § 4(5) no. 6c EStG, versions 30.06.2025 and
   * 31.12.2025. The work is done mostly at home, and the first place of work is not visited. The
   * days of sentence 2, for a person with no other permanent place of work, go on line 59, which
   * the app does not cover. Sentence 3 rules out the day allowance next to a deduction under no. 6b.
   * Lines 31 to 33 split the distance by the means of transport (2025AnlN032NET).
   */
  public static final Section WORK_2025 =
      work(
          "Tragen Sie die Kilometer in die Zeile des Verkehrsmittels ein: mit eigenem oder"
              + " überlassenem Kraftwagen in Anlage N Zeile 31, mit öffentlichen Verkehrsmitteln,"
              + " Fahrrad, zu Fuß oder Carsharing in Zeile 33. Haben Sie mehrere Verkehrsmittel"
              + " genutzt, teilen Sie die Kilometer auf diese Zeilen auf.",
          "Tage, an denen Sie überwiegend zu Hause gearbeitet und Ihre erste Tätigkeitsstätte nicht"
              + " aufgesucht haben (Anlage N Zeile 58). Das Finanzamt berechnet den Betrag. Steht"
              + " Ihnen dauerhaft kein anderer Arbeitsplatz zur Verfügung, gehören die Tage in"
              + " Zeile 59, auch wenn Sie am selben Tag auswärts oder an der ersten"
              + " Tätigkeitsstätte gearbeitet haben. Zeile 59 deckt die App nicht ab. Ziehen Sie"
              + " ein häusliches Arbeitszimmer ab (Zeile 57), gibt es für denselben Zeitraum keine"
              + " Tagespauschale.");

  /**
   * Anlage N 2023, line 61: the home office day of § 4(5) no. 6c sentence 1 EStG, versions
   * 30.06.2023 and 31.12.2023, which § 52(6) sentence 12 EStG applies from 2023. The days of
   * sentence 2, for a person with no other permanent place of work, go on line 62, which the app
   * does not cover. Lines 34 to 36 split the distance by the means of transport (2023AnlN032NET).
   */
  public static final Section WORK_2023 =
      work(
          "Tragen Sie die Kilometer in die Zeile des Verkehrsmittels ein: mit eigenem oder zur"
              + " Nutzung überlassenem PKW in Anlage N Zeile 34, mit öffentlichen Verkehrsmitteln,"
              + " Motorrad, Fahrrad, zu Fuß oder als Mitfahrer in Zeile 36. Haben Sie mehrere"
              + " Verkehrsmittel genutzt, teilen Sie die Kilometer auf diese Zeilen auf.",
          "Tage, an denen Sie ganz oder überwiegend zu Hause gearbeitet und Ihre erste"
              + " Tätigkeitsstätte nicht aufgesucht haben (Anlage N Zeile 61). Das Finanzamt"
              + " berechnet den Betrag. Steht Ihnen dauerhaft kein anderer Arbeitsplatz zur"
              + " Verfügung, gehören die Tage in Zeile 62, auch wenn Sie am selben Tag auswärts"
              + " oder an der ersten Tätigkeitsstätte gearbeitet haben. Zeile 62 deckt die App"
              + " nicht ab. Es gibt keine Tagespauschale, soweit Sie ein häusliches"
              + " Arbeitszimmer abziehen (Zeile 60).");

  private static Section work(String transportHelp, String homeOfficeHelp) {
    return Section.once(
              optional("workplace", WORK_TITLE, "", "Erste Tätigkeitsstätte", Kind.TEXT)
                  .withHelp("Postleitzahl, Ort und Straße."),
              optional(
                  "commuteDays",
                  WORK_TITLE,
                  "",
                  "Wege zur Arbeit: aufgesucht an Tagen",
                  Kind.WHOLE_NUMBER),
              optional(
                      "commuteKm",
                      WORK_TITLE,
                      "",
                      "Wege zur Arbeit: einfache Entfernung (km)",
                      Kind.WHOLE_NUMBER)
                  .withHelp("Auf volle Kilometer abgerundet."),
              optional(
                      "commuteTransport",
                      WORK_TITLE,
                      "",
                      "Wege zur Arbeit: zurückgelegt mit",
                      Kind.COMMUTE_TRANSPORT)
                  .withHelp(transportHelp),
              optional(
                      "homeOfficeDays",
                      WORK_TITLE,
                      "",
                      "Homeoffice: Kalendertage",
                      Kind.WHOLE_NUMBER)
                  .withHelp(homeOfficeHelp))
        .withCheck(Sections::checkWork);
  }

  /**
   * Anlage Vorsorgeaufwand: the social insurance values of one Lohnsteuerbescheinigung, in the
   * order of the certificate.
   */
  public static final Section SOCIAL_INSURANCE =
      Section.perEmployer(
          money(
              "pensionEmployer",
              "Arbeitgeberanteil zur Rentenversicherung und an berufsständische"
                  + " Versorgungseinrichtungen (EUR)"),
          money(
              "pensionEmployee",
              "Arbeitnehmeranteil zur Rentenversicherung und an berufsständische"
                  + " Versorgungseinrichtungen (EUR)"),
          money(
              "healthAllowanceStatutory",
              "Steuerfreie Arbeitgeberzuschüsse zur gesetzlichen Krankenversicherung (EUR)"),
          money(
              "healthAllowancePrivate",
              "Steuerfreie Arbeitgeberzuschüsse zur privaten Krankenversicherung (EUR)"),
          money(
              "careAllowance",
              "Steuerfreie Arbeitgeberzuschüsse zur gesetzlichen Pflegeversicherung (EUR)"),
          money("healthEmployee", "Arbeitnehmerbeiträge zur gesetzlichen Krankenversicherung (EUR)"),
          money("careEmployee", "Arbeitnehmerbeiträge zur sozialen Pflegeversicherung (EUR)"),
          money("unemploymentEmployee", "Arbeitnehmerbeiträge zur Arbeitslosenversicherung (EUR)"));

  /** Anlage Vorsorgeaufwand: the values of the certificate of the private health insurer. */
  public static final Section PRIVATE_HEALTH =
      Section.once(
          optional(
                  "privateHealthBasic",
                  PRIVATE_TITLE,
                  "",
                  "Beiträge zur privaten Krankenversicherung, Basisabsicherung (EUR)",
                  Kind.MONEY)
              .withHelp("Der Betrag für die Basisabsicherung laut Bescheinigung des Versicherers."),
          optional(
              "privateCare",
              PRIVATE_TITLE,
              "",
              "Beiträge zur privaten Pflege-Pflichtversicherung (EUR)",
              Kind.MONEY),
          optional(
              "privateRefunds",
              PRIVATE_TITLE,
              "",
              "Erstattete Beiträge zur Kranken- und Pflegeversicherung (EUR)",
              Kind.MONEY));

  private Sections() {}

  /**
   * True if the value of this field comes from the Lohnsteuerbescheinigung. A test checks that
   * these IDs are exactly the fields of the certificate of each year.
   */
  public static boolean isOnCertificate(String fieldId) {
    return certificateFieldIds().contains(fieldId);
  }

  /** The IDs of the fields whose value comes from the Lohnsteuerbescheinigung. */
  static List<String> certificateFieldIds() {
    return Stream.of(EMPLOYER, SOCIAL_INSURANCE)
        .flatMap(section -> section.fields().stream())
        .map(Field::id)
        .toList();
  }

  /** The certificate of a year covers days of that year only, and the start comes first. */
  private static List<String> checkPeriod(Answers answers, int employer, YearPack pack) {
    Optional<LocalDate> from = dateOf(answers, AnlageN.employerFieldId(employer, "employedFrom"));
    Optional<LocalDate> to = dateOf(answers, AnlageN.employerFieldId(employer, "employedTo"));
    int year = pack.taxYear().value();
    for (Optional<LocalDate> date : List.of(from, to)) {
      if (date.isPresent() && date.get().getYear() != year) {
        return List.of(
            "Arbeitgeber %d: Die Dauer des Dienstverhältnisses muss in %d liegen."
                .formatted(employer, year));
      }
    }
    if (from.isPresent() && to.isPresent() && from.get().isAfter(to.get())) {
      return List.of(
          "Arbeitgeber %d: Das Ende des Dienstverhältnisses liegt vor dem Beginn."
              .formatted(employer));
    }
    return List.of();
  }

  /** A day cannot be both, because the home office amount excludes days at the workplace. */
  private static List<String> checkWork(Answers answers, int employer, YearPack pack) {
    List<String> messages = new ArrayList<>();
    long days = answers.wholeNumber("commuteDays") + answers.wholeNumber("homeOfficeDays");
    if (days > pack.daysInYear()) {
      messages.add(
          "Tage mit Wegen zur Arbeit und Homeoffice-Tage ergeben zusammen %d, aber %d hat nur %d Tage."
              .formatted(days, pack.taxYear().value(), pack.daysInYear()));
    }
    // The limit of § 9(1) no. 4 EStG depends on the transport, so the amount needs this answer.
    if (answers.has("commuteDays") && !answers.has("commuteTransport")) {
      messages.add("Wählen Sie, womit Sie zur Arbeit gefahren sind. Davon hängt der Höchstbetrag ab.");
    }
    return messages;
  }

  private static Optional<LocalDate> dateOf(Answers answers, String fieldId) {
    return answers.has(fieldId)
        ? Optional.of(GermanFormats.parseDate(answers.text(fieldId)))
        : Optional.empty();
  }

  /** The fields of the general section that belong to these IDs, in the order of the section. */
  static List<Field> generalFields(List<String> fieldIds) {
    return GENERAL.fields().stream().filter(field -> fieldIds.contains(field.id())).toList();
  }

  private static Field money(String id, String label) {
    return optional(id, "", "", label, Kind.MONEY);
  }
}
