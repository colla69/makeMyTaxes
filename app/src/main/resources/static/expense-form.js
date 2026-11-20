// Shows "Davon erstattet" only for the topic that has a refund, Krankheitskosten. The server renders the
// field visible, so that it works without JavaScript. The field is in a fieldset that is disabled while
// hidden, so the form does not send it. The receipt check has its own topic, so its topic decides while
// it is open.
document.addEventListener("change", (event) => {
  if (event.target.matches("#expense-form select[name=topic]")) {
    showRefundFor(event.target);
  }
});

// htmx sends this event for the page on load and for each swapped part, such as the panel after a
// refused save. The topic of the page goes first, so that an open receipt check decides last.
document.addEventListener("htmx:load", (event) => {
  event.detail.elt
    .querySelectorAll("#expense-fields select[name=topic], dialog[data-receipt-check] select[name=topic]")
    .forEach(showRefundFor);
});

function showRefundFor(topic) {
  const refund = topic.form.querySelector("#refund");
  const shown = topic.value === refund.dataset.topic;
  refund.hidden = !shown;
  refund.disabled = !shown;
}
