package ImperiousArchon;

import battlecode.common.*;

import java.util.Arrays;
import java.util.List;

import static ImperiousArchon.Gardener.MAX_TREES;
import static ImperiousArchon.Utils.DIRECTION_CHANNEL;
import static ImperiousArchon.Utils.ZERO_LOCATION;
import static ImperiousArchon.Utils.randomAvailableDirection;

/**
 * Class providing implementation of AI for Archons.
 */
class Archon extends AbstractRobot {

    private final static int NUM_ANGLES = 360 / 4;
    private final static int MAX_GARDENERS = 700;

    private int numGardeners;
    private float[] cumSum;

    static List<RobotType> priorityBuildQueue = Arrays.asList(RobotType.SCOUT, RobotType.SOLDIER);


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

//                if (RobotPlayer.DEBUG) {
//                    for (int i = 0; i < NUM_ANGLES; i++) {
//                        float angle = (float) (2f * Math.PI / NUM_ANGLES * i);
//                        int intensity = (int) (countDensity(angle) * 255);
//                        if (intensity == 0) {
//                            continue;
//                        }
//                        indicateLine(
//                                ourArchonsCentroid,
//                                ourArchonsCentroid.add(angle, 2),
//                                intensity, intensity, intensity);
//                    }
//                }

                /* Move away from enemy */
//                Direction walkDir = new Direction((float) (enemyCentroidDirection + Math.PI));
//                if (rc.canMove(walkDir)) {
//                    rc.move(walkDir);
//                }
                Direction walkDir = randomAvailableDirection(rc, 16);
//                Direction walkDir = findOpenSpaceDir();
                if (currentDirection != null) {
                    final float momentum = 0.7f;
                    MapLocation force = new MapLocation(0, 0);
                    force = force.add(walkDir, 1);
                    force = force.add(currentDirection, momentum);
                    walkDir = new MapLocation(0, 0).directionTo(force);
                }
                if (walkDir != null && rc.canMove(walkDir)) {
                    rc.move(walkDir);
                }

                /* Randomly attempt to build a gardener in available direction */
//                Direction dir = new Direction(findOpenSpaceDir());
                Direction dir = new Direction(randomDir());
                if (!rc.canBuildRobot(RobotType.GARDENER, dir)) {
                    dir = buildingDirection(RobotType.GARDENER, 32, 0);
                }
                final int numBuildFromQueue = rc.readBroadcastInt(Utils.BUILD_CHANNEL);
                if (dir != null
                        && ((rc.canHireGardener(dir) && (numGardeners <= 0 || numGardeners <= 4 && rc.getTeamBullets() >= 150))
                        || (numGardeners < MAX_GARDENERS
                        && rc.canHireGardener(dir)
                        && Math.random() < .1
                        && numBuildFromQueue >= priorityBuildQueue.size()))) {
                    rc.hireGardener(dir);
                    rc.broadcastFloat(DIRECTION_CHANNEL, randomDir());
//                    rc.broadcastFloat(DIRECTION_CHANNEL, dir.radians);
                    ++numGardeners;
                }
                if (dir == null) {
                    //TODO: najdi cestu do volneho prostoru!
                }

                /* Call for help if being attacked */
                checkHelpNeeds();

                postloop();
            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    private Direction findOpenSpaceDir() {
        /* Method using probability to find the biggest open space */
        final int numDivisions = 16;
        final int[] hits = new int[numDivisions];
        for (int i = 0; i < hits.length; i++) {
            hits[i] = 0;
        }
        final int hitsToTry = 32;
        final float angleStep = (float) (2 * Math.PI / numDivisions);
        for (int i = 0; i < numDivisions; i++) {
            Direction angle = new Direction((float) ((Math.random() - 0.5) * angleStep + angleStep * i));
            for (int j = 0; j < hitsToTry; j++) {
                MapLocation loc = currentLocation.add(angle, (float) (Math.random() * myType.sensorRadius));
                if (rc.canMove(loc)) {
                    ++hits[i];
//                    indicate(loc, 160, 255, 160);
                }
            }
        }

        MapLocation force = new MapLocation(0, 0);
        for (int i = 0; i < numDivisions; i++) {
            force = force.add(i * angleStep, hits[i] * hits[i]);
        }
        indicateLine(currentLocation, currentLocation.add(ZERO_LOCATION.directionTo(force), 10), 60, 250, 60);
        return ZERO_LOCATION.directionTo(force);
    }

    private float findBestGardenerDirection() {
        /* Method directly trying the positions -> precise */
        Direction dir = new Direction(randomDir());
        final int numDirs = 16;
        final float offset = (float) (2 * Math.PI / numDirs);
        for (int i = 0; i < numDirs; i++) {
            Direction buildDir = dir.rotateLeftRads(offset * i);
            if (rc.canHireGardener(buildDir)) {
                MapLocation gardenerLocation = currentLocation.add(buildDir,
                        myType.bodyRadius + myType.strideRadius + RobotType.GARDENER.bodyRadius);
                indicate(gardenerLocation, 64, 256, 64);
                final int _maxNum = numPlantPositions(gardenerLocation, buildDir);
                //TODO:...
            }
        }
        return 42;
    }

    private int numPlantPositions(MapLocation gardener, Direction myBuildingDirection) {
        int availablePositions = 0;
        for (int i = 0; i < MAX_TREES; i++) {
            Direction plantDir = myBuildingDirection.rotateLeftRads((float) (2 * Math.PI / MAX_TREES * i));
            if (rc.canMove(plantDir, RobotType.GARDENER.bodyRadius + RobotType.GARDENER.strideRadius
                    + GameConstants.BULLET_TREE_RADIUS)) {
                ++availablePositions;
            }
        }
        return availablePositions;
    }

    private float countDensity(float angle) {
        Direction dir = new Direction(angle);
        if (Math.abs(dir.radiansBetween(new Direction(enemyCentroidDirection))) > Math.PI / 3) {
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
