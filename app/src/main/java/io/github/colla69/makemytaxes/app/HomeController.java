package io.github.colla69.makemytaxes.app;

import io.github.colla69.makemytaxes.app.assistant.PersonAssistant;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.MainForm;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
class HomeController {

  private static final List<Field> PERSONAL_FIELDS = MainForm.personalFields();

  /** The years that the start page offers as a button for each person. */
  private static final List<Integer> YEARS = List.of(2022, 2023, 2024, 2025, 2026);

  private final HouseholdStore household;
  private final PersonAssistant assistant;

  HomeController(HouseholdStore household, PersonAssistant assistant) {
    this.household = household;
    this.assistant = assistant;
  }

  @GetMapping("/")
  String home(Model model) {
    fillHome(model, Map.of(), Map.of());
    return "home";
  }

  @PostMapping("/persons")
  String addPerson(@RequestParam Map<String, String> submitted, Model model) {
    Map<String, String> details = details(submitted);
    if (displayName(details).isEmpty()) {
      return "redirect:/";
    }
    Map<String, String> errors = errors(details);
    for (String nameField : MainForm.NAME_FIELDS) {
      if (!details.containsKey(nameField)) {
        errors.put(nameField, "Bitte ausfüllen.");
      }
    }
    if (!errors.isEmpty()) {
      fillHome(model, details, errors);
      return "home";
    }
    household.addPerson(displayName(details), new Answers(details));
    return "redirect:/";
  }

  @GetMapping("/persons/{personId}")
  String person(
      @PathVariable long personId,
      @RequestParam(defaultValue = "false") boolean saved,
      Model model) {
    fillPerson(personId, household.detailsOf(personId).values(), Map.of(), model);
    model.addAttribute("saved", saved);
    return "person";
  }

  @PostMapping("/persons/{personId}")
  String savePerson(
      @PathVariable long personId, @RequestParam Map<String, String> submitted, Model model) {
    Map<String, String> details = details(submitted);
    Map<String, String> errors = errors(details);
    if (!errors.isEmpty()) {
      fillPerson(personId, details, errors, model);
      model.addAttribute("saved", false);
      return "person";
    }
    household.saveDetails(personId, new Answers(details));
    // A person from before the name fields keeps its name until the user enters both names here.
    if (details.keySet().containsAll(MainForm.NAME_FIELDS)) {
      household.rename(personId, displayName(details));
    }
    return "redirect:/persons/" + personId + "?saved=true";
  }

  /** Saves one detail when the user leaves its field. The answer is empty, or it holds the error. */
  @PostMapping("/persons/{personId}/field")
  @ResponseBody
  ResponseEntity<String> saveDetail(
      @PathVariable long personId,
      @RequestParam String field,
      @RequestParam(defaultValue = "") String value) {
    if (!household.hasPerson(personId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    Field target =
        PERSONAL_FIELDS.stream()
            .filter(candidate -> candidate.id().equals(field))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String text = value.strip();
    if (!text.isEmpty()) {
      try {
        target.kind().validate(text);
      } catch (IllegalArgumentException e) {
        return ResponseEntity.status(422).body(e.getMessage());
      }
    }
    household.saveDetail(personId, field, text);
    Map<String, String> details = household.detailsOf(personId).values();
    if (details.keySet().containsAll(MainForm.NAME_FIELDS)) {
      household.rename(personId, displayName(details));
    }
    return ResponseEntity.ok("");
  }

  /** Turns the assistant on or off for the returns of this person. */
  @PostMapping("/persons/{personId}/assistant")
  String switchAssistant(@PathVariable long personId, @RequestParam boolean on) {
    if (!household.hasPerson(personId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    household.switchAssistant(personId, on);
    return "redirect:/persons/" + personId;
  }

  @PostMapping("/persons/{personId}/returns")
  String addReturn(@PathVariable long personId, @RequestParam int taxYear) {
    if (!household.hasPerson(personId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    long returnId;
    try {
      returnId = household.addReturn(personId, new TaxYear(taxYear));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    return "redirect:/returns/" + returnId;
  }

  private void fillHome(Model model, Map<String, String> values, Map<String, String> errors) {
    model.addAttribute("persons", household.persons());
    model.addAttribute("years", YEARS);
    model.addAttribute("personalFields", PERSONAL_FIELDS);
    model.addAttribute("requiredFields", MainForm.NAME_FIELDS);
    model.addAttribute("values", values);
    model.addAttribute("errors", errors);
  }

  private void fillPerson(
      long personId, Map<String, String> values, Map<String, String> errors, Model model) {
    String name =
        household
            .nameOf(personId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    model.addAttribute("personId", personId);
    model.addAttribute("personName", name);
    model.addAttribute("personalFields", PERSONAL_FIELDS);
    model.addAttribute("requiredFields", List.of());
    model.addAttribute("values", values);
    model.addAttribute("errors", errors);
    model.addAttribute("assistantOn", household.assistantOn(personId));
    model.addAttribute("assistantAvailable", assistant.available());
  }

  /** The name that the app shows: first name, then surname. Empty if the user entered neither. */
  private static String displayName(Map<String, String> details) {
    return (details.getOrDefault("firstName", "") + " " + details.getOrDefault("lastName", ""))
        .strip();
  }

  /** The personal details that the user entered. A blank detail is not in the map. */
  private static Map<String, String> details(Map<String, String> submitted) {
    Map<String, String> details = new LinkedHashMap<>();
    for (Field field : PERSONAL_FIELDS) {
      String value = submitted.getOrDefault(field.id(), "").strip();
      if (!value.isEmpty()) {
        details.put(field.id(), value);
      }
    }
    return details;
  }

  private static Map<String, String> errors(Map<String, String> details) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (Field field : PERSONAL_FIELDS) {
      if (details.containsKey(field.id())) {
        try {
          field.kind().validate(details.get(field.id()));
        } catch (IllegalArgumentException e) {
          errors.put(field.id(), e.getMessage());
        }
      }
    }
    return errors;
  }
}
