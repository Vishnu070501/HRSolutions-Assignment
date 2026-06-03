# MSME Credit Pipeline Scheduler

This repository contains the implementation, unit tests, and assessment report for the **ScoreMe MSME Credit Pipeline Scheduling Assignment**.

The project implements the **SLA-SDG-BT** (SLA-Priority Saturation-Degree Greedy with Backtracking) algorithm to solve a multi-dimensional, conflict-constrained credit pipeline task scheduling problem.

---

## 📂 Project Structure

- `Scheduler.java` - Core scheduling engine in Java 17+ containing the JSON parser/writer, custom penalty calculation, feasibility verification, and the SLA-SDG-BT backtracking heuristic solver.
- `SchedulerTest.java` - Java unit test suite verifying mandatory edge cases (single task, all-conflict graph, zero capacity slots, tight SLA windows).
- `report.md` - Formal design report containing mathematical proofs, algorithm pseudocode, approximation proofs, and the Design Journal.
- `penalty_vs_n.png` - Performance chart illustrating heuristic penalty scaling vs task count n.
- `runtime_vs_n.png` - Performance chart illustrating scheduler execution runtime (in milliseconds, on a logarithmic scale) vs task count n.
- `.gitignore` - Standard git exclusions.

---

## ⚙️ Setup Instructions

### Prerequisites
- Java 17+ (JDK) installed and configured on the path.

---

## 🚀 Running the Project

### 1. Run Java Unit Tests
Compile and run the standalone Java test suite:
```bash
java SchedulerTest.java
```
All four required edge-case test suites (single-task, complete graph conflicts, zero capacity slot, tight SLA windows) will run and self-verify using assertions.

### 2. Run Custom Instance Solver
You can execute the Java scheduler directly on a JSON instance file:
```bash
java Scheduler.java --input path/to/input.json --output path/to/output.json [--beta 5.0] [--brute]
```
- `--input`: Path to the input JSON file matching the instance format.
- `--output`: Path to save the output JSON file.
- `--beta`: (Optional) Proximity penalty scaling factor (defaults to 5.0).
- `--brute`: (Optional) Flag to run the brute-force optimal solver instead of the heuristic solver.

---

## 📝 Submitting Your Work
- The formal mathematical and system design report is available in **[report.md](report.md)**. Review the Design Journal section to personalize it.
- Ready for the **Viva Voce** exam prep, including whiteboard walkthroughs and hand-tracing examples, as detailed in Section 4 of the report.
