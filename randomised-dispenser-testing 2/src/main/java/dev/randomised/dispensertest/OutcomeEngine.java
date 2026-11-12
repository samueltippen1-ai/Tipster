package dev.randomised.dispensertest;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Pure-Java outcome logic (no Minecraft dependencies, so it is trivial to unit test or tweak).
 *
 * <p>Input: for each container, an int[9] where index = inventory slot (0 = top-left, 8 = bottom-right)
 * and the value is the "number" of that slot (0 = empty). With the default SLOT value mode the value is
 * {@code slot + 1}, so values grow towards the bottom of the 3x3 grid (row 1 = 1-3, row 2 = 4-6, row 3 = 7-9).
 *
 * <p>Outcome: one occupied slot is picked in each container such that the container inspected most recently
 * ("recent") gets a value that is greater than or equal to the other one (strictly greater if configured).
 * Every valid (recentSlot, otherSlot) pair is a candidate and one is drawn at random, so results vary.
 */
public final class OutcomeEngine {

    private OutcomeEngine() {}

    /** @param satisfied false only when the comparison rule was impossible to meet with the available items. */
    public record Result(int slotRecent, int slotOther, int valueRecent, int valueOther, boolean satisfied) {}

    /**
     * @param recent  values of the most recently inspected container (0 = empty slot)
     * @param other   values of the other container
     * @param strict  require recent &gt; other instead of recent &gt;= other
     * @param spread  0 = every valid pair equally likely; higher values favour larger margins
     * @return the chosen slots, or null if either container is empty
     */
    public static Result roll(int[] recent, int[] other, boolean strict, double spread, RandomGenerator rng) {
        List<int[]> pairs = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;

        for (int i = 0; i < recent.length; i++) {
            if (recent[i] <= 0) continue;
            for (int j = 0; j < other.length; j++) {
                if (other[j] <= 0) continue;
                boolean ok = strict ? recent[i] > other[j] : recent[i] >= other[j];
                if (!ok) continue;
                double w = spread <= 0 ? 1.0 : Math.pow(1.0 + (recent[i] - other[j]), spread);
                pairs.add(new int[] {i, j});
                weights.add(w);
                total += w;
            }
        }

        if (!pairs.isEmpty()) {
            double r = rng.nextDouble() * total;
            for (int k = 0; k < pairs.size(); k++) {
                r -= weights.get(k);
                if (r <= 0 || k == pairs.size() - 1) {
                    int[] p = pairs.get(k);
                    return new Result(p[0], p[1], recent[p[0]], other[p[1]], true);
                }
            }
        }

        // Rule cannot be met (e.g. every item in "other" outranks "recent"): best effort.
        int bestRecent = pickExtreme(recent, true, rng);
        int worstOther = pickExtreme(other, false, rng);
        if (bestRecent < 0 || worstOther < 0) return null;
        return new Result(bestRecent, worstOther, recent[bestRecent], other[worstOther], false);
    }

    /** Random slot among those holding the max (or min) non-zero value; -1 if all empty. */
    private static int pickExtreme(int[] values, boolean max, RandomGenerator rng) {
        int best = -1;
        List<Integer> ties = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            if (best < 0 || (max ? values[i] > values[best] : values[i] < values[best])) {
                best = i;
                ties.clear();
                ties.add(i);
            } else if (values[i] == values[best]) {
                ties.add(i);
            }
        }
        return ties.isEmpty() ? -1 : ties.get(rng.nextInt(ties.size()));
    }
}
