// Input masks for the fields of fields.html. They only help with typing: the server still parses
// and checks every value. The listeners sit on the document, so they also work on parts of the
// page that htmx swaps in. The same file keeps a draft of the input and runs the clear buttons,
// because all of this works on the fields of fields.html.
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

  // The draft keeps the input of each form while the user switches tabs.
  //
  // The input stays in sessionStorage, so it stays only in this browser tab. Each form has its own
  // entry, with the path of the page and the id of the form.
  //
  // A page that the server shows after a save, with a success or an error, holds the current truth.
  // Then the script stores the values of that page. On other pages, the script puts the stored
  // input back into the fields.
  function storageKey(form) {
    return "draft:" + window.location.pathname + "#" + form.id;
  }

  function controls(form) {
    return form.querySelectorAll("input[name]:not([type=hidden]), select[name], textarea[name]");
  }

  function store(form) {
    const values = {};
    controls(form).forEach((control) => {
      values[control.name] = control.value;
    });
    sessionStorage.setItem(storageKey(form), JSON.stringify(values));
  }

  function restore(form) {
    const stored = sessionStorage.getItem(storageKey(form));
    if (stored === null) {
      return;
    }
    const values = JSON.parse(stored);
    controls(form).forEach((control) => {
      if (Object.prototype.hasOwnProperty.call(values, control.name)) {
        control.value = values[control.name];
      }
    });
  }

  // A select goes back to its first option, so that it always has a value. On a form page, the
  // first option is the blank "Bitte wählen".
  function clear(control) {
    if (control.tagName === "SELECT") {
      control.selectedIndex = 0;
    } else {
      control.value = "";
    }
    control.dispatchEvent(new Event("input", { bubbles: true }));
  }

  function rememberedForms(root) {
    return root.querySelectorAll("form[data-remember]");
  }

  function showsAServerResult() {
    return document.querySelector("main .saved, main .error") !== null;
  }

  // The draft also listens to "change", because the amount format and the calendar change a value
  // without an "input" event. These listeners come after the mask listeners, so they store the
  // masked value.
  function storeDraft(event) {
    const form = event.target.closest?.("form[data-remember]");
    if (form) {
      store(form);
    }
  }

  document.addEventListener("input", storeDraft);
  document.addEventListener("change", storeDraft);

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

  function storeOrRestore() {
    const fromServer = showsAServerResult();
    rememberedForms(document).forEach((form) => {
      if (fromServer) {
        store(form);
      } else {
        restore(form);
      }
    });
  }

  // A save on a page with data-clear-drafts changes values that the forms of other pages show,
  // such as the personal details in the main form. Then all drafts are old.
  function clearDraftsAfterASave() {
    if (document.querySelector("main .saved") === null
        || document.querySelector("[data-clear-drafts]") === null) {
      return;
    }
    Object.keys(sessionStorage)
      .filter((key) => key.startsWith("draft:"))
      .forEach((key) => sessionStorage.removeItem(key));
  }

  // The script has "defer", so the document is parsed when it runs.
  clearDraftsAfterASave();
  storeOrRestore();

  // htmx puts the answer of the server in place. That answer holds the current truth.
  document.addEventListener("htmx:afterSwap", () => {
    rememberedForms(document).forEach(store);
  });
})();
