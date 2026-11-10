package io.github.colla69.makemytaxes.core.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One form in one year: its sections in the order of the form, and the line of each field.
 *
 * @param lines the line on the form by field ID. Anlage N also has the line of each expense topic,
 *     by the name of the topic.
 * @param certificate the Lohnsteuerbescheinigung of the year. A label of a field from the
 *     certificate starts with its line there.
 */
public record FormLayout(
    List<Section> sections, Map<String, String> lines, WageTaxCertificate certificate) {

  private static final Pattern EMPLOYER_ID = Pattern.compile("employer(\\d+)-(.+)");

  /** The line of a field or an expense topic, or an empty text if it has none. */
  public String line(String key) {
    return lines.getOrDefault(key, "");
  }

  /** The fields of each section in order. A section per employer comes once for each employer. */
  public List<Field> fields(int employers) {
    List<Field> fields = new ArrayList<>();
    for (Section section : sections) {
      if (section.perEmployer()) {
        for (int employer = 1; employer <= employers; employer++) {
          fields.addAll(employerFields(section, employer));
        }
      } else {
        section.fields().forEach(field -> fields.add(ofYear(field)));
      }
    }
    return fields;
  }

  /** The field with this ID, also in an employer block that has no answer yet. */
  public Optional<Field> field(String fieldId) {
    Matcher matcher = EMPLOYER_ID.matcher(fieldId);
    int employers = matcher.matches() ? Integer.parseInt(matcher.group(1)) : 1;
    return fields(employers).stream().filter(field -> field.id().equals(fieldId)).findFirst();
  }

  private List<Field> employerFields(Section section, int employer) {
    return section.fields().stream()
        .map(
            field ->
                ofYear(field)
                    .inEntry(
                        AnlageN.employerFieldId(employer, field.id()), "Arbeitgeber " + employer))
        .toList();
  }

  private Field ofYear(Field field) {
    return new Field(
        field.id(),
        field.section(),
        line(field.id()),
        certificate.label(field.id(), field.label()),
        field.kind(),
        field.required(),
        field.help());
  }
}
