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

  /** Hauptvordruck from 2024: the same fields, with the occupation (line 12) before the address. */
  public static final Section GENERAL_OCCUPATION_FIRST =
      new Section(
          false,
          Stream.of(
                  "taxNumber",
                  "taxOffice",
                  "taxId",
                  "birthDate",
                  "lastName",
                  "firstName",
                  "occupation",
                  "street",
                  "houseNumber",
                  "postalCode",
                  "city")
              .flatMap(id -> GENERAL.fields().stream().filter(field -> field.id().equals(id)))
              .toList(),
          Section.Check.NONE);

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
                  .withHelp("Die Summe der Beträge in Zeile 17 und Zeile 18."))
          .withCheck(Sections::checkPeriod);

  /**
   * Anlage N: the workplace, the way to work and the home office, with the home office days of §
   * 4(5) sentence 1 no. 6b sentence 4 EStG, version 30.06.2022, with § 52(6) sentence 15: days of
   * work only at home.
   */
  public static final Section WORK =
      work(
          "Tage, an denen Sie nur zu Hause gearbeitet haben. Das Finanzamt berechnet den"
              + " Betrag.");

  /**
   * Anlage N from 2023: the home office days of § 4(5) sentence 1 no. 6c sentence 1 EStG, versions
   * 30.06.2024 and 31.12.2024. A day counts if the work was mostly at home and the first place of
   * work was not visited. The exception of sentence 2, for a person without another workplace, is
   * line 62 of Anlage N 2024, which the app does not have yet.
   */
  public static final Section WORK_FROM_2023 =
      work(
          "Tage, an denen Sie ganz oder überwiegend zu Hause gearbeitet und keine erste"
              + " Tätigkeitsstätte aufgesucht haben. Das Finanzamt berechnet den Betrag.");

  private static Section work(String homeOfficeHelp) {
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
                  Kind.COMMUTE_TRANSPORT),
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
