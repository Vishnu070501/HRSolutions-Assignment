import java.util.*;

public class SchedulerTest {

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + " - Expected: true, Actual: false");
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message + " - Expected: false, Actual: true");
        }
    }

    public static void testSingleTaskInstance() {
        System.out.print("Running testSingleTaskInstance... ");
        // just a single task with huge slot capacity.
        // should go to slot 0 since early slot = less penalty.
        List<String> tasks = List.of("T0");
        List<List<Integer>> conflicts = List.of();
        double[][] resources = {{2.0, 4.0, 1.0, 0.5}};
        double[][] capacities = {{10.0, 10.0, 10.0, 10.0}, {10.0, 10.0, 10.0, 10.0}};
        int[][] windows = {{0, 1}};
        double[] weights = {5.0};
        
        Scheduler.Instance instance = new Scheduler.Instance(tasks, conflicts, resources, capacities, windows, weights, 2);
        
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Integer> assignment = Scheduler.solveHeuristic(instance, 5.0, 10000, metadata);
        
        assertNotNull(assignment, "Assignment should not be null");
        assertTrue(assignment.containsKey("T0"), "T0 should be assigned");
        assertEquals(0, assignment.get("T0"), "T0 should be scheduled in slot 0 to minimize penalty");
        
        Scheduler.VerificationResult ver = Scheduler.verifyFeasibility(assignment, instance);
        assertTrue(ver.isValid(), "Feasibility verify failed: " + ver.reason());
        System.out.println("PASSED");
    }

    public static void testAllConflictGraph() {
        System.out.print("Running testAllConflictGraph... ");
        // K4 complete graph conflicts.
        // since we only have K=3 slots, we cannot color it (chromatic number is 4).
        // solver should fail and return null.
        List<String> tasks = List.of("T0", "T1", "T2", "T3");
        // K4 graph: every task conflicts with every other task
        List<List<Integer>> conflicts = List.of(
            List.of(0, 1), List.of(0, 2), List.of(0, 3),
            List.of(1, 2), List.of(1, 3), List.of(2, 3)
        );
        double[][] resources = {
            {1.0, 1.0, 0.0, 0.0}, {1.0, 1.0, 0.0, 0.0},
            {1.0, 1.0, 0.0, 0.0}, {1.0, 1.0, 0.0, 0.0}
        };
        double[][] capacities = {
            {10.0, 10.0, 10.0, 10.0}, {10.0, 10.0, 10.0, 10.0}, {10.0, 10.0, 10.0, 10.0}
        };
        int[][] windows = {{0, 2}, {0, 2}, {0, 2}, {0, 2}};
        double[] weights = {1.0, 1.0, 1.0, 1.0};
        
        Scheduler.Instance instance = new Scheduler.Instance(tasks, conflicts, resources, capacities, windows, weights, 3);
        
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Integer> assignment = Scheduler.solveHeuristic(instance, 5.0, 10000, metadata);
        
        // Should be infeasible since chromatic number is 4 > K (3)
        assertNull(assignment, "Complete graph with chromatic number > K must be infeasible");
        System.out.println("PASSED");
    }

    public static void testZeroCapacitySlot() {
        System.out.print("Running testZeroCapacitySlot... ");
        
        // Case A: task is forced into a zero-capacity slot -> infeasible
        List<String> tasks = List.of("T0");
        List<List<Integer>> conflicts = List.of();
        double[][] resources = {{1.0, 1.0, 1.0, 1.0}};
        double[][] capacities = {{10.0, 10.0, 10.0, 10.0}, {0.0, 0.0, 0.0, 0.0}};
        int[][] windows = {{1, 1}}; // forced to slot 1 (zero capacity)
        double[] weights = {3.0};
        
        Scheduler.Instance instanceInfeasible = new Scheduler.Instance(tasks, conflicts, resources, capacities, windows, weights, 2);
        
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Integer> assignmentInfeasible = Scheduler.solveHeuristic(instanceInfeasible, 5.0, 10000, metadata);
        assertNull(assignmentInfeasible, "Task forced into zero capacity slot should be infeasible");
        
        // Case B: task can choose slot 0 or slot 1, should pick slot 0 since slot 1 has zero capacity
        int[][] windowsFeasible = {{0, 1}};
        Scheduler.Instance instanceFeasible = new Scheduler.Instance(tasks, conflicts, resources, capacities, windowsFeasible, weights, 2);
        
        Map<String, Integer> assignmentFeasible = Scheduler.solveHeuristic(instanceFeasible, 5.0, 10000, metadata);
        assertNotNull(assignmentFeasible, "Task should avoid slot 1 and schedule in slot 0");
        assertEquals(0, assignmentFeasible.get("T0"), "Task should be scheduled in slot 0");
        
        System.out.println("PASSED");
    }

    public static void testTightSlaWindows() {
        System.out.print("Running testTightSlaWindows... ");
        // two tasks conflict and both are forced to slot 0 because of their SLA windows.
        // should fail since we can't schedule them in the same slot.
        List<String> tasks = List.of("T0", "T1");
        List<List<Integer>> conflicts = List.of(List.of(0, 1)); // conflict
        double[][] resources = {{1.0, 1.0, 0.0, 0.0}, {1.0, 1.0, 0.0, 0.0}};
        double[][] capacities = {{10.0, 10.0, 10.0, 10.0}, {10.0, 10.0, 10.0, 10.0}, {10.0, 10.0, 10.0, 10.0}};
        int[][] windows = {{0, 0}, {0, 0}}; // both forced to slot 0
        double[] weights = {1.0, 1.0};
        
        Scheduler.Instance instance = new Scheduler.Instance(tasks, conflicts, resources, capacities, windows, weights, 3);
        
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Integer> assignment = Scheduler.solveHeuristic(instance, 5.0, 10000, metadata);
        
        // Should be infeasible since they conflict and are forced to share slot 0
        assertNull(assignment, "Conflicting tasks forced to same slot must be infeasible");
        System.out.println("PASSED");
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError(message + " - Expected non-null, but was null");
        }
    }

    private static void assertNull(Object obj, String message) {
        if (obj != null) {
            throw new AssertionError(message + " - Expected null, but was " + obj);
        }
    }

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("RUNNING JAVA SCHEDULER UNIT TESTS");
        System.out.println("==========================================");
        try {
            testSingleTaskInstance();
            testAllConflictGraph();
            testZeroCapacitySlot();
            testTightSlaWindows();
            System.out.println("==========================================");
            System.out.println("ALL JAVA UNIT TESTS PASSED SUCCESSFULLY!");
            System.out.println("==========================================");
        } catch (Throwable e) {
            System.err.println("\nTEST SUITE RUNTIME FAILURE:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
