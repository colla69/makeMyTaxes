package io.github.colla69.makemytaxes.core.form;

import static io.github.colla69.makemytaxes.core.form.Field.optional;
import static io.github.colla69.makemytaxes.core.form.Field.required;

import java.util.List;

/** Hauptvordruck ESt 1 A 2022. */
public final class MainForm implements FormModule {

  private static final String GENERAL = "Allgemeine Angaben";
  private static final String BANK = "Bankverbindung";
  private static final String BENEFITS = "Einkommensersatzleistungen";
  private static final String NOTES = "Ergänzende Angaben zur Steuererklärung";

  @Override
  public String id() {
    return "main";
  }

  @Override
  public String title() {
    return "Main form (ESt 1 A)";
  }

  @Override
  public List<Field> fields() {
    return List.of(
        optional("taxNumber", GENERAL, "4", "Steuernummer", Kind.TEXT)
            .withHelp("If this is your first return and you have no tax number, leave this blank."),
        required("taxOffice", GENERAL, "5", "An das Finanzamt", Kind.TEXT),
        required("taxId", GENERAL, "8", "Identifikationsnummer (IdNr.)", Kind.TAX_ID),
        required("birthDate", GENERAL, "8", "Geburtsdatum", Kind.DATE),
        required("lastName", GENERAL, "9", "Name", Kind.TEXT),
        required("firstName", GENERAL, "10", "Vorname", Kind.TEXT),
        required("street", GENERAL, "12", "Straße", Kind.TEXT),
        required("houseNumber", GENERAL, "13", "Hausnummer", Kind.TEXT),
        required("postalCode", GENERAL, "14", "Postleitzahl", Kind.TEXT),
        required("city", GENERAL, "15", "Wohnort", Kind.TEXT),
        required("occupation", GENERAL, "17", "Ausgeübter Beruf", Kind.TEXT),
        required("iban", BANK, "31", "IBAN", Kind.IBAN),
        optional("benefits", BENEFITS, "43", "Einkommensersatzleistungen (EUR)", Kind.MONEY)
            .withHelp("For example Arbeitslosengeld, Elterngeld or Krankengeld."),
        optional("notes", NOTES, "45", "Ergänzende Angaben", Kind.TEXT)
            .withHelp("Facts for the tax office that have no other field on the form."));
  }
}
