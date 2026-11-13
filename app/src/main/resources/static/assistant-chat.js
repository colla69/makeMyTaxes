// The chat column shows the newest message at the bottom: after the page loads and after htmx
// swaps in a new question or answer, the messages scroll to the end.
(function () {
  function scrollToNewest() {
    var messages = document.querySelector("#assistant-chat .assistant-scroll");
    if (messages) {
      messages.scrollTop = messages.scrollHeight;
    }
  }

  scrollToNewest();
  document.body.addEventListener("htmx:afterSettle", function (event) {
    var target = event.detail.target;
    if (target && target.id === "assistant-conversation") {
      scrollToNewest();
    }
  });
})();
