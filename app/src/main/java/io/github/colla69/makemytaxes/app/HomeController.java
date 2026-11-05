package io.github.colla69.makemytaxes.app;

import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.core.TaxYear;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class HomeController {

  private final HouseholdStore household;

  HomeController(HouseholdStore household) {
    this.household = household;
  }

  @GetMapping("/")
  String home(Model model) {
    model.addAttribute("persons", household.persons());
    return "home";
  }

  @PostMapping("/persons")
  String addPerson(@RequestParam String name) {
    if (!name.isBlank()) {
      household.addPerson(name.strip());
    }
    return "redirect:/";
  }

  @PostMapping("/persons/{personId}/returns")
  String addReturn(@PathVariable long personId, @RequestParam int taxYear) {
    household.addReturn(personId, new TaxYear(taxYear));
    return "redirect:/";
  }
}
