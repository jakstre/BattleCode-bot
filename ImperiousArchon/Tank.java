package ImperiousArchon;

import battlecode.common.*;

/**
 * Created by jakub on 05.03.2017.
 */
public class Tank extends  AbstractRobot
{

    public Tank(RobotController rc)
    {
        super(rc);
    }
    @Override
    void run() throws GameActionException
    {
        rallyPoint = enemyArchonsCentroid;
        // The code you want your robot to perform every round should be in this loop
        //noinspection InfiniteLoopStatement
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                preloop();

                boolean fought = fight();
                if (!fought)
                {
                    if (rallyPoint != null)
                    {
                        moveTo(rallyPoint);
                    }
                    else
                        randomWalk();
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                postloop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /*
    * Checks to see if we can move here
    * Uses rc.canMove and then performs extra checks for a TANK unit as we don't want to destroy our own trees
    */
    @Override
    boolean canMove(MapLocation dest) throws GameActionException {

        if (!rc.canMove(dest))
            return false;

        TreeInfo[] bump = rc.senseNearbyTrees(dest, RobotType.TANK.bodyRadius, rc.getTeam());
        if (bump != null && bump.length > 0)
            return false;


        return true;
    }

    @Override
    void readBroadcast() throws GameActionException {
        if (!checkHelpCalls())
            checkReports();
    }


    /*
    * Find the best target and the best position to shoot from
    * Returns true if we are engaged in combat
    */
    boolean fight() throws GameActionException
    {
        Team enemy = rc.getTeam().opponent();
        MapLocation myLocation = rc.getLocation();
        RobotInfo nearestGardener = null;
        RobotInfo nearestArchon = null;
        RobotInfo nearestDanger = null;
        RobotInfo nearestEnemy = null;
        RobotInfo nearestLumberjack = null;
        float safeDistance=lumberjackRange();

        RobotInfo target = null;
        float enemyPower = 0f;
        float allyPower = 0f;

        for (RobotInfo r:robots)
        {
            if (r.getTeam() == enemy)
            {
                enemyPower+=Utils.unitStrength(r.type);
                if (nearestGardener == null && r.getType() == RobotType.GARDENER)
                    nearestGardener = r;
                else if (nearestArchon == null && r.getType() == RobotType.ARCHON)
                    nearestArchon = r;
                else if (nearestLumberjack == null && r.getType() == RobotType.LUMBERJACK)
                    nearestLumberjack = r;
                if (nearestDanger == null && r.getType().canAttack())
                    nearestDanger = r;
                if (nearestEnemy == null)
                    nearestEnemy = r;
            }
            else
                allyPower+=Utils.unitStrength(r.type);
        }

        if (nearestEnemy == null)
        { //There are no enemies in sight but we might be being shot at
            return false;
        }


        if (allyPower<=enemyPower)
        {
            callHelp(nearestEnemy.location, allyPower, enemyPower);
        }

        MapLocation combatPosition = null;

        // If there is a threat ...
        // TODO this is simplyfication
        if (nearestDanger != null)
        {
            MapLocation dangerLoc = nearestDanger.getLocation();

            if (nearestLumberjack != null && myLocation.distanceTo(nearestLumberjack.getLocation()) < safeDistance)
            {
                combatPosition = nearestLumberjack.getLocation().add(nearestLumberjack.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                target=nearestDanger;
                //shoot(nearestLumberjack);
            }
            else if (canBeat(nearestDanger))
            {
                indicate(nearestDanger.location,255,0,255);
                safeDistance = rc.getType().bodyRadius + nearestDanger.getType().bodyRadius+ GameConstants.BULLET_SPAWN_OFFSET;
                combatPosition = dangerLoc.add(dangerLoc.directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                target = nearestDanger;
                //shoot(nearestDanger);
            }
            else if (canShootMe(nearestDanger))
            {
                indicate(rc.getLocation(),255,0,0);
                RobotType t = nearestDanger.getType();
                // safeDistance = t.sensorRadius + RobotType.SCOUT.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
                safeDistance = rc.getType().sensorRadius;
                combatPosition = rc.getLocation().add(nearestDanger.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                target = nearestDanger;
                // call for help
            }
            else return false;
        }

        else
        {
            //Not in danger so close with best enemy target

            if (nearestGardener != null)
            {
                indicate(nearestGardener.getLocation(),0,0,255);
                target=nearestGardener;
                safeDistance = rc.getType().bodyRadius + RobotType.GARDENER.bodyRadius+ GameConstants.BULLET_SPAWN_OFFSET;
                combatPosition = target.getLocation().add(target.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);

                //shoot(nearestGardener);
            }
            else if (nearestArchon != null)
            {
                indicate(nearestArchon.getLocation(),0,0,255);
                target = nearestArchon;
                safeDistance = rc.getType().bodyRadius + RobotType.ARCHON.bodyRadius+ GameConstants.BULLET_SPAWN_OFFSET;
                //shoot(nearestArchon);
                combatPosition = target.getLocation().add(target.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);

            }
            else
                return false; //shouldnt happen
        }
        if (combatPosition != null)
        {
            tryMove(combatPosition);
        }

        if(target!=null)
            shoot(target);
        return true;
    }

}
