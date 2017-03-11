package ImperiousArchon;

import battlecode.common.*;

import static ImperiousArchon.Utils.ZERO_LOCATION;
import static ImperiousArchon.Utils.randomAvailableDirection;
import static ImperiousArchon.Utils.randomDirection;

/**
 * Class providing implementation of AI for Lumberjacks.
 */
class Lumberjack extends AbstractRobot {

    /**
     * Creates a new Lumberjack robot.
     *
     * @param rc {@link RobotController} for this robot.
     */
    Lumberjack(RobotController rc) {
        super(rc);
    }

    @Override
    void run() throws GameActionException {
        System.out.println("I'm a lumberjack!");

        // The code you want your robot to perform every round should be in this loop
        //noinspection InfiniteLoopStatement
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                preloop();

                cutClosest();

//                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
//                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemyTeam);
//                if (robots.length > 0 && !rc.hasAttacked()) {
//                    // Use strike() to hit all nearby robots!
//                    rc.strike();
//                } else {
//                    // No close robots, so search for robots within sight radius
//                    robots = rc.senseNearbyRobots(-1, enemyTeam);
//
//                    // If there is a robot, move towards it
//                    if (robots.length > 0) {
//                        MapLocation myLocation = rc.getLocation();
//                        MapLocation enemyLocation = robots[0].getLocation();
//                        Direction toEnemy = myLocation.directionTo(enemyLocation);
//
//                        tryMove(myLocation.add(toEnemy));
//                    } else {
//                        // Move Randomly
//                        tryMove(currentLocation.add(randomDirection()));
//                    }
//                }

                postloop();
            } catch (Exception e) {
                System.out.println("ImperiousArchon.Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    private void cutClosest() throws GameActionException {
        TreeInfo closest = closestTree();

        /* Chop the closest tree */
        if (closest != null) {
            if (rc.canChop(closest.getID())) {
                rc.chop(closest.location);
            } else {
                tryMove(closest.location);
            }
        } else {
//            tryMove(currentLocation.add(randomAvailableDirection(rc, 10)));
            groupMove();
        }
    }

    /**
     * Finds the best closest chopable tree.
     *
     * @return The best closest chopable tree, null if does not exist.
     */
    private TreeInfo closestTree() {
        TreeInfo closestToStart = null;
        TreeInfo closestToMe = null;
        float distToStart = Float.POSITIVE_INFINITY;
        float distToMe = Float.POSITIVE_INFINITY;
        for (TreeInfo tree : trees) {
            float _distToStart = startLocation.distanceTo(tree.location);
            float _distToMe = currentLocation.distanceTo(tree.location);
            if (tree.team != ourTeam/* && rc.canChop(tree.getID())*/) {
                if (_distToStart < distToStart) {
                    closestToStart = tree;
                    distToStart = _distToStart;
                }
                if (_distToMe < distToMe) {
                    closestToMe = tree;
                    distToMe = _distToMe;
                }
            }
        }
        return closestToStart == null ? closestToMe : closestToStart;
    }

    private void groupMove() throws GameActionException {
        MapLocation force = new MapLocation(0, 0);

        final int lumberjackMass = 256;
        final int archonMass = 512;


        /* Add main force towards the destination */
//        force = force.add(myDirection, 128);
        final float distFromArchons2 = ourArchonsCentroid.distanceSquaredTo(currentLocation);
        force = force.add(enemyCentroidDirection, lumberjackMass * archonMass / distFromArchons2);

        for (RobotInfo robot : robots) {
            if (robot.team != ourTeam || robot.type != myType) {
                continue;
            }
            force = force.add(currentLocation.directionTo(robot.location),
                    lumberjackMass * lumberjackMass / currentLocation.distanceTo(robot.location));
        }

        tryMove(currentLocation.add(ZERO_LOCATION.directionTo(force), ZERO_LOCATION.distanceTo(force)));
    }

    @Override
    void readBroadcast() {
        /* ignoring... */
    }

}
