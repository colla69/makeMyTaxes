// Input masks for the fields of fields.html. They only help with typing: the server still parses
// and checks every value. The listeners sit on the document, so they also work on parts of the
// page that htmx swaps in. The same file saves each field when the user leaves it, and runs the
// clear buttons, because all of this works on the fields of fields.html.
(() => {
  // A date that the user types with dots, such as 3.2.2022, stays as it is.
  const DATE_WITH_DOTS = /^\d{1,2}(\.(\d{1,2}(\.\d{0,4})?)?)?$/;
  const PLAIN_AMOUNT = /^(\d+)(?:,(\d{0,2}))?$/;

  function maskDate(text) {
    if (DATE_WITH_DOTS.test(text)) {
      return text;
    }
    const digits = text.replace(/\D/g, "").slice(0, 8);
    return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4)].filter(Boolean).join(".");
  }

  // A number mask removes only characters that cannot be part of a number. It keeps every digit and
  // every separator, so a wrong value such as 12.50 or 12,5 reaches the server and fails its check,
  // instead of turning into another valid number.
  function maskNumber(text) {
    return text.replace(/[^\d.,]/g, "");
  }

  // 1234,5 becomes 1.234,50.
  function formatAmount(text) {
    const match = PLAIN_AMOUNT.exec(text);
    if (!match) {
      return text;
    }
    const whole = match[1].replace(/^0+(?=\d)/, "").replace(/\B(?=(\d{3})+$)/g, ".");
    return whole + "," + (match[2] || "").padEnd(2, "0");
  }

  const MASKS = {
    DATE: maskDate,
    MONEY: maskNumber,
    WHOLE_NUMBER: maskNumber,
  };

  // A mask acts only while the user types at the end. An edit in the middle or a deletion stays as
  // it is, so the mask cannot move digits into another part of a date. The server checks the result.
  function typesAtTheEnd(event) {
    const field = event.target;
    return !event.inputType?.startsWith("delete") && field.selectionStart === field.value.length;
  }

  document.addEventListener("input", (event) => {
    const mask = MASKS[event.target.dataset?.kind];
    if (mask && typesAtTheEnd(event)) {
      const masked = mask(event.target.value);
      if (masked !== event.target.value) {
        event.target.value = masked;
      }
    }
  });

  document.addEventListener("change", (event) => {
    if (event.target.dataset?.kind === "MONEY") {
      event.target.value = formatAmount(event.target.value);
    }
  });

  // One hidden date input opens the calendar of the browser and writes the chosen day as dd.mm.yyyy.
  let picker;
  let target;

  function calendarPicker() {
    if (!picker) {
      picker = document.createElement("input");
      picker.type = "date";
      picker.id = "calendar-picker";
      picker.tabIndex = -1;
      picker.setAttribute("aria-hidden", "true");
      picker.addEventListener("change", () => {
        const [year, month, day] = picker.value.split("-");
        if (target && day) {
          target.value = day + "." + month + "." + year;
          target.dispatchEvent(new Event("change", { bubbles: true }));
          target.focus();
        }
      });
      document.body.append(picker);
    }
    return picker;
  }

  function isoDate(text) {
    const match = /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/.exec(text);
    return match ? match[3] + "-" + match[2].padStart(2, "0") + "-" + match[1].padStart(2, "0") : "";
  }

  document.addEventListener("click", (event) => {
    const button = event.target.closest?.("button.calendar");
    if (!button) {
      return;
    }
    target = button.parentElement.querySelector("input");
    const box = button.getBoundingClientRect();
    const input = calendarPicker();
    input.style.left = box.left + "px";
    input.style.top = box.bottom + "px";
    input.value = isoDate(target.value);
    try {
      input.showPicker();
    } catch {
      input.focus();
    }
  });

  // Autosave: the database is the only store of a value. When the user leaves a changed field of a
  // form with data-autosave, the script sends the name and the value of the field to that address.
  // The server saves a valid value. For an invalid value it answers 422 with the error, and the
  // script shows the error at the field. The other fields still save.
  //
  // The saves go one after the other. A click on a link waits for the open saves, so that the next
  // page shows the saved values. "keepalive" lets a save finish when the page closes.
  let saves = Promise.resolve();
  let openSaves = 0;

  function controls(form) {
    return form.querySelectorAll("input[name]:not([type=hidden]), select[name], textarea[name]");
  }

  function showFieldError(control, message) {
    const field = control.closest(".field");
    let error = field.querySelector(":scope > p.error");
    if (!message) {
      error?.remove();
      return;
    }
    if (!error) {
      error = document.createElement("p");
      error.className = "error";
      field.append(error);
    }
    error.textContent = message;
  }

  // The checks of several fields. Only a form page has a place for them.
  function showFormMessages(text) {
    const place = document.getElementById("form-messages");
    if (!place) {
      return;
    }
    place.replaceChildren(
      ...text.split("\n").filter(Boolean).map((message) => {
        const paragraph = document.createElement("p");
        paragraph.className = "error";
        paragraph.textContent = message;
        return paragraph;
      }),
    );
  }

  async function save(address, control) {
    const body = new URLSearchParams({ field: control.name, value: control.value });
    try {
      const response = await fetch(address, { method: "POST", body, keepalive: true });
      const text = await response.text();
      if (response.status === 422) {
        showFieldError(control, text);
      } else if (response.ok) {
        showFieldError(control, "");
        showFormMessages(text);
      } else {
        showFieldError(control, "Nicht gespeichert. Bitte versuchen Sie es noch einmal.");
      }
    } catch {
      showFieldError(control, "Nicht gespeichert. Bitte versuchen Sie es noch einmal.");
    }
  }

  // This listener comes after the listener of the amount format, so it saves the formatted value.
  document.addEventListener("change", (event) => {
    const control = event.target;
    const form = control.closest?.("form[data-autosave]");
    if (!form || !control.name || control.type === "hidden") {
      return;
    }
    openSaves++;
    saves = saves
      .then(() => save(form.dataset.autosave, control))
      .finally(() => openSaves--);
  });

  document.addEventListener("click", (event) => {
    const link = event.target.closest?.("a[href]");
    const opensElsewhere = event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey
      || event.altKey || link?.target;
    if (link && openSaves > 0 && !event.defaultPrevented && !opensElsewhere) {
      event.preventDefault();
      saves.then(() => window.location.assign(link.href));
    }
  });

  // A select goes back to its first option, so that it always has a value. On a form page, the
  // first option is the blank "Bitte wählen". The "change" event saves the cleared field.
  function clear(control) {
    if (control.tagName === "SELECT") {
      control.selectedIndex = 0;
    } else {
      control.value = "";
    }
    control.dispatchEvent(new Event("input", { bubbles: true }));
    control.dispatchEvent(new Event("change", { bubbles: true }));
  }

  document.addEventListener("click", (event) => {
    const fieldButton = event.target.closest?.("[data-clear-field]");
    if (fieldButton) {
      const control = fieldButton.closest(".field").querySelector("input[name], select[name]");
      clear(control);
      control.focus();
      return;
    }
    const formButton = event.target.closest?.("[data-clear-form]");
    if (formButton) {
      controls(formButton.closest("form")).forEach(clear);
    }
  });
})();
