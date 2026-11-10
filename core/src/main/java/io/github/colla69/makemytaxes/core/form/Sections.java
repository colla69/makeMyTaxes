package io.github.colla69.makemytaxes.core.form;

import static io.github.colla69.makemytaxes.core.form.Field.optional;
import static io.github.colla69.makemytaxes.core.form.Field.required;

import java.util.List;
import java.util.stream.Stream;

/**
 * The sections of the forms. A section holds what does not depend on the year: the fields, their
 * labels and their kinds. A {@link YearDefinition} picks the sections of each form and gives the
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
              .withHelp("Die Summe der Beträge in Zeile 17 und Zeile 18."));

  /** Anlage N: the workplace, the way to work and the home office. */
  public static final Section WORK =
      Section.once(
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
          optional("homeOfficeDays", WORK_TITLE, "", "Homeoffice: Kalendertage", Kind.WHOLE_NUMBER)
              .withHelp(
                  "Tage, an denen Sie nur zu Hause gearbeitet haben. Das Finanzamt berechnet den"
                      + " Betrag."));

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

  /** True if the value of this field comes from the Lohnsteuerbescheinigung. */
  public static boolean isOnCertificate(String fieldId) {
    return Stream.of(EMPLOYER, SOCIAL_INSURANCE)
        .flatMap(section -> section.fields().stream())
        .anyMatch(field -> field.id().equals(fieldId));
  }

  /** The fields of the general section that belong to these IDs, in the order of the section. */
  static List<Field> generalFields(List<String> fieldIds) {
    return GENERAL.fields().stream().filter(field -> fieldIds.contains(field.id())).toList();
  }

  private static Field money(String id, String label) {
    return optional(id, "", "", label, Kind.MONEY);
  }
}
