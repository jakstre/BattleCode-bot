package ImperiousArchon;

import battlecode.common.*;

/**
 * Created by jakub on 03.03.2017.
 */
public class Utils {

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

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
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
