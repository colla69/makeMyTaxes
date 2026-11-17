package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.document.CertificatePdfReader;
import io.github.colla69.makemytaxes.app.storage.DocumentStore;
import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.filing.FilingCheck;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.CertificateEvidence;
import io.github.colla69.makemytaxes.core.form.CertificateValues;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FilingCheckForm;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import io.github.colla69.makemytaxes.core.form.Interview;
import io.github.colla69.makemytaxes.core.form.WageTaxCertificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class FormController {

  /**
   * The fields of one section. {@code documentId} is the certificate beside them, or null, and
   * {@code documentHref} its address.
   */
  record FieldGroup(String title, List<Field> fields, Long documentId, String documentHref) {}

  /**
   * One field in the check against a certificate. {@code read} is the value that the app read from
   * the certificate, or empty if it did not find one. {@code input} is the value on the page, or
   * the read value if the page has none. {@code readText} is what the dialog shows for the read
   * value: the value, "leer auf der Bescheinigung" or "nicht gefunden".
   */
  record CheckRow(
      String fieldId, String label, String certificateLine, String kind, String read,
      String readText, String input) {}

  private final ReturnYears years;
  private final FormStore forms;
  private final DocumentStore documents;

  FormController(ReturnYears years, FormStore forms, DocumentStore documents) {
    this.years = years;
    this.forms = forms;
    this.documents = documents;
  }

  @GetMapping("/returns/{returnId}/forms/{formId}")
  String form(
      @PathVariable long returnId,
      @PathVariable String formId,
      @RequestParam(defaultValue = "false") boolean saved,
      Model model) {
    FormModule form = years.formOf(returnId, formId);
    fillForm(returnId, form, forms.answers(returnId, formId).values(), Map.of(), List.of(), model);
    model.addAttribute("saved", saved);
    return "form";
  }

  @PostMapping("/returns/{returnId}/forms/{formId}")
  String save(
      @PathVariable long returnId,
      @PathVariable String formId,
      @RequestParam Map<String, String> submitted,
      Model model) {
    FormModule form = years.formOf(returnId, formId);
    Map<String, String> values = new LinkedHashMap<>();
    Map<String, String> errors = new LinkedHashMap<>();
    // The submitted names tell which list entries, such as employers, the page had.
    for (Field field : form.fields(new Answers(submitted))) {
      String value = submitted.getOrDefault(field.id(), "").strip();
      if (value.isEmpty()) {
        continue;
      }
      values.put(field.id(), value);
      try {
        field.kind().validate(value);
      } catch (IllegalArgumentException e) {
        errors.put(field.id(), e.getMessage());
      }
    }
    // A value that fails its own check cannot go into a check of several fields.
    List<String> formErrors = errors.isEmpty() ? form.check(new Answers(values)) : List.of();
    if (!errors.isEmpty() || !formErrors.isEmpty()) {
      fillForm(returnId, form, values, errors, formErrors, model);
      model.addAttribute("saved", false);
      return "form";
    }
    forms.save(returnId, formId, new Answers(values));
    return "redirect:/returns/" + returnId + "/forms/" + formId + "?saved=true";
  }

  /**
   * Saves one field when the user leaves it. A value that fails its own check is not saved, and
   * the answer holds the error. Otherwise the answer holds the messages of the checks of several
   * fields, one per line. They do not stop the save, because the other value of such a check can
   * still be missing.
   */
  @PostMapping("/returns/{returnId}/forms/{formId}/field")
  @ResponseBody
  ResponseEntity<String> saveField(
      @PathVariable long returnId,
      @PathVariable String formId,
      @RequestParam String field,
      @RequestParam(defaultValue = "") String value) {
    FormModule form = years.formOf(returnId, formId);
    Field target = form.field(field).orElseThrow(ReturnYears::notFound);
    String text = value.strip();
    if (!text.isEmpty()) {
      try {
        target.kind().validate(text);
      } catch (IllegalArgumentException e) {
        return ResponseEntity.status(422).body(e.getMessage());
      }
    }
    forms.saveAnswer(returnId, formId, field, text);
    return ResponseEntity.ok(String.join("\n", form.check(forms.answers(returnId, formId))));
  }

  /**
   * The dialog that checks the values of the fields from one certificate against the values that
   * the app reads from it. The page sends the values in its fields. This saves nothing: the page
   * saves the values after the user confirms them in the dialog.
   */
  @PostMapping("/returns/{returnId}/forms/{formId}/document-check")
  String documentCheck(
      @PathVariable long returnId,
      @PathVariable String formId,
      @RequestParam long document,
      @RequestParam Map<String, String> submitted,
      Model model) {
    FormModule form = years.formOf(returnId, formId);
    documents.find(returnId, document).orElseThrow(ReturnYears::notFound);
    WageTaxCertificate certificate = years.definitionOf(returnId).certificate();
    CertificateValues read = CertificatePdfReader.read(documents.content(document), certificate);
    Answers interview = forms.answers(returnId, Interview.ID);
    List<CheckRow> rows = new ArrayList<>();
    for (Field field : form.fields(forms.answers(returnId, formId))) {
      OptionalLong source = CertificateEvidence.certificateDocument(interview, field.id());
      if (source.isPresent() && source.getAsLong() == document) {
        // A value that the app did not read stays empty: the app never suggests a value of its own.
        String certificateId = field.hintId();
        String readValue = read.value(certificateId).orElse("");
        String onPage = submitted.getOrDefault(field.id(), "").strip();
        rows.add(
            new CheckRow(
                field.id(),
                field.label(),
                certificate.lineOf(certificateId).orElse("ohne Zeile"),
                field.kind().name(),
                readValue,
                read.readText(certificateId),
                onPage.isEmpty() ? readValue : onPage));
      }
    }
    model.addAttribute("rows", rows);
    return "document-check :: dialog";
  }

  private void fillForm(
      long returnId,
      FormModule form,
      Map<String, String> values,
      Map<String, String> errors,
      List<String> formErrors,
      Model model) {
    TaxYear taxYear = years.taxYearOf(returnId);
    model.addAttribute("returnId", returnId);
    model.addAttribute("tab", form.id());
    model.addAttribute("taxYear", taxYear.value());
    model.addAttribute("forms", Forms.of(taxYear));
    model.addAttribute("linesKnown", years.definitionOf(returnId).linesKnown());
    model.addAttribute("form", form);
    model.addAttribute("fieldGroups", fieldGroups(returnId, form.fields(new Answers(values))));
    model.addAttribute("values", values);
    model.addAttribute("errors", errors);
    model.addAttribute("formErrors", formErrors);
    model.addAttribute("missing", form.missing(forms.answers(returnId, form.id())));
    if (form.id().equals(FilingCheckForm.ID) && errors.isEmpty()) {
      model.addAttribute(
          "filing",
          FilingView.of(
              FilingCheck.of(
                  ReturnYears.packOf(taxYear), forms.answers(returnId, FilingCheckForm.ID))));
    }
  }

  private List<FieldGroup> fieldGroups(long returnId, List<Field> fields) {
    Answers interview = forms.answers(returnId, Interview.ID);
    Map<String, List<Field>> bySection = new LinkedHashMap<>();
    for (Field field : fields) {
      bySection.computeIfAbsent(field.section(), section -> new ArrayList<>()).add(field);
    }
    return bySection.entrySet().stream()
        .map(
            entry -> {
              OptionalLong document =
                  CertificateEvidence.certificateDocument(interview, entry.getValue().getFirst().id());
              return document.isPresent()
                  ? new FieldGroup(
                      entry.getKey(),
                      entry.getValue(),
                      document.getAsLong(),
                      "/returns/" + returnId + "/documents/" + document.getAsLong())
                  : new FieldGroup(entry.getKey(), entry.getValue(), null, null);
            })
        .toList();
  }
}
