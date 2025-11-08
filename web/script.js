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
  const indicator = document.getElementById("status-indicator");
  const statusText = document.getElementById("status-text");

  if (connected) {
    indicator.classList.remove("inactive");
    statusText.textContent = "Connected";
  } else {
    indicator.classList.add("inactive");
    statusText.textContent = "Connection Lost";
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
    noPoll.style.display = "block";
    pollContent.style.display = "none";
    return;
  }

  // Show poll content
  noPoll.style.display = "none";
  pollContent.style.display = "block";

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
    correctAnswerDiv.style.display = "block";
    document.getElementById("correct-choice").textContent = data.correct;
  } else {
    correctAnswerDiv.style.display = "none";
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
    container.innerHTML = "<p>No options available</p>";
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
    const correctClass = isCorrect ? "correct" : "";

    html += `
            <div class="option-item ${correctClass}">
                <div class="option-header">
                    <div class="option-label">${escapeHtml(option)}</div>
                    <div class="option-count">${count} vote${
      count !== 1 ? "s" : ""
    }</div>
                </div>
                <div class="progress-bar">
                    <div class="progress-fill" style="width: ${percentage}%">
                        ${
                          percentage > 10
                            ? `<span class="progress-percentage">${percentage.toFixed(
                                1
                              )}%</span>`
                            : ""
                        }
                    </div>
                    ${
                      percentage <= 10 && percentage > 0
                        ? `<span class="progress-percentage-outside">${percentage.toFixed(
                            1
                          )}%</span>`
                        : ""
                    }
                </div>
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