// The check of the values of one certificate before they are saved. A button with
// data-document-check sends the values of its section to the server, and the server answers with a
// dialog: one row per field, with the value read from the certificate and an input. A row is marked
// where the input differs from the read value. "Übernehmen" copies the inputs into the fields of
// the page, and the autosave of fields.js saves each changed field. "Abbrechen" saves nothing.
(() => {
  const MARK = "Weicht vom Beleg ab";

  // The same value can be typed in more than one way: 1234,5 and 1.234,50, or 3.2.2022 and
  // 03.02.2022. Each form becomes one text before the comparison.
  function normalized(kind, text) {
    const value = text.trim();
    if (kind === "MONEY") {
      const match = /^(\d+),?(\d{0,2})$/.exec(value.replaceAll(".", ""));
      return match ? match[1].replace(/^0+(?=\d)/, "") + "," + match[2].padEnd(2, "0") : value;
    }
    if (kind === "DATE") {
      const match = /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/.exec(value);
      return match ? match[1].padStart(2, "0") + "." + match[2].padStart(2, "0") + "." + match[3] : value;
    }
    return value;
  }

  function mark(row) {
    const input = row.querySelector("input");
    const kind = row.dataset.kind;
    // A value that the app did not find has no data-read attribute.
    const read = row.dataset.read ?? "";
    const differs = normalized(kind, input.value) !== normalized(kind, read);
    row.classList.toggle("differs", differs);
    row.querySelector(".mark").textContent = differs ? MARK : "";
  }

  function confirm(dialog, form) {
    dialog.querySelectorAll("tr[data-field]").forEach((row) => {
      const control = form.querySelector(`[name="${CSS.escape(row.dataset.field)}"]`);
      const value = row.querySelector("input").value.trim();
      if (control && control.value !== value) {
        control.value = value;
        control.dispatchEvent(new Event("change", { bubbles: true }));
      }
    });
    dialog.close();
  }

  async function open(button) {
    const form = button.closest("form");
    const body = new URLSearchParams();
    button.closest(".field-group").querySelectorAll(".fields [name]").forEach((control) => {
      body.append(control.name, control.value);
    });
    const response = await fetch(button.dataset.documentCheck, { method: "POST", body });
    if (!response.ok) {
      return;
    }
    document.body.insertAdjacentHTML("beforeend", await response.text());
    const dialog = document.body.lastElementChild;
    dialog.querySelectorAll("tr[data-field]").forEach(mark);
    dialog.addEventListener("input", (event) => mark(event.target.closest("tr")));
    dialog.addEventListener("change", (event) => mark(event.target.closest("tr")));
    dialog.querySelector("[data-check-confirm]").addEventListener("click", () => confirm(dialog, form));
    dialog.querySelector("[data-check-cancel]").addEventListener("click", () => dialog.close());
    // The Escape key closes the dialog too, like "Abbrechen".
    dialog.addEventListener("close", () => dialog.remove());
    dialog.showModal();
  }

  document.addEventListener("click", (event) => {
    const button = event.target.closest?.("[data-document-check]");
    if (button) {
      open(button);
    }
  });
})();
