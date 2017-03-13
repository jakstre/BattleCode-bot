package ImperiousArchon;

import battlecode.common.*;

import static ImperiousArchon.Utils.ZERO_LOCATION;
import static ImperiousArchon.Utils.randomAvailableDirection;
import static ImperiousArchon.Utils.randomDirection;

/**
 * Created by jakub on 05.03.2017.
 */
public class Soldier extends  AbstractRobot
{

    private int startMovingRound;
    private int lastDirectionChange;


    public Soldier(RobotController rc)
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
                    //checkShake();
                    if (rallyPoint != null)
                    {
                        move();
                    }
                    else {
                        randomWalk();
//                        wanderWalk();
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                postloop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    void readBroadcast() throws GameActionException
    {
        if (!checkHelpCalls()) {
            checkReports();
        } else {
            startMovingRound = currentRound;
        }
    }

    private void move() throws GameActionException {
        /* If stuck by a tree, shoot it down! */
        if (lastLocation != null && currentRound - startMovingRound > 1 && currentSpeed < 0.2) {
            float dist = Float.POSITIVE_INFINITY;
            TreeInfo closest = null;
            for (TreeInfo tree : trees) {
                if (tree.team != ourTeam) {
                    float _dist = currentLocation.distanceTo(tree.location);
                    if (_dist < dist) {
                        closest = tree;
                    }
                }
            }

            if (closest != null) {
                rc.move(currentLocation.directionTo(closest.location));
                rc.fireSingleShot(currentLocation.directionTo(closest.location));
                return;
            }
        }

        if (!moveTo(rallyPoint)) {
            wanderWalk();
        }
    }

    private void wanderWalk() throws GameActionException {
        MapLocation force = new MapLocation(0, 0);

        final int myMass = 16;
        final int wanderForce = 128;

        if (currentRound - lastDirectionChange > 1
                && (currentDirection == null || !rc.canMove(currentDirection) || currentSpeed < 0.1)) {
            Direction randDir = randomAvailableDirection(rc, 16);
            if (randDir == null) {
                randDir = randomDirection();
            }
            if (currentDirection != null) {
                force = force.add((float) (currentDirection.radians + Math.PI / 2), 2 * wanderForce);
            }
            force = force.add(randDir, wanderForce);
            force = force.add(currentLocation.directionTo(enemyArchonsCentroid), 0.3f * wanderForce);
            lastDirectionChange = currentRound;
        } else {
            force = force.add(currentDirection, wanderForce);
        }

        /* Add pushing forces */
        for (RobotInfo robot : robots) {
            if (robot.team == ourTeam) {
                if (robot.type == RobotType.SOLDIER) {
                    /* Force towards other soldiers */
                    final MapLocation objectLocation = robot.location;
                    final float distToObject = currentLocation.distanceSquaredTo(objectLocation);
                    force = force.add(
                            currentLocation.directionTo(objectLocation).radians,
                            myMass * myMass / distToObject);
                } else if (robot.type == RobotType.GARDENER) {
                    /* Force away from gardeners */
                    final MapLocation objectLocation = robot.location;
                    final float distToObject = currentLocation.distanceSquaredTo(objectLocation);
                    force = force.subtract(
                            currentLocation.directionTo(objectLocation).radians,
                            myMass * myMass / distToObject);
                }
            }
        }

        tryMove(currentLocation.add(ZERO_LOCATION.directionTo(force), ZERO_LOCATION.distanceTo(force)));
    }

    /*
    * Find the best target and the best position to shoot from
    * Returns true if we are engaged in combat
    */
    boolean fight() throws GameActionException
    {
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
            if (r.getTeam() == enemyTeam)
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

            if (nearestLumberjack != null && currentLocation.distanceTo(nearestLumberjack.getLocation()) < safeDistance)
            {
                combatPosition = nearestLumberjack.getLocation().add(nearestLumberjack.getLocation().directionTo(currentLocation).rotateLeftDegrees(5), safeDistance);
                target=nearestDanger;
                //shoot(nearestLumberjack);
            }
            else if (canBeat(nearestDanger))
            {
                indicate(nearestDanger.location,255,0,255);
                safeDistance = rc.getType().bodyRadius + nearestDanger.getType().bodyRadius+ GameConstants.BULLET_SPAWN_OFFSET;
                combatPosition = dangerLoc.add(dangerLoc.directionTo(currentLocation).rotateLeftDegrees(5), safeDistance);
                target = nearestDanger;
                //shoot(nearestDanger);
            }
            else //if (canShootMe(nearestDanger))
            {
                indicate(rc.getLocation(),255,0,0);
                RobotType t = nearestDanger.getType();
                // safeDistance = t.sensorRadius + RobotType.SCOUT.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
                safeDistance = t.sensorRadius;
                combatPosition = rc.getLocation().add(nearestDanger.getLocation().directionTo(currentLocation).rotateLeftDegrees(5), safeDistance);
                target = nearestDanger;
                // call for help
            }
            //else return false;
        }

        else
        {
            //Not in danger so close with best enemy target

            if (nearestGardener != null)
            {
                indicate(nearestGardener.getLocation(),0,0,255);
                target=nearestGardener;
                safeDistance = rc.getType().bodyRadius + RobotType.GARDENER.bodyRadius+ GameConstants.BULLET_SPAWN_OFFSET;
                combatPosition = target.getLocation().add(target.getLocation().directionTo(currentLocation).rotateLeftDegrees(5), safeDistance);

                //shoot(nearestGardener);
            }
            else if (nearestArchon != null)
            {
                indicate(nearestArchon.getLocation(),0,0,255);
                target = nearestArchon;
                safeDistance = rc.getType().bodyRadius + RobotType.ARCHON.bodyRadius+ GameConstants.BULLET_SPAWN_OFFSET;
                //shoot(nearestArchon);
                combatPosition = target.getLocation().add(target.getLocation().directionTo(currentLocation).rotateLeftDegrees(5), safeDistance);

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
