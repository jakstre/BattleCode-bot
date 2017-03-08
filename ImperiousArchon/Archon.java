package ImperiousArchon;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

import static ImperiousArchon.Utils.DIRECTION_CHANNEL;

/**
 * Class providing implementation of AI for Archons.
 */
class Archon extends AbstractRobot {

    private final static int NUM_ANGLES = 360 / 4;
    private final static int MAX_GARDENERS = 10;

    private int numGardeners;
    private float[] cumSum;

    /**
     * Creates a new Archon robot.
     *
     * @param rc {@link RobotController} for this robot.
     */
    Archon(RobotController rc) {
        super(rc);
    }

    @Override
    void run() throws GameActionException {
        System.out.println("I'm an archon!");

        /* Precalculate distribution */
        cumSumDist();

        // The code you want your robot to perform every round should be in this loop
        //noinspection InfiniteLoopStatement
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                preloop();

                if (RobotPlayer.DEBUG) {
                    for (int i = 0; i < NUM_ANGLES; i++) {
                        float angle = (float) (2f * Math.PI / NUM_ANGLES * i);
                        int intensity = (int) (countDensity(angle) * 255);
                        if (intensity == 0) {
                            continue;
                        }
                        indicateLine(
                                ourArchonsCentroid,
                                ourArchonsCentroid.add(angle, 4),
                                intensity, intensity, intensity);
                    }
                }

                /* Generate a random direction with custom distribution */
                Direction dir = new Direction(randomDir());

                /* Randomly attempt to build a gardener in this direction */
                if (numGardeners < MAX_GARDENERS && rc.canHireGardener(dir) && Math.random() < .05) {
                    rc.hireGardener(dir);
                    rc.broadcastFloat(DIRECTION_CHANNEL, randomDir());
                    ++numGardeners;
                }

                postloop();
            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    private float countDensity(float angle) {
        Direction dir = new Direction(angle);
        if (Math.abs(dir.radiansBetween(new Direction(enemyCentroidDirection))) > Math.PI / 4) {
            return 0;
        }
        return (float)
                (Math.abs(Math.PI - Math.abs((angle % (2 * Math.PI)) - enemyCentroidDirection))
                        / (Math.PI * Math.PI));
    }

    private void cumSumDist() {
        cumSum = new float[NUM_ANGLES];
        for (int i = 0; i < NUM_ANGLES; i++) {
            float angle = (float) (2 * Math.PI / NUM_ANGLES * i);
            if (i > 0) {
                cumSum[i] = cumSum[i - 1] + countDensity(angle);
            } else {
                cumSum[0] = countDensity(angle);
            }
        }

        /* Normalization not needed */
        if (cumSum[NUM_ANGLES - 1] == 1) {
            return;
        }

        /* Normalize cumulative sum */
        for (int i = 0; i < NUM_ANGLES; i++) {
            cumSum[i] /= cumSum[NUM_ANGLES - 1];
//            System.out.println("cumsum[" + i + "] = " + cumSum[i]);
        }
    }

    private float randomDir() {
        double uniformRand = Math.random();
        for (int i = 0; i < cumSum.length; i++) {
            if (uniformRand <= cumSum[i]) {
                return (float) (2 * Math.PI / NUM_ANGLES * i);
            }
        }
        return 0;
    }

    @Override
    void readBroadcast() {
        /* ignoring... */
    }

}
