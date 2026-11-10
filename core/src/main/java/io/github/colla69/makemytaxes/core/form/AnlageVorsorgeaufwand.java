package io.github.colla69.makemytaxes.core.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anlage Vorsorgeaufwand: the social insurance values of each Lohnsteuerbescheinigung and the
 * values of the certificate of a private health insurer. The app shows the values only. It does
 * not compute the deductible amount and does not apply a limit.
 *
 * <p>The fields have no line of the form yet. Each label names the line on its certificate.
 */
public final class AnlageVorsorgeaufwand implements FormModule {

  public static final String ID = "anlage-vorsorgeaufwand";

  /** The certificate lines for an employee with a statutory health insurance, by field ID. */
  public static final List<String> STATUTORY_EMPLOYER_FIELDS =
      List.of(
          "pensionEmployer",
          "pensionEmployee",
          "healthAllowanceStatutory",
          "careAllowance",
          "healthEmployee",
          "careEmployee",
          "unemploymentEmployee");

  /** The certificate lines for an employee with a private health insurance, by field ID. */
  public static final List<String> PRIVATE_EMPLOYER_FIELDS =
      List.of("pensionEmployer", "pensionEmployee", "healthAllowancePrivate", "unemploymentEmployee");

  private static final Pattern EMPLOYER_ID = Pattern.compile("employer(\\d+)-(.+)");

  private final FormLayout layout;

  public AnlageVorsorgeaufwand(FormLayout layout) {
    this.layout = layout;
  }

  /**
   * The answers without the lines that the other kind of health insurance has. After a change of
   * the kind, an old amount must not stay on the review, where the user copies it into Mein ELSTER.
   */
  public static Answers forHealthInsurance(Answers answers, boolean isPrivate) {
    List<String> kept = new ArrayList<>();
    if (isPrivate) {
      kept.addAll(PRIVATE_EMPLOYER_FIELDS);
      Sections.PRIVATE_HEALTH.fields().forEach(field -> kept.add(field.id()));
    } else {
      kept.addAll(STATUTORY_EMPLOYER_FIELDS);
    }
    Set<String> keptIds = Set.copyOf(kept);
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
    return layout.fields(AnlageN.employerCount(answers));
  }

  @Override
  public Optional<Field> field(String fieldId) {
    return layout.field(fieldId);
  }
}
