import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scheduler {

    // ==========================================
    // Data structures (Records)
    // ==========================================
    public record Instance(
        List<String> tasks,
        List<List<Integer>> conflicts,
        double[][] resources,
        double[][] capacities,
        int[][] windows,
        double[] weights,
        int K
    ) {}

    // ==========================================
    // Task 2: Custom Penalty Function P(sigma)
    // ==========================================
    public static double computePenalty(Map<String, Integer> assignment, Instance instance, double beta) {
        /*
         * custom penalty function for task 2.
         * basically, we want to compte the base penalty (delay) but also add
         * a quadratic penalty if we schedule stuff too close to the upper SLA deadline.
         * scheduling near the deadline is super risky in real life (network drops, spikes)
         * so this quadratic term penalizes that risk.
         */
        double penalty = 0.0;
        List<String> tasks = instance.tasks();
        double[] weights = instance.weights();
        int[][] windows = instance.windows();

        Map<String, Integer> taskToIdx = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            taskToIdx.put(tasks.get(i), i);
        }

        for (Map.Entry<String, Integer> entry : assignment.entrySet()) {
            String taskId = entry.getKey();
            int slot = entry.getValue();
            if (!taskToIdx.containsKey(taskId)) continue;

            int idx = taskToIdx.get(taskId);
            double w_i = weights[idx];
            int l_i = windows[idx][0];
            int u_i = windows[idx][1];

            // linear delay cost (priority * slot index)
            double base = w_i * slot;

            // quadratic proximity penalty to avoid schedulin right at deadline
            double proximity = 0.0;
            if (u_i > l_i) {
                proximity = Math.pow((double)(slot - l_i) / (u_i - l_i), 2);
            }

            penalty += base + beta * w_i * proximity;
        }
        return penalty;
    }

    // ==========================================
    // Task 3: Heuristic Solver (SLA-SDG-BT)
    // ==========================================
    public static class MrvScore implements Comparable<MrvScore> {
        int numFeasible;
        double negWeight;
        int windowSize;
        int negDegree;
        int taskIdx;

        public MrvScore(int numFeasible, double negWeight, int windowSize, int negDegree, int taskIdx) {
            this.numFeasible = numFeasible;
            this.negWeight = negWeight;
            this.windowSize = windowSize;
            this.negDegree = negDegree;
            this.taskIdx = taskIdx;
        }

        @Override
        public int compareTo(MrvScore o) {
            if (this.numFeasible != o.numFeasible) {
                return Integer.compare(this.numFeasible, o.numFeasible);
            }
            if (Double.compare(this.negWeight, o.negWeight) != 0) {
                return Double.compare(this.negWeight, o.negWeight);
            }
            if (this.windowSize != o.windowSize) {
                return Integer.compare(this.windowSize, o.windowSize);
            }
            if (this.negDegree != o.negDegree) {
                return Integer.compare(this.negDegree, o.negDegree);
            }
            return Integer.compare(this.taskIdx, o.taskIdx);
        }
    }

    public static class SolverState {
        public int stateCount = 0;
        public Map<Integer, Integer> assignment = new HashMap<>(); // task_idx -> slot
        public double[][] slotResources;
        public int maxStates;

        public SolverState(double[][] capacities, int maxStates) {
            this.maxStates = maxStates;
            int K = capacities.length;
            this.slotResources = new double[K][4];
            for (int s = 0; s < K; s++) {
                System.arraycopy(capacities[s], 0, this.slotResources[s], 0, 4);
            }
        }
    }

    public static boolean solveHeuristicRec(Instance instance, Set<Integer>[] adj, SolverState state) {
        state.stateCount++;
        if (state.stateCount > state.maxStates) {
            return false; // state limit failsafe to guarantee poly runtime
        }

        int n = instance.tasks().size();
        if (state.assignment.size() == n) {
            return true; // yay, all tasks scheduled!
        }

        // pick next task using MRV (minimum remaining values)
        int bestTask = -1;
        List<Integer> bestFeasibleSlots = null;
        MrvScore bestMrvScore = new MrvScore(Integer.MAX_VALUE, 0.0, 0, 0, 0);

        for (int i = 0; i < n; i++) {
            if (state.assignment.containsKey(i)) {
                continue;
            }

            int lo = instance.windows()[i][0];
            int hi = instance.windows()[i][1];
            List<Integer> feasibleSlots = new ArrayList<>();

            for (int s = lo; s <= hi; s++) {
                // constraint F1: conflct check
                boolean conflict = false;
                for (int neighbor : adj[i]) {
                    if (state.assignment.containsKey(neighbor) && state.assignment.get(neighbor) == s) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) continue;

                // constraint F2: resource capacity check
                boolean fits = true;
                for (int d = 0; d < 4; d++) {
                    if (instance.resources()[i][d] > state.slotResources[s][d] + 1e-9) {
                        fits = false;
                        break;
                    }
                }
                if (!fits) continue;

                feasibleSlots.add(s);
            }

            int numFeasible = feasibleSlots.size();

            // forward checkin: if some unassigned task has 0 slots left, prune immediately
            if (numFeasible == 0) {
                return false;
            }

            // tie break score: (fewest slots, highest weight, smallest window, highest conflict degre, index)
            MrvScore score = new MrvScore(
                numFeasible,
                -instance.weights()[i],
                hi - lo,
                -adj[i].size(),
                i
            );

            if (score.compareTo(bestMrvScore) < 0) {
                bestMrvScore = score;
                bestTask = i;
                bestFeasibleSlots = feasibleSlots;
            }
        }

        if (bestTask == -1) {
            return false;
        }

        // try slots in ascending order to minimize penalty contribution (monotonicity)
        for (int s : bestFeasibleSlots) {
            state.assignment.put(bestTask, s);
            for (int d = 0; d < 4; d++) {
                state.slotResources[s][d] -= instance.resources()[bestTask][d];
            }

            // recurse
            if (solveHeuristicRec(instance, adj, state)) {
                return true;
            }

            // backtrack
            state.assignment.remove(bestTask);
            for (int d = 0; d < 4; d++) {
                state.slotResources[s][d] += instance.resources()[bestTask][d];
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Integer> solveHeuristic(Instance instance, double beta, int maxStates, Map<String, Object> metadata) {
        // build adjacency list so conflict check is super fast O(1) instead of looping conflicts every time.
        // using arrays of sets because it is easy to code.
        int n = instance.tasks().size();
        Set<Integer>[] adj = new Set[n];
        for (int i = 0; i < n; i++) adj[i] = new HashSet<>();
        for (List<Integer> edge : instance.conflicts()) {
            int u = edge.get(0);
            int v = edge.get(1);
            adj[u].add(v);
            adj[v].add(u);
        }

        SolverState state = new SolverState(instance.capacities(), maxStates);
        boolean success = solveHeuristicRec(instance, adj, state);
        metadata.put("states", state.stateCount);

        if (success) {
            Map<String, Integer> res = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : state.assignment.entrySet()) {
                res.put(instance.tasks().get(entry.getKey()), entry.getValue());
            }
            return res;
        }
        return null;
    }

    // ==========================================
    // Task 6: Brute-Force Solver for Validation
    // ==========================================
    public static class BruteForceState {
        public Map<Integer, Integer> bestAssignment = null;
        public double bestPenalty = Double.MAX_VALUE;
        public Map<Integer, Integer> currentAssignment = new HashMap<>();
        public double[][] slotResources;

        public BruteForceState(double[][] capacities) {
            int K = capacities.length;
            this.slotResources = new double[K][4];
            for (int s = 0; s < K; s++) {
                System.arraycopy(capacities[s], 0, this.slotResources[s], 0, 4);
            }
        }
    }

    public static void solveBruteForceRec(Instance instance, Set<Integer>[] adj, int taskIdx, BruteForceState state, double beta) {
        // brute force solver to check optimal solution for small instances.
        // basically tries every single valid slot combination recursively and keeps the min penalty one.
        // obviously will blow up for n >= 15 but great for verification on small data.
        int n = instance.tasks().size();
        if (taskIdx == n) {
            Map<String, Integer> assignmentIds = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : state.currentAssignment.entrySet()) {
                assignmentIds.put(instance.tasks().get(entry.getKey()), entry.getValue());
            }
            double penalty = computePenalty(assignmentIds, instance, beta);
            if (penalty < state.bestPenalty) {
                state.bestPenalty = penalty;
                state.bestAssignment = new HashMap<>(state.currentAssignment);
            }
            return;
        }

        int lo = instance.windows()[taskIdx][0];
        int hi = instance.windows()[taskIdx][1];

        for (int s = lo; s <= hi; s++) {
            boolean conflict = false;
            for (int neighbor : adj[taskIdx]) {
                if (state.currentAssignment.containsKey(neighbor) && state.currentAssignment.get(neighbor) == s) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;

            boolean fits = true;
            for (int d = 0; d < 4; d++) {
                if (instance.resources()[taskIdx][d] > state.slotResources[s][d] + 1e-9) {
                    fits = false;
                    break;
                }
            }
            if (!fits) continue;

            state.currentAssignment.put(taskIdx, s);
            for (int d = 0; d < 4; d++) {
                state.slotResources[s][d] -= instance.resources()[taskIdx][d];
            }

            solveBruteForceRec(instance, adj, taskIdx + 1, state, beta);

            state.currentAssignment.remove(taskIdx);
            for (int d = 0; d < 4; d++) {
                state.slotResources[s][d] += instance.resources()[taskIdx][d];
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Integer> solveBruteForce(Instance instance, double beta) {
        int n = instance.tasks().size();
        Set<Integer>[] adj = new Set[n];
        for (int i = 0; i < n; i++) adj[i] = new HashSet<>();
        for (List<Integer> edge : instance.conflicts()) {
            int u = edge.get(0);
            int v = edge.get(1);
            adj[u].add(v);
            adj[v].add(u);
        }

        BruteForceState state = new BruteForceState(instance.capacities());
        solveBruteForceRec(instance, adj, 0, state, beta);

        if (state.bestAssignment != null) {
            Map<String, Integer> res = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : state.bestAssignment.entrySet()) {
                res.put(instance.tasks().get(entry.getKey()), entry.getValue());
            }
            return res;
        }
        return null;
    }

    // ==========================================
    // Verification Utility
    // ==========================================
    public record VerificationResult(boolean isValid, String reason) {}

    public static VerificationResult verifyFeasibility(Map<String, Integer> assignment, Instance instance) {
        // double check everything to make sure the solver did not cheat or leak resource limits.
        // checks conflict, capacity, and SLA window constraints.
        // returns a validation result with failure reason if something broke.
        int n = instance.tasks().size();
        if (assignment.size() != n) {
            return new VerificationResult(false, "Not all tasks assigned");
        }

        Map<String, Integer> taskToIdx = new HashMap<>();
        for (int i = 0; i < n; i++) {
            taskToIdx.put(instance.tasks().get(i), i);
        }

        // F3: SLA Windows
        for (Map.Entry<String, Integer> entry : assignment.entrySet()) {
            String t = entry.getKey();
            int slot = entry.getValue();
            if (!taskToIdx.containsKey(t)) {
                return new VerificationResult(false, "Unknown task: " + t);
            }
            int idx = taskToIdx.get(t);
            int lo = instance.windows()[idx][0];
            int hi = instance.windows()[idx][1];
            if (slot < lo || slot > hi) {
                return new VerificationResult(false, "F3 Violation: Task " + t + " scheduled in slot " + slot + ", outside SLA [" + lo + "," + hi + "]");
            }
        }

        // F1: Conflict Graph
        for (List<Integer> edge : instance.conflicts()) {
            String uName = instance.tasks().get(edge.get(0));
            String vName = instance.tasks.get(edge.get(1));
            if (assignment.get(uName).equals(assignment.get(vName))) {
                return new VerificationResult(false, "F1 Violation: Conflicting tasks " + uName + " and " + vName + " scheduled in same slot " + assignment.get(uName));
            }
        }

        // F2: Resource Capacities
        int K = instance.K();
        double[][] slotUsage = new double[K][4];
        for (Map.Entry<String, Integer> entry : assignment.entrySet()) {
            String t = entry.getKey();
            int slot = entry.getValue();
            int idx = taskToIdx.get(t);
            for (int d = 0; d < 4; d++) {
                slotUsage[slot][d] += instance.resources()[idx][d];
            }
        }

        for (int s = 0; s < K; s++) {
            for (int d = 0; d < 4; d++) {
                if (slotUsage[s][d] > instance.capacities()[s][d] + 1e-9) {
                    String[] dims = {"CPU", "RAM", "GPU", "Network"};
                    return new VerificationResult(false, "F2 Violation: Slot " + s + " capacity exceeded for " + dims[d] + ". Usage: " + slotUsage[s][d] + ", Capacity: " + instance.capacities()[s][d]);
                }
            }
        }

        return new VerificationResult(true, "");
    }

    // ==========================================
    // Json Parser (Built-in, Dependency-Free)
    // ==========================================
    public static List<String> parseStringArray(String s) {
        List<String> list = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(s);
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }

    public static List<List<Integer>> parseInt2DArray(String s) {
        List<List<Integer>> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]").matcher(s);
        while (m.find()) {
            list.add(List.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        }
        return list;
    }

    public static double[][] parseDouble2DArray(String s, int expectedRows, int expectedCols) {
        double[][] arr = new double[expectedRows][expectedCols];
        Matcher m = Pattern.compile("\\[\\s*([^\\[\\]]+)\\s*\\]").matcher(s);
        int row = 0;
        while (m.find() && row < expectedRows) {
            String[] parts = m.group(1).split(",");
            for (int col = 0; col < expectedCols && col < parts.length; col++) {
                arr[row][col] = Double.parseDouble(parts[col].trim());
            }
            row++;
        }
        return arr;
    }

    public static int[][] parseInt2DArrayAsMatrix(String s, int expectedRows) {
        int[][] arr = new int[expectedRows][2];
        Matcher m = Pattern.compile("\\[\\s*([^\\[\\]]+)\\s*\\]").matcher(s);
        int row = 0;
        while (m.find() && row < expectedRows) {
            String[] parts = m.group(1).split(",");
            arr[row][0] = Integer.parseInt(parts[0].trim());
            arr[row][1] = Integer.parseInt(parts[1].trim());
            row++;
        }
        return arr;
    }

    public static double[] parseDoubleArray(String s, int expectedSize) {
        double[] arr = new double[expectedSize];
        Matcher m = Pattern.compile("\\[\\s*([^\\]]+)\\s*\\]").matcher(s);
        if (m.find()) {
            String[] parts = m.group(1).split(",");
            for (int i = 0; i < expectedSize && i < parts.length; i++) {
                arr[i] = Double.parseDouble(parts[i].trim());
            }
        }
        return arr;
    }

    public static int parseKeyInteger(String s, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(s);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    public static Instance loadInstance(String jsonPath) throws IOException {
        // load the json file as a string. no external libs allowed, so we do manual regex parsing.
        // this was a huge pain to write, but it works fine for the generated inputs.
        // parses out all arrays and matrix fields.
        String json = new String(Files.readAllBytes(Paths.get(jsonPath)));

        int tasksIdx = json.indexOf("\"tasks\"");
        int conflictsIdx = json.indexOf("\"conflicts\"");
        int resourcesIdx = json.indexOf("\"resources\"");
        int capacitiesIdx = json.indexOf("\"capacities\"");
        int windowsIdx = json.indexOf("\"windows\"");
        int weightsIdx = json.indexOf("\"weights\"");
        int kIdx = json.indexOf("\"K\"");

        String tasksPart = json.substring(json.indexOf(":", tasksIdx) + 1, conflictsIdx);
        List<String> tasks = parseStringArray(tasksPart);

        String conflictsPart = json.substring(conflictsIdx, resourcesIdx);
        List<List<Integer>> conflicts = parseInt2DArray(conflictsPart);

        int K = parseKeyInteger(json.substring(kIdx), "K");

        String resourcesPart = json.substring(resourcesIdx, capacitiesIdx);
        double[][] resources = parseDouble2DArray(resourcesPart, tasks.size(), 4);

        String capacitiesPart = json.substring(capacitiesIdx, windowsIdx);
        double[][] capacities = parseDouble2DArray(capacitiesPart, K, 4);

        String windowsPart = json.substring(windowsIdx, weightsIdx);
        int[][] windows = parseInt2DArrayAsMatrix(windowsPart, tasks.size());

        String weightsPart = json.substring(weightsIdx, kIdx);
        double[] weights = parseDoubleArray(weightsPart, tasks.size());

        return new Instance(tasks, conflicts, resources, capacities, windows, weights, K);
    }

    public static String toJson(Map<String, Integer> assignment, double penalty, long runtimeMs, boolean feasible, String violationReason) {
        // format the results back into json.
        // format output float to 2 decimal places using Locale.US so commas don't mess up floats.
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"assignment\": {");
        if (feasible && assignment != null) {
            List<String> sortedTasks = new ArrayList<>(assignment.keySet());
            Collections.sort(sortedTasks);
            int i = 0;
            for (String t : sortedTasks) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(t).append("\": ").append(assignment.get(t));
                i++;
            }
        }
        sb.append("},\n");
        sb.append("  \"penalty\": ").append(feasible ? String.format(Locale.US, "%.2f", penalty) : "0.0").append(",\n");
        sb.append("  \"runtime_ms\": ").append(runtimeMs).append(",\n");
        sb.append("  \"feasible\": ").append(feasible).append(",\n");
        sb.append("  \"violation_reason\": \"").append(violationReason == null ? "" : violationReason.replace("\"", "\\\"")).append("\"\n");
        sb.append("}");
        return sb.toString();
    }

    // ==========================================
    // Main CLI Entry Point
    // ==========================================
    public static void main(String[] args) {
        // CLI entry point. parses args like --input, --output, --beta, --brute.
        // uses brute force if --brute is passed, else runs our fast heuristic solver.
        String inputPath = null;
        String outputPath = null;
        double beta = 5.0;
        int maxStates = 10000;
        boolean brute = false;

        for (int i = 0; i < args.length; i++) {
            if ("--input".equals(args[i]) && i + 1 < args.length) {
                inputPath = args[i + 1];
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputPath = args[i + 1];
            } else if ("--beta".equals(args[i]) && i + 1 < args.length) {
                beta = Double.parseDouble(args[i + 1]);
            } else if ("--brute".equals(args[i])) {
                brute = true;
            }
        }

        if (inputPath == null) {
            System.err.println("Usage: java Scheduler.java --input <file> [--output <file>] [--beta <value>] [--brute]");
            System.exit(1);
        }

        try {
            Instance instance = loadInstance(inputPath);
            Map<String, Object> metadata = new HashMap<>();

            long t0 = System.nanoTime();
            Map<String, Integer> assignment;
            if (brute) {
                assignment = solveBruteForce(instance, beta);
            } else {
                assignment = solveHeuristic(instance, beta, maxStates, metadata);
            }
            long runtimeMs = (System.nanoTime() - t0) / 1000000;

            boolean feasible = assignment != null;
            String reason = "";
            double penalty = 0.0;

            if (feasible) {
                VerificationResult ver = verifyFeasibility(assignment, instance);
                if (!ver.isValid()) {
                    feasible = false;
                    reason = ver.reason();
                } else {
                    penalty = computePenalty(assignment, instance, beta);
                }
            } else {
                reason = "Search space exhausted (infeasible)";
            }

            String jsonOut = toJson(assignment, penalty, runtimeMs, feasible, reason);

            if (outputPath != null) {
                Files.write(Paths.get(outputPath), jsonOut.getBytes());
                System.out.println("Results saved to " + outputPath);
            } else {
                System.out.println(jsonOut);
            }

        } catch (Exception e) {
            System.err.println("Error running scheduler: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
