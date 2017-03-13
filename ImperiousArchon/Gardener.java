package ImperiousArchon;

import battlecode.common.*;

import static ImperiousArchon.Archon.priorityBuildQueue;
import static ImperiousArchon.Utils.*;

/**
 * Class providing implementation of AI for Gardeners.
 */
class Gardener extends AbstractRobot {

    static final int MAX_TREES = 5;
    private static final float MIN_TREE_HEALTH_DIFF = 5;

    private static final float DEFAULT_SOLDIER_PROBABILITY = 0.08f;
    private static final float DEFAULT_TANK_PROBABILITY = 0.03f;
    private static final float DEFAULT_SCOUT_PROBABILITY = 0.005f;
    private static final float DEFAULT_LUMBERJACK_PROBABILITY = 0.0f;
    //NOTE: this constant has a huge impact on fight result, but what is the "right" value?
    private static final float BUILDING_GAP = 4.5f;

    enum GardenerState {
        POSITIONING, MOTHER, LUMBERCAMPING
    }

    private int startMovingRound;
    private int numBuild[] = new int[RobotPlayer.orderedTypes.length];
    private int[] wantedRobots = {1000, 300, 2, 2};
    private float[] buildProbs = {
            DEFAULT_SOLDIER_PROBABILITY, DEFAULT_TANK_PROBABILITY,
            DEFAULT_SCOUT_PROBABILITY, DEFAULT_LUMBERJACK_PROBABILITY};
    private GardenerState state = GardenerState.POSITIONING;
    private Float myPositionDirection;
    private Direction myBuildingDirection;


    /**
     * Creates a new Gardener robot.
     *
     * @param rc {@link RobotController} for this robot.
     */
    Gardener(RobotController rc) {
        super(rc);

        init();
    }

    private void init() {
        for (int i = 0; i < numBuild.length; i++) {
            numBuild[i] = 0;
        }
    }

    @Override
    void run() throws GameActionException {
        /* The code you want your robot to perform every round should be in this loop */
        //noinspection InfiniteLoopStatement
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                preloop();

                myBuildingDirection = currentLocation.directionTo(enemyArchonsCentroid);

                /* Check if some units have to be build */
                checkPriorityBuilds();

                /* Water trees if you can */
                tryWater();

                /* Act based on your state */
                switch (state) {
                    case POSITIONING:
                        takePosition();
                        break;
                    case MOTHER:
                        tryBuild();
                        plant();
                        break;
                    case LUMBERCAMPING:
                        lumbercamp();
                        takePosition();
                        break;
                }

                /* Call for help if being attacked */
                checkHelpNeeds();

                postloop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    void readBroadcast() throws GameActionException {
        if (myPositionDirection == null) {
            myPositionDirection = rc.readBroadcastFloat(DIRECTION_CHANNEL);
            startMovingRound = currentRound;
        }
        if (checkHelpCalls()) {
            buildProbs[RobotPlayer.typeToInt.get(RobotType.SOLDIER)] = 2 * DEFAULT_SOLDIER_PROBABILITY;
        } else {
            buildProbs[RobotPlayer.typeToInt.get(RobotType.SOLDIER)] = DEFAULT_SOLDIER_PROBABILITY;
        }
    }

    private boolean checkPriorityBuilds() throws GameActionException {
        int buildIndex = rc.readBroadcastInt(BUILD_CHANNEL);
        if (buildIndex < priorityBuildQueue.size()) {
            Direction dir = buildingDirection(priorityBuildQueue.get(buildIndex), 10, 25);
            if (dir != null) {
                rc.buildRobot(priorityBuildQueue.get(buildIndex), dir);
                rc.broadcastInt(BUILD_CHANNEL, ++buildIndex);
            }
        }
        return buildIndex >= priorityBuildQueue.size();
    }

    private void tryBuild() throws GameActionException {
        for (RobotType orderedType : RobotPlayer.orderedTypes) {
            Direction dir = buildingDirection(orderedType, 12,0);
            if (dir == null) {
                continue;
            }

            int robotID = RobotPlayer.typeToInt.get(orderedType);
            if (numBuild[robotID] < wantedRobots[robotID]
                    && rc.canBuildRobot(orderedType, dir)
                    && Math.random() < buildProbs[robotID]) {
                rc.buildRobot(orderedType, dir);
                ++numBuild[robotID];
            }
        }
    }

    private void tryWater() throws GameActionException {
        /* Find a damaged tree with minimal health */
        TreeInfo worstTree = null;
        float minHealth = Float.POSITIVE_INFINITY;
        for (TreeInfo treeInfo : trees) {
            /* Finds the most damaged one */
            float _health = treeInfo.getHealth();
            float healthDiff = treeInfo.maxHealth - _health;
            if (_health < minHealth && healthDiff > MIN_TREE_HEALTH_DIFF && rc.canWater(treeInfo.location)) {
                worstTree = treeInfo;
                minHealth = _health;
            }
        }

        /* Nothing to do here */
        if (worstTree == null) {
            return;
        }

        /* Make the tree feel better */
        if (rc.canWater(worstTree.location)) {
            try {
                rc.water(worstTree.location);
            } catch (GameActionException e) {
                System.err.println("This should not have happened!");
                e.printStackTrace();
            }
        }
    }

    private void plant() throws GameActionException {
        /* Let's not build if available position would be lost */
        if (numPlantPositions() <= 1) {
            return;
        }

        /* Don't build trees right when you can, allow other thing to happen */
        if (Math.random() < 0.5) {
            return;
        }

        /* Then we can build some new ones */
        try {
            /* Find available position */
            Direction dir = null;

            /* Try to build the first few trees in direction to enemy to block the bullets */
            final float angularOffset = (float) (2f * Math.PI / MAX_TREES);
            for (int i = 0; i < MAX_TREES; i++) {
                if (i % 2 == 0) {
                    dir = myBuildingDirection.rotateLeftRads(angularOffset * i / 2);
                } else {
                    dir = myBuildingDirection.rotateRightRads(angularOffset * (i + 1) / 2);
                }
                /* Direction found */
                if (rc.canPlantTree(dir)) {
                    indicate(currentLocation.add(dir, RobotType.GARDENER.bodyRadius
                            + RobotType.GARDENER.strideRadius + GameConstants.BULLET_TREE_RADIUS), 128, 128, 256);
                    break;
                }
                /* Lets try another direction */
                indicate(currentLocation.add(dir, RobotType.GARDENER.bodyRadius
                        + RobotType.GARDENER.strideRadius + GameConstants.BULLET_TREE_RADIUS), 256, 128, 128);
                dir = null;
            }

            /* Cannot plant right now */
            if (dir == null) {
                return;
            }

            /* Plant the tree here */
            rc.plantTree(dir);
        } catch (GameActionException e) {
            e.printStackTrace();
        }
    }

    private int numPlantPositions() {
        int availablePositions = 0;
        for (int i = 0; i < MAX_TREES; i++) {
            Direction plantDir = myBuildingDirection.rotateLeftRads((float) (2 * Math.PI / MAX_TREES * i));
            if (rc.canPlantTree(plantDir)) {
                ++availablePositions;
            }
        }
        return availablePositions;
    }

    private void takePosition() throws GameActionException {
        if (lastLocation != null && currentRound - startMovingRound > 1 && currentSpeed < 0.2) {
            if (numNotOurTreesVisible() > 3) {
                state = GardenerState.LUMBERCAMPING;
                return;
            }
        }

        /* Find path away from the Archon towards the enemy */
        newtonPath();

        /* Probability to change state increases with distance and with time */
        if (canSettle()) {
            state = GardenerState.MOTHER;
        } else if (currentRound - startMovingRound > 10) {
            if (numNotOurTreesVisible() > 3) {
                state = GardenerState.LUMBERCAMPING;
            }
        }
    }

    private boolean canSettle() {
//        /* Don't build too close to archon */
//        final float dist = getClosestOurArchonDistance() / defenceDist;
//        if (dist < 1 && dist < Math.min(1, 0.2 + (currentRound - startMovingRound) / 100)) {
//            return false;
//        }

        /* Don't build too close to other gardeners */
        RobotInfo nearestGardener = findNearestRobot(robots, RobotType.GARDENER, ourTeam);
        if (nearestGardener != null && currentLocation.distanceTo(nearestGardener.location)
                < RobotType.GARDENER.bodyRadius + RobotType.GARDENER.strideRadius
                + GameConstants.BULLET_TREE_RADIUS + BUILDING_GAP) {
            return false;
        }

        /* Count available positions */
        final int freePositions = numPlantPositions();
        return freePositions >= MAX_TREES / 2;
    }

    private void newtonPath() throws GameActionException {
        MapLocation force = new MapLocation(0, 0);

        final int gardenerMass = 32;
        final int archonMass = 1024;
        final float treeMass = 1;

        /* Add main force towards the destination away from our centroid */
        final float distFromArchons2 = ourArchonsCentroid.distanceSquaredTo(currentLocation);
        force = force.add(myPositionDirection, gardenerMass * archonMass / distFromArchons2);
        force = force.add(myPositionDirection, archonMass);

        /* Force away from enemy centroid */
        final float distToArchons2 = enemyArchonsCentroid.distanceSquaredTo(currentLocation);
        force = force.subtract(enemyCentroidDirection, 0.5f * gardenerMass * archonMass / distToArchons2);

        /* Add pushing forces */
        for (TreeInfo tree : trees) {
            final float distToTree2 = currentLocation.distanceSquaredTo(tree.location);
            force = force.subtract(
                    currentLocation.directionTo(tree.location).radians,
                    gardenerMass * treeMass / distToTree2);
        }
        for (RobotInfo robot : robots) {
            if (robot.getTeam() != ourTeam || robot.type != RobotType.GARDENER) {
                continue;
            }

            final float distToRobot2 = currentLocation.distanceSquaredTo(robot.location);
            force = force.subtract(
                    currentLocation.directionTo(robot.location).radians,
                    gardenerMass * gardenerMass / distToRobot2);
        }

        /* Apply the resulting target position */
        tryMove(currentLocation.add(ZERO_LOCATION.directionTo(force), ZERO_LOCATION.distanceTo(force)));
    }

    private void lumbercamp() throws GameActionException {
        /* If the place is ready, change the state */
        if (canSettle()) {
            advanceState();
            return;
        }

        Direction dir = randomAvailableDirection(rc, 10);
        if (dir == null) {
            return;
        }
        int robotID = RobotPlayer.typeToInt.get(RobotType.LUMBERJACK);
        if (numBuild[robotID] < wantedRobots[robotID] && rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
            rc.buildRobot(RobotType.LUMBERJACK, dir);
            ++numBuild[robotID];
        } else if (numBuild[robotID] > 1) {
            state = GardenerState.POSITIONING;
        }
    }

    private void advanceState() {
        final double uniRand = Math.random();
        if (uniRand < 0.8) {
            state = GardenerState.MOTHER;
        } else {
            state = GardenerState.POSITIONING;
        }
    }

}
