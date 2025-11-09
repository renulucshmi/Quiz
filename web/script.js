// Remote Classroom Polling Dashboard - Client-side JavaScript

let lastPollId = null;

/**
 * Fetch and update poll statistics from the server.
 */
async function updateStats() {
  try {
    const response = await fetch("/stats");
    if (!response.ok) {
      throw new Error("Failed to fetch stats");
    }

    const data = await response.json();
    renderPollData(data);
    updateStatusIndicator(true);
  } catch (error) {
    console.error("Error fetching stats:", error);
    updateStatusIndicator(false);
  }
}

/**
 * Update the connection status indicator.
 */
function updateStatusIndicator(connected) {
  const statusText = document.getElementById("status-text");
  const statusDot = document.getElementById("status-dot");

  if (connected) {
    statusText.textContent = "Connected";
    statusDot.className =
      "w-3 h-3 rounded-full bg-emerald-400 animate-pulse-custom shadow-lg";
  } else {
    statusText.textContent = "Connection Lost";
    statusDot.className = "w-3 h-3 rounded-full bg-rose-400 shadow-lg";
  }
}

/**
 * Render poll data on the dashboard.
 */
function renderPollData(data) {
  const noPoll = document.getElementById("no-poll");
  const pollContent = document.getElementById("poll-content");

  // Check if there's an active poll
  if (!data.active && !data.question) {
    // No poll
    noPoll.classList.remove("hidden");
    pollContent.classList.add("hidden");
    return;
  }

  // Show poll content
  noPoll.classList.add("hidden");
  pollContent.classList.remove("hidden");

  // Update question
  document.getElementById("poll-question").textContent =
    data.question || "Loading...";

  // Update total votes
  const totalVotes = data.totalVotes || 0;
  document.getElementById("total-votes").textContent = `${totalVotes} vote${
    totalVotes !== 1 ? "s" : ""
  }`;

  // Render options
  renderOptions(data);

  // Show correct answer if revealed
  const correctAnswerDiv = document.getElementById("correct-answer");
  if (data.revealed && data.correct) {
    correctAnswerDiv.classList.remove("hidden");
    document.getElementById("correct-choice").textContent = data.correct;
  } else {
    correctAnswerDiv.classList.add("hidden");
  }

  // Check if poll changed
  if (data.pollId !== lastPollId) {
    lastPollId = data.pollId;
    console.log("New poll detected:", data.pollId);
  }
}

/**
 * Render poll options with vote counts and percentages.
 */
function renderOptions(data) {
  const container = document.getElementById("options-container");

  if (!data.options || data.options.length === 0) {
    container.innerHTML =
      '<p class="text-white/60 text-center">No options available</p>';
    return;
  }

  // Build options HTML
  let html = "";
  for (let i = 0; i < data.options.length; i++) {
    const option = data.options[i];
    const count = data.counts ? data.counts[i] : 0;
    const percentage = data.percentages ? data.percentages[i] : 0;
    const letter = String.fromCharCode(65 + i); // A, B, C, D

    const isCorrect = data.revealed && data.correct === letter;

    // Determine border and background color based on correctness
    let borderClass = "border-white/20";
    let bgClass = "glass-light";

    if (isCorrect) {
      borderClass = "border-emerald-400/50";
      bgClass = "glass-light bg-emerald-500/10";
    }

    html += `
      <div class="${bgClass} rounded-xl p-4 border ${borderClass} transition-all duration-300 hover:border-white/40">
        <div class="flex items-center justify-between mb-3">
          <div class="text-white font-medium text-lg">
            <span class="text-sky-400 font-semibold">${letter}.</span> ${escapeHtml(
      option
    )}
          </div>
          <div class="bg-white/10 px-3 py-1 rounded-full text-sm text-white/80">
            ${count} vote${count !== 1 ? "s" : ""}
          </div>
        </div>
        <div class="relative">
          <div class="h-3 bg-white/10 rounded-full overflow-hidden">
            <div class="progress-bar h-full bg-gradient-to-r from-blue-500 to-sky-400 rounded-full transition-all duration-600" style="width: ${percentage}%"></div>
          </div>
          ${
            percentage > 0
              ? `<div class="text-right mt-1 text-sm font-medium ${
                  isCorrect ? "text-emerald-400" : "text-sky-400"
                }">${percentage.toFixed(1)}%</div>`
              : ""
          }
        </div>
        ${
          isCorrect
            ? `
          <div class="mt-2 flex items-center gap-2 text-emerald-400 text-sm font-medium">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
            </svg>
            <span>Correct Answer</span>
          </div>
        `
            : ""
        }
      </div>
    `;
  }

  container.innerHTML = html;
}

/**
 * Escape HTML to prevent XSS.
 */
function escapeHtml(text) {
  const map = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;",
  };
  return text.replace(/[&<>"']/g, (m) => map[m]);
}

/**
 * Initialize the dashboard.
 */
function init() {
  console.log("Remote Classroom Polling Dashboard initialized");

  // Initial fetch
  updateStats();

  // Poll every 1 second
  setInterval(updateStats, 1000);
}

// Start when DOM is ready
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
