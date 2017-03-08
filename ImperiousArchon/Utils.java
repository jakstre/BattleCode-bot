package ImperiousArchon;

import battlecode.common.*;

/**
 * General utilities for all sort of helpful things.
 */
class Utils {

    public static final int BUILD_CHANNEL = 0;
    public static final int DIRECTION_CHANNEL = 1;

    static float unitStrength(RobotType type)
    {
        if (type.bulletSpeed > 0)
            return type.attackPower;
        if (type == RobotType.LUMBERJACK)
            return 1.0f;
        return 0.0f;
    }

    static RobotInfo findNearestRobot(RobotInfo[] robots, RobotType type, Team team)
    {
        //List is ordered by distance already so return first entry in list of the correct type
        for (RobotInfo r:robots) {
            if ((type == null || r.getType() == type) && (team == null || r.getTeam() == team))
                return r;
        }
        return null;
    }

    /**
     * Calculates a random {@link Direction}.
     *
     * @return The random {@link Direction}.
     */
    static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

    /**
     * Tries to find available {@link Direction} from the current position.
     * Runs only specified maximum number of times.
     *
     * @param rc {@link RobotController} of the robot to check.
     * @param maxAttempts Maximum number of attempts to find the not-obstructed direction.
     * @return Found non-obstructed {@link Direction} or null on fail.
     */
    static Direction randomAvailableDirection(RobotController rc, int maxAttempts) {
        Direction dir;
        MapLocation origin = rc.getLocation();
        for (int i = 0; i < maxAttempts; i++) {
            dir = randomDirection();
            if (rc.canMove(origin.add(dir))) {
                return dir;
            }
        }
        return null;
    }


    /*
    * Code copied from server to calculate if a bullet will hit an object
    */
    static float calcHitDist(MapLocation bulletStart, MapLocation bulletFinish,
                      MapLocation targetCenter, float targetRadius)
    {
        final float minDist = 0;
        final float maxDist = bulletStart.distanceTo(bulletFinish);
        final float distToTarget = bulletStart.distanceTo(targetCenter);
        final Direction toFinish = bulletStart.directionTo(bulletFinish);
        final Direction toTarget = bulletStart.directionTo(targetCenter);

        // If toTarget is null, then bullet is on top of centre of unit, distance is zero
        if(toTarget == null || distToTarget <= targetRadius) {
            return 0;
        }

        if(toFinish == null) {
            // This should never happen
            throw new RuntimeException("bulletStart and bulletFinish are the same.");
        }

        float radiansBetween = toFinish.radiansBetween(toTarget);

        //Check if the target intersects with the line made between the bullet points
        float perpDist = (float)Math.abs(distToTarget * Math.sin(radiansBetween));
        if(perpDist > targetRadius){
            return -1;
        }

        //Calculate hitDist
        float halfChordDist = (float)Math.sqrt(targetRadius * targetRadius - perpDist * perpDist);
        float hitDist = distToTarget * (float)Math.cos(radiansBetween);
        if(hitDist < 0){
            hitDist += halfChordDist;
            hitDist = hitDist >= 0 ? 0 : hitDist;
        }else{
            hitDist -= halfChordDist;
            hitDist = hitDist < 0 ? 0 : hitDist;
        }

        //Check invalid hitDists
        if(hitDist < minDist || hitDist > maxDist){
            return -1;
        }
        return hitDist;
    }
}
