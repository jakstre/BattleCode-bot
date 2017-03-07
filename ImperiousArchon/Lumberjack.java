package ImperiousArchon;

import battlecode.common.*;

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

                //TODO: to co je nejblíže ke startu!
                /* Find the closest tree */
                TreeInfo closest = null;
                float dist = Float.POSITIVE_INFINITY;
                for (TreeInfo tree : trees) {
                    float _dist = currentLocation.distanceTo(tree.location);
                    if (tree.team != ourTeam && _dist < dist/* && rc.canChop(tree.getID())*/) {
                        closest = tree;
                        dist = _dist;
                    }
                }

                /* Chop the closest tree */
                if (closest != null) {
                    if (rc.canChop(closest.getID())) {
                        rc.chop(closest.location);
                    } else {
                        tryMove(closest.location);
                    }
                } else {
                    tryMove(currentLocation.add(randomAvailableDirection(rc, 10)));
                }

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

    @Override
    void readBroadcast() {
        /* ignoring... */
    }

}
