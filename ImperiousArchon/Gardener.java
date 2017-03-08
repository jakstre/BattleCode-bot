package ImperiousArchon;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ImperiousArchon.Utils.BUILD_CHANNEL;
import static ImperiousArchon.Utils.randomAvailableDirection;

/**
 * Class providing implementation of AI for Gardeners.
 */
class Gardener extends AbstractRobot {

    private static final int MAX_TREES = 5;
    private static final float MIN_TREE_HEALTH_DIFF = 5;

    private static final MapLocation ZERO_LOCATION = new MapLocation(0, 0);

    enum GardenerState {
        POSITIONING, PLANTING, GARDENING, MOTHER, LUMBERCAMPING
    }

    enum TreeStrategy {
        COMPACT, BARRICADE
    }

    private Direction lastTreeDirection;
    private int wantedTrees = MAX_TREES;
    private int numBuild[] = new int[RobotPlayer.orderedTypes.length];
    private int[] wantedRobots = {10, 3, 2, 2};
    private float[] buildProbs = {0.04f, 0.02f, 0.01f, 0f};
    private GardenerState state = GardenerState.POSITIONING;
    private Float myDirection;
    private List<TreeInfo> myTrees = new ArrayList<>();
    private static List<RobotType> toBuild = Arrays.asList(RobotType.SCOUT, RobotType.SOLDIER);


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

                /* Check if some units have to be build */
                checkPriorityBuilds();

                switch (state) {
                    case POSITIONING:
                        takePosition();
                        break;
                    case PLANTING:
                        plant();
                        break;
                    case GARDENING:
                        tryWater();
                        tryBuild();
                        break;
                    case MOTHER:
                        wantedTrees = 3;
                        tryWater();
                        tryBuild();
                        break;
                    case LUMBERCAMPING:
                        lumbercamp();
                        break;
                }

                postloop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    void readBroadcast() throws GameActionException {
        //TODO: hezčí dekódování / předávání zpráv vůbec
        if (myDirection == null) {
            myDirection = rc.readBroadcastFloat(0);
            startMovingRound = rc.getRoundNum();
        }
    }

    private void checkPriorityBuilds() throws GameActionException {
        int buildIndex = rc.readBroadcastInt(BUILD_CHANNEL);
        if (buildIndex < toBuild.size()) {
            Direction dir = buildingDirection(toBuild.get(buildIndex), 10, 25);
            if (dir != null) {
                rc.buildRobot(toBuild.get(buildIndex), dir);
                rc.broadcast(BUILD_CHANNEL, buildIndex + 1);
            }
        }
    }

    private void tryBuild() throws GameActionException {
        for (RobotType orderedType : RobotPlayer.orderedTypes) {
            Direction dir = buildingDirection(orderedType, 12,25);
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
        plant();
    }

    // build specific robot on demand
    private void tryBuild(RobotType type) throws GameActionException
    {
        Direction buildDir = buildingDirection(type, 12,25);
        if (buildDir != null) {
            rc.buildRobot(type, buildDir);
        }
    }

    private void tryWater() throws GameActionException {
        /* Find a damaged tree with minimal health */
        TreeInfo worstTree = null;
        float minHealth = Float.POSITIVE_INFINITY;
        for (TreeInfo treeInfo : trees) {
            float _health = treeInfo.getHealth();
            float healthDiff = treeInfo.maxHealth - _health;
            if (_health < minHealth && healthDiff > MIN_TREE_HEALTH_DIFF && rc.canWater(treeInfo.location)) {
                worstTree = treeInfo;
                minHealth = _health;
            }
        }

        /* Remove first found killed tree */
        for (TreeInfo myTree : myTrees) {
            if (myTree.health <= 0) {
                myTrees.remove(myTree);
                break;
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
        /* Let's not forget about our trees */
        if (myTrees.size() > 0) {
            tryWater();
        }

        /* This gardener has enough trees to carry about already */
        if (myTrees.size() >= wantedTrees) {
            return;
        }

        /* Don't build trees right when you can, allow other thing to happen */
        if (Math.random() < 0.5) {
            return;
        }

        /* Then we can build some new ones */
        try {
            /* Find available position */
            Direction dir;
            if (myTrees.size() == 0) {
                dir = randomAvailableDirection(rc, 32);
            } else {
                dir = lastTreeDirection.rotateRightRads((float) (2 * Math.PI / 5));
            }

            /* Cannot plant right now */
            if (dir == null) {
                return;
            }

            /* Plant the tree here */
            if (rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                MapLocation plantPos = currentLocation.add(dir, RobotType.GARDENER.bodyRadius
                                + RobotType.GARDENER.strideRadius + GameConstants.BULLET_TREE_RADIUS);
                TreeInfo treeInfo = rc.senseTreeAtLocation(plantPos);
                System.out.println("info: "+treeInfo);
                myTrees.add(treeInfo);
//                ++numTrees;
            }
            lastTreeDirection = dir;
        } catch (GameActionException e) {
            e.printStackTrace();
        }
    }

    private int startMovingRound;

    private void takePosition() throws GameActionException {
        if (lastLocation != null && rc.getRoundNum() - startMovingRound > 1 && currentSpeed < 0.2) {
            if (numNotOurTreesVisible() > 3) {
                state = GardenerState.LUMBERCAMPING;
                return;
            }
        }

        /* Find path away from the Archon towards the enemy */
        newtonPath();

        /* Probability to change state increases with distance and with time */
        if (0.3 * getClosestOurArchonDistance() / defenceDist
                > Math.random() + 0.12 * Math.max(0, 1 - (rc.getRoundNum() - startMovingRound) / (42 + toBuild.size() * 15))) {
            if (Math.random() < 0.8) {
                state = GardenerState.MOTHER;
            } else {
                state = GardenerState.PLANTING;
            }
        }
    }

    private int numNotOurTreesVisible() {
        int numNotOursTreesVisible = 0;
        for (TreeInfo tree : trees) {
            if (tree.team != ourTeam) {
                ++numNotOursTreesVisible;
            }
        }
        return numNotOursTreesVisible;
    }

    private void newtonPath() throws GameActionException {
        MapLocation force = new MapLocation(0, 0);

        final int gardenerMass = 256;
        final int archonMass = 512;
        final float treeMass = 8;
        final float obstacleMass = 1;


        /* Add main force towards the destination */
//        force = force.add(myDirection, 128);
        final float distFromArchons2 = ourArchonsCentroid.distanceSquaredTo(currentLocation);
        force = force.add(myDirection, gardenerMass * archonMass / distFromArchons2);

        /* Force towards enemy */
//        force = force.add(enemyCentroidDirection, 32);
        final float distToArchons2 = enemyArchonsCentroid.distanceSquaredTo(currentLocation);
        force = force.add(enemyCentroidDirection, gardenerMass * archonMass / distToArchons2);

        /* Add pushing forces */
        final int sightRange = 7;
        final int step = 2;
        int row;
        for (int i = -sightRange; i < sightRange; i += step) {
            row = 0;
            for (int j = -sightRange; j < sightRange; j += step) {
                if (i == 0 && j == 0) {
                    continue;
                }

                MapLocation shift = currentLocation.translate(i + ((row % 2 == 0) ? -0 / 2f : 0f), j);
                if (currentLocation.distanceTo(shift) < sightRange) {
                    if (rc.canMove(shift)) {
                        indicate(shift, 10, 10, 128);
                        final float distToDot2 = currentLocation.distanceSquaredTo(shift);
                        force = force.add(
                                currentLocation.directionTo(shift).radians,
                                (float) (Math.random() * gardenerMass * obstacleMass / distToDot2));
                    } else {
                        indicate(shift, 128, 10, 10);
                        final float distToDot2 = currentLocation.distanceSquaredTo(shift);
                        force = force.subtract(
                                currentLocation.directionTo(shift).radians,
                                (float) (Math.random() * gardenerMass * obstacleMass / distToDot2));
                    }
                }
                ++row;
            }
        }
//        for (TreeInfo tree : trees) {
//            final float distToTree2 = currentLocation.distanceSquaredTo(tree.location);
//            force = force.subtract(
//                    currentLocation.directionTo(tree.location).radians,
//                    gardenerMass * treeMass / distToTree2);
//        }
        for (RobotInfo robot : robots) {
            if (robot.getTeam() != ourTeam || robot.type != RobotType.GARDENER) {
                continue;
            }

            final float distToRobot2 = currentLocation.distanceSquaredTo(robot.location);
            force = force.subtract(
                    currentLocation.directionTo(robot.location).radians,
                    gardenerMass * gardenerMass / distToRobot2);
        }
        tryMove(currentLocation.add(ZERO_LOCATION.directionTo(force), ZERO_LOCATION.distanceTo(force)));
    }

    private void lumbercamp() throws GameActionException {
        Direction dir = randomAvailableDirection(rc, 10);
        if (dir == null) {
            return;
        }
        int robotID = RobotPlayer.typeToInt.get(RobotType.LUMBERJACK);
        if (numBuild[robotID] < wantedRobots[robotID] && rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
            rc.buildRobot(RobotType.LUMBERJACK, dir);
            ++numBuild[robotID];
        } else if (numBuild[robotID] > 1) {
            final double uniRand = Math.random();
            if (uniRand < 0.8) {
                state = GardenerState.MOTHER;
            } else if (uniRand < 0.9) {
                state = GardenerState.PLANTING;
            } else {
                state = GardenerState.POSITIONING;
            }
        }
    }

}
