package io.github.colla69.makemytaxes.core.form;

import static io.github.colla69.makemytaxes.core.form.Field.optional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Anlage Vorsorgeaufwand: the social insurance values of each Lohnsteuerbescheinigung and the
 * values of the certificate of a private health insurer. The app shows the values only. It does
 * not compute the deductible amount and does not apply a limit.
 *
 * <p>The fields have no line of the form yet. Each label names the number on its certificate.
 */
public final class AnlageVorsorgeaufwand implements FormModule {

  public static final String ID = "anlage-vorsorgeaufwand";

  private static final String PRIVATE = "Private Kranken- und Pflegeversicherung";

  public static final Field PENSION_EMPLOYER =
      optional(
          "pensionEmployer",
          "",
          "",
          "Nr. 22a/b Arbeitgeberanteil zur Rentenversicherung (EUR)",
          Kind.MONEY);
  public static final Field PENSION_EMPLOYEE =
      optional(
          "pensionEmployee",
          "",
          "",
          "Nr. 23a/b Arbeitnehmeranteil zur Rentenversicherung (EUR)",
          Kind.MONEY);
  public static final Field HEALTH_ALLOWANCE_STATUTORY =
      optional(
          "healthAllowanceStatutory",
          "",
          "",
          "Nr. 24a Steuerfreie Arbeitgeberzuschüsse zur gesetzlichen Krankenversicherung (EUR)",
          Kind.MONEY);
  public static final Field HEALTH_ALLOWANCE_PRIVATE =
      optional(
          "healthAllowancePrivate",
          "",
          "",
          "Nr. 24b Steuerfreie Arbeitgeberzuschüsse zur privaten Krankenversicherung (EUR)",
          Kind.MONEY);
  public static final Field CARE_ALLOWANCE =
      optional(
          "careAllowance",
          "",
          "",
          "Nr. 24c Steuerfreie Arbeitgeberzuschüsse zur gesetzlichen Pflegeversicherung (EUR)",
          Kind.MONEY);
  public static final Field HEALTH_EMPLOYEE =
      optional(
          "healthEmployee",
          "",
          "",
          "Nr. 25 Arbeitnehmerbeiträge zur gesetzlichen Krankenversicherung (EUR)",
          Kind.MONEY);
  public static final Field CARE_EMPLOYEE =
      optional(
          "careEmployee",
          "",
          "",
          "Nr. 26 Arbeitnehmerbeiträge zur sozialen Pflegeversicherung (EUR)",
          Kind.MONEY);
  public static final Field UNEMPLOYMENT_EMPLOYEE =
      optional(
          "unemploymentEmployee",
          "",
          "",
          "Nr. 27 Arbeitnehmerbeiträge zur Arbeitslosenversicherung (EUR)",
          Kind.MONEY);

  /** The social insurance lines of one certificate, in the order of the certificate. */
  private static final List<Field> EMPLOYER_FIELDS =
      List.of(
          PENSION_EMPLOYER,
          PENSION_EMPLOYEE,
          HEALTH_ALLOWANCE_STATUTORY,
          HEALTH_ALLOWANCE_PRIVATE,
          CARE_ALLOWANCE,
          HEALTH_EMPLOYEE,
          CARE_EMPLOYEE,
          UNEMPLOYMENT_EMPLOYEE);

  /** The lines for an employee with a statutory health insurance. */
  public static final List<Field> STATUTORY_EMPLOYER_FIELDS =
      List.of(
          PENSION_EMPLOYER,
          PENSION_EMPLOYEE,
          HEALTH_ALLOWANCE_STATUTORY,
          CARE_ALLOWANCE,
          HEALTH_EMPLOYEE,
          CARE_EMPLOYEE,
          UNEMPLOYMENT_EMPLOYEE);

  /** The lines for an employee with a private health insurance. */
  public static final List<Field> PRIVATE_EMPLOYER_FIELDS =
      List.of(PENSION_EMPLOYER, PENSION_EMPLOYEE, HEALTH_ALLOWANCE_PRIVATE, UNEMPLOYMENT_EMPLOYEE);

  /** The values of the certificate of the private health insurer. */
  public static final List<Field> PRIVATE_FIELDS =
      List.of(
          optional(
                  "privateHealthBasic",
                  PRIVATE,
                  "",
                  "Beiträge zur privaten Krankenversicherung, Basisabsicherung (EUR)",
                  Kind.MONEY)
              .withHelp("Der Betrag für die Basisabsicherung laut Bescheinigung des Versicherers."),
          optional(
              "privateCare",
              PRIVATE,
              "",
              "Beiträge zur privaten Pflege-Pflichtversicherung (EUR)",
              Kind.MONEY),
          optional(
              "privateRefunds",
              PRIVATE,
              "",
              "Erstattete Beiträge zur Kranken- und Pflegeversicherung (EUR)",
              Kind.MONEY));

  private static final Pattern EMPLOYER_ID = Pattern.compile("employer(\\d+)-(.+)");

  /**
   * The answers without the lines that the other kind of health insurance has. After a change of
   * the kind, an old amount must not stay on the review, where the user copies it into Mein ELSTER.
   */
  public static Answers forHealthInsurance(Answers answers, boolean isPrivate) {
    List<Field> kept = new ArrayList<>();
    if (isPrivate) {
      kept.addAll(PRIVATE_EMPLOYER_FIELDS);
      kept.addAll(PRIVATE_FIELDS);
    } else {
      kept.addAll(STATUTORY_EMPLOYER_FIELDS);
    }
    Set<String> keptIds = kept.stream().map(Field::id).collect(Collectors.toSet());
    Map<String, String> values = new LinkedHashMap<>();
    answers
        .values()
        .forEach(
            (fieldId, value) -> {
              Matcher matcher = EMPLOYER_ID.matcher(fieldId);
              if (keptIds.contains(matcher.matches() ? matcher.group(2) : fieldId)) {
                values.put(fieldId, value);
              }
            });
    return new Answers(values);
  }

  /** The fields of one employer, with the IDs and the numbers of the employers of Anlage N. */
  private static List<Field> employerFields(int employer) {
    return EMPLOYER_FIELDS.stream()
        .map(
            field ->
                field.inEntry(
                    AnlageN.employerFieldId(employer, field.id()), "Arbeitgeber " + employer))
        .toList();
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return "Anlage Vorsorgeaufwand";
  }

  @Override
  public List<Field> fields() {
    return fields(new Answers(Map.of()));
  }

  /** One block for each employer with answers, then the private health insurance. */
  @Override
  public List<Field> fields(Answers answers) {
    List<Field> fields = new ArrayList<>();
    for (int employer = 1; employer <= AnlageN.employerCount(answers); employer++) {
      fields.addAll(employerFields(employer));
    }
    fields.addAll(PRIVATE_FIELDS);
    return fields;
  }

  @Override
  public Optional<Field> field(String fieldId) {
    Matcher matcher = EMPLOYER_ID.matcher(fieldId);
    List<Field> candidates =
        matcher.matches() ? employerFields(Integer.parseInt(matcher.group(1))) : fields();
    return candidates.stream().filter(field -> field.id().equals(fieldId)).findFirst();
  }
}
