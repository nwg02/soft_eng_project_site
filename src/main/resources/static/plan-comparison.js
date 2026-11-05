"use strict";

// GISU 2027 plan explorer: filtering and side-by-side comparisons.
// Runs after the HTML has been parsed (the script tag uses defer).
document.addEventListener("DOMContentLoaded", function () {
  const cards = Array.from(document.querySelectorAll(".market-plan"));
  const grid = document.getElementById("market-grid");
  const search = document.getElementById("plan-search");
  const provider = document.getElementById("provider-filter");
  const tier = document.getElementById("tier-filter");
  const count = document.getElementById("plan-count");
  const noResults = document.getElementById("no-results");
  const actions = document.getElementById("comparison-actions");
  const selectedCount = document.getElementById("selection-count");
  const selectionHint = document.getElementById("selection-hint");
  const showComparison = document.getElementById("show-comparison");
  const clearSelected = document.getElementById("clear-selected");
  const clearBottom = document.getElementById("clear-compare");
  const comparisonSection = document.getElementById("compare-section");
  const status = document.getElementById("compare-status");
  const tableWrap = document.getElementById("compare-table-wrap");
  const tableHead = document.getElementById("compare-head");
  const tableBody = document.getElementById("compare-body");

  if (!grid || !actions || !showComparison || !comparisonSection || !tableWrap) {
    return;
  }

  function selectedCards() {
    return cards.filter(function (card) {
      const checkbox = card.querySelector(".compare-plan");
      return checkbox && checkbox.checked;
    });
  }

  function updateFilters() {
    const q = search.value.trim().toLowerCase();
    let shown = 0;

    cards.forEach(function (card) {
      const terms = [card.dataset.name, card.dataset.provider, card.dataset.type]
        .join(" ").toLowerCase();
      const visible = (provider.value === "all" || card.dataset.provider === provider.value)
        && (tier.value === "all" || card.dataset.tier === tier.value)
        && terms.includes(q);
      card.hidden = !visible;
      if (visible) shown += 1;
    });

    count.textContent = "Showing " + shown + " of " + cards.length + " plans";
    noResults.hidden = shown !== 0;
  }

  function appendCell(row, tag, value, scope) {
    const cell = document.createElement(tag);
    cell.textContent = value;
    if (scope) cell.scope = scope;
    row.appendChild(cell);
    return cell;
  }

  function buildComparison(chosen) {
    tableHead.replaceChildren();
    tableBody.replaceChildren();

    if (chosen.length < 2) {
      tableWrap.hidden = true;
      return;
    }

    const heading = document.createElement("tr");
    appendCell(heading, "th", "Plan feature", "col");
    chosen.forEach(function (card) {
      appendCell(heading, "th", card.dataset.name, "col");
    });
    tableHead.appendChild(heading);

    const rows = [
      ["Insurance company", "provider"],
      ["Metal level", "tier"],
      ["Plan type", "type"],
      ["Deductible in plan name*", "deductible"],
      ["Monthly premium", "premium"]
    ];

    rows.forEach(function (rowData) {
      const row = document.createElement("tr");
      appendCell(row, "th", rowData[0], "row");
      chosen.forEach(function (card) {
        let value = rowData[1] === "premium" ? "Individual quote required" : card.dataset[rowData[1]];
        if (rowData[1] === "deductible") {
          value = "$" + Number(value).toLocaleString("en-US");
        }
        appendCell(row, "td", value || "Not listed");
      });
      tableBody.appendChild(row);
    });

    const linkRow = document.createElement("tr");
    appendCell(linkRow, "th", "Official plan document", "row");
    chosen.forEach(function (card) {
      const cell = document.createElement("td");
      const link = card.querySelector(".market-link");
      if (link) {
        const a = document.createElement("a");
        a.href = link.href;
        a.target = "_blank";
        a.rel = "noopener noreferrer";
        a.textContent = "View plan details ↗";
        cell.appendChild(a);
      } else {
        cell.textContent = "Not available";
      }
      linkRow.appendChild(cell);
    });
    tableBody.appendChild(linkRow);
    tableWrap.hidden = false;
  }

  function updateComparison(message) {
    const chosen = selectedCards();
    const number = chosen.length;

    actions.hidden = number === 0;
    selectedCount.textContent = number + (number === 1 ? " plan selected" : " plans selected");
    selectionHint.textContent = number < 2
      ? "Choose at least one more plan."
      : "Your side-by-side comparison is ready.";
    showComparison.disabled = number < 2;
    showComparison.textContent = "Compare " + number + (number === 1 ? " plan" : " plans");

    buildComparison(chosen);

    if (message) {
      status.textContent = message;
    } else if (number === 0) {
      status.textContent = "Select two or three plans above to see their comparison here.";
    } else if (number === 1) {
      status.textContent = "One plan selected. Select one or two more plans to compare.";
    } else {
      status.textContent = number + " plans selected. Comparison is shown below.";
    }
  }

  function clearSelection() {
    cards.forEach(function (card) {
      const checkbox = card.querySelector(".compare-plan");
      if (checkbox) checkbox.checked = false;
    });
    updateComparison("Comparison cleared. No plans selected.");
  }

  grid.addEventListener("change", function (event) {
    if (!event.target.matches(".compare-plan")) return;
    const chosen = selectedCards();
    if (chosen.length > 3) {
      event.target.checked = false;
      updateComparison("You can compare a maximum of three plans.");
      return;
    }
    updateComparison();
    if (chosen.length === 2 && event.target.checked) {
      comparisonSection.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  });

  showComparison.addEventListener("click", function () {
    if (selectedCards().length >= 2) {
      comparisonSection.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  });
  clearSelected.addEventListener("click", clearSelection);
  clearBottom.addEventListener("click", clearSelection);
  search.addEventListener("input", updateFilters);
  provider.addEventListener("change", updateFilters);
  tier.addEventListener("change", updateFilters);
  document.getElementById("clear-filters").addEventListener("click", function () {
    search.value = "";
    provider.value = "all";
    tier.value = "all";
    updateFilters();
  });

  updateFilters();
  updateComparison();
});
