// Input masks for the fields of fields.html. They only help with typing: the server still parses
// and checks every value. The listeners sit on the document, so they also work on parts of the
// page that htmx swaps in.
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
})();
