// Keeps the input of each form while the user switches tabs, and clears fields on request.
//
// The input stays in sessionStorage, so it stays only in this browser tab, and it goes when the
// tab closes. Each form has its own entry, with the path of the page and the id of the form.
//
// A page that the server shows after a save, with a success or an error, holds the current truth.
// Then the script stores the values of that page. On other pages, the script puts the stored
// input back into the fields.
(function () {
  "use strict";

  function storageKey(form) {
    return "draft:" + window.location.pathname + "#" + form.id;
  }

  function controls(form) {
    return form.querySelectorAll("input[name]:not([type=hidden]), select[name], textarea[name]");
  }

  function store(form) {
    const values = {};
    controls(form).forEach(function (control) {
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
    controls(form).forEach(function (control) {
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

  document.addEventListener("input", function (event) {
    const form = event.target.closest("form[data-remember]");
    if (form) {
      store(form);
    }
  });

  document.addEventListener("click", function (event) {
    const fieldButton = event.target.closest("[data-clear-field]");
    if (fieldButton) {
      const control = fieldButton.closest(".field").querySelector("input[name], select[name]");
      clear(control);
      control.focus();
      return;
    }
    const formButton = event.target.closest("[data-clear-form]");
    if (formButton) {
      controls(formButton.closest("form")).forEach(clear);
    }
  });

  document.addEventListener("DOMContentLoaded", function () {
    const fromServer = showsAServerResult();
    rememberedForms(document).forEach(function (form) {
      if (fromServer) {
        store(form);
      } else {
        restore(form);
      }
    });
  });

  // htmx puts the answer of the server in place. That answer holds the current truth.
  document.addEventListener("htmx:afterSwap", function (event) {
    rememberedForms(event.detail.elt.ownerDocument).forEach(store);
  });
})();
