"use strict";

// GISU help widget. The API key stays on the server, never in this script.
(function () {
  function init() {
    const toggle = document.getElementById("chat-toggle");
    const close = document.getElementById("chat-close");
    const panel = document.getElementById("chat-panel");
    const form = document.getElementById("chat-form");
    const input = document.getElementById("chat-input");
    const sendButton = document.getElementById("chat-send");
    const messages = document.getElementById("chat-messages");
    const mode = document.getElementById("chat-mode");
    if (!toggle || !panel || !form || !messages) return;

    function appendMessage(text, cssClass) {
      const div = document.createElement("div");
      div.className = "chat-message " + cssClass;
      div.textContent = text;
      messages.appendChild(div);
      messages.scrollTop = messages.scrollHeight;
      return div;
    }

    function setOpen(isOpen) {
      panel.hidden = !isOpen;
      toggle.setAttribute("aria-expanded", String(isOpen));
      toggle.setAttribute("aria-label", isOpen ? "Hide insurance help chat" : "Open insurance help chat");
      if (isOpen) input.focus();
    }
    toggle.addEventListener("click", function () { setOpen(panel.hidden); });
    close.addEventListener("click", function () { setOpen(false); });

    function modeLabel(value) {
      if (value === "gemini") return "Gemini AI enabled";
      if (value === "openai" || value === "ai") return "AI answers enabled";
      return "Built-in insurance guide";
    }

    fetch("/api/chat/status", { credentials: "same-origin" })
      .then(function (response) { if (!response.ok) throw new Error("status"); return response.json(); })
      .then(function (result) {
        mode.textContent = modeLabel(result.mode);
      })
      .catch(function () { mode.textContent = "Built-in insurance guide"; });

    async function askQuestion(question) {
      const message = question.trim();
      if (!message) return;
      appendMessage(message, "chat-user");
      input.value = "";
      input.disabled = true;
      sendButton.disabled = true;
      const pending = appendMessage("Thinking…", "chat-assistant chat-pending");

      try {
        const response = await fetch("/api/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "same-origin",
          body: JSON.stringify({ message: message })
        });
        const result = await response.json();
        pending.textContent = result.answer || "I couldn't answer that right now.";
        mode.textContent = modeLabel(result.mode);
      } catch (err) {
        pending.textContent = "The chat service is unavailable right now. Please try again.";
      } finally {
        pending.classList.remove("chat-pending");
        input.disabled = false;
        sendButton.disabled = false;
        input.focus();
        messages.scrollTop = messages.scrollHeight;
      }
    }

    form.addEventListener("submit", function (event) {
      event.preventDefault();
      if (!input.disabled) askQuestion(input.value);
    });
    document.querySelectorAll("[data-chat-question]").forEach(function (button) {
      button.addEventListener("click", function () { askQuestion(button.dataset.chatQuestion); });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
