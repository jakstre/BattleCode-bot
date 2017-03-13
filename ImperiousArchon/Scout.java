package ImperiousArchon;
import battlecode.common.*;

import static ImperiousArchon.Utils.REPORT_CHANNEL;

public class Scout extends AbstractRobot {

    public boolean SHOOTJACKS = true;


    public Scout(RobotController rc)
    {
        super(rc);
    }



    public void run() throws GameActionException
    {
        Team enemy = rc.getTeam().opponent();
        MapLocation[] archons = rc.getInitialArchonLocations(enemy);
        int archon_to_visit = 0;
        //Work out which of our archons we are nearest
        MapLocation[] myArchons = rc.getInitialArchonLocations(rc.getTeam());
        int nearest = -1;
        int i = 0;
        for (MapLocation m: myArchons) {
            if (nearest == -1 || rc.getLocation().distanceTo(m) < rc.getLocation().distanceTo(myArchons[nearest]))
                nearest = i;
            i++;
        }

        // The code you want your robot to perform every round should be in this loop
        //noinspection InfiniteLoopStatement
        while (true)
        {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                preloop();
                boolean fought = fight();

                if (!fought) {
                    checkShake();

                    // Move towards current enemy archon position
                    if (!rc.hasMoved()) {
                        if (archon_to_visit >= archons.length) {
                            randomWalk();
                        } else {
                            if (rc.getLocation().distanceTo(archons[(archon_to_visit + nearest) % archons.length]) < rc.getType().strideRadius)
                                archon_to_visit++;
                            tryMove(archons[(archon_to_visit + nearest) % archons.length]);
                        }
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again

                //System.out.print(Clock.getBytecodesLeft());
                postloop();

            } catch (Exception e) {

                e.printStackTrace();
            }
        }

    }

    @Override
    void readBroadcast() throws GameActionException {

    }

    void reportEnemy(MapLocation loc, float power) throws GameActionException
    {
        int round = rc.readBroadcast(REPORT_CHANNEL);
        int active = rc.readBroadcast(REPORT_CHANNEL +1);

        if (rc.getRoundNum()-round <30 && active>0)
        {
            float otherEnemyPower = rc.readBroadcastFloat(REPORT_CHANNEL +2);
            //TODO maybe change the condition
            if (otherEnemyPower< power)
                //other scout found easier target
                return;
        }

        rc.broadcast(REPORT_CHANNEL, rc.getRoundNum());
        rc.broadcast(REPORT_CHANNEL +1,1);
        rc.broadcastFloat(REPORT_CHANNEL +2, power);
        broadCastLocation(REPORT_CHANNEL +3, loc);
    }

    /*
    * A tree is safe if we can hide in it
    * That means there has to be room
    */
    boolean isTreeSafe(TreeInfo t) throws GameActionException {
        if (Clock.getBytecodesLeft() < 2000) //This routine can take time so return false if we are short on time
            return false;

        if (t.getHealth() <= 20)
            return false;

        if (t.getRadius() < RobotType.SCOUT.bodyRadius) //Too small to hide in
            return false;

        //For trees the same size as us they are safe if no lumberjack is in range and no unit with bullets is within a stride of the tree edge
        for (RobotInfo r: robots)
        {
            float distanceToTree = r.getLocation().distanceTo(t.getLocation());
            float gapBetween = distanceToTree - t.getRadius() - r.getType().bodyRadius;
            if (gapBetween < 0)
            { //Occupied
                //setIndicator(t.getLocation(),255,128,128);
                return false;
            }
            if (r.getTeam() != rc.getTeam() && r.getType().canAttack())
            { //Enemy
                float dangerDist = r.getType().strideRadius;
                if (r.getType() == RobotType.LUMBERJACK)
                    dangerDist += GameConstants.LUMBERJACK_STRIKE_RADIUS;
                else
                    dangerDist += GameConstants.BULLET_SPAWN_OFFSET;
                if (gapBetween <= dangerDist) {
                    //setIndicator(t.getLocation(), 255,0,0);
                    return false;
                }
            }
        }
        indicate(t.getLocation(),0,255,0);
        return true;
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

        float allyPower = 0f;
        float enemyPower = 0f;

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
            indicate(rc.getLocation(),255,255,255);
            return false;
        }
        reportEnemy(nearestEnemy.location,enemyPower);

        //dodge
        if (damageAtLocation(rc.getLocation()) > 0)
        {
            //We are better off dodging by moving away
            shoot(nearestEnemy);
            safeDistance = nearestEnemy.getType().sensorRadius;// + rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET; //Move out of its sight radius
            MapLocation away = rc.getLocation().add(nearestEnemy.getLocation().directionTo(rc.getLocation()).rotateLeftDegrees(5), safeDistance);
            tryMove(away);
            return true;
        }

        MapLocation combatPosition = null;

        // If there is a threat ...
       // TODO this is simplyfication
        if (nearestDanger != null && canSenseMe(nearestDanger))
        {
            MapLocation dangerLoc = nearestDanger.getLocation();

            //If we are a scout then find the nearest available tree to shoot from
            //Must be close to target and us and safe
            TreeInfo nearestTree = null;

            TreeInfo[] near = rc.senseNearbyTrees(dangerLoc, 3, null);
            for (TreeInfo t : near) {
                if (isTreeSafe(t)) {
                    nearestTree = t;
                    break;
                }
            }


            if (nearestTree != null)
            { //Scouts can hide in trees
                indicate(rc.getLocation(),0,255,0);

                float bulletOffset = GameConstants.BULLET_SPAWN_OFFSET / 2;
                float dist = nearestTree.radius - RobotType.SCOUT.bodyRadius - bulletOffset;

                combatPosition = nearestTree.getLocation().add(nearestTree.getLocation().directionTo(dangerLoc), dist);

                //combatPosition = nearestTree.getLocation();
                if (nearestGardener != null)
                    target = nearestGardener;
                    //shoot(nearestGardener);
                    //TODO maybe change this
                else if (nearestEnemy.health < 110)
                    target = nearestEnemy;
                //shoot(nearestEnemy);
            }
            else
            {
                if (nearestLumberjack != null && myLocation.distanceTo(nearestLumberjack.getLocation()) < safeDistance)
                {
                    indicate(rc.getLocation(),255,0,0);
                    combatPosition = nearestLumberjack.getLocation().add(nearestLumberjack.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                    target = nearestDanger;
                        /*
                      safeDistance = RobotType.SCOUT.sensorRadius;
                      combatPosition = rc.getLocation().add(nearestDanger.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                        }*/

                    //shoot(nearestLumberjack);
                }
                else if (canBeat(nearestDanger)&& allyPower>=enemyPower)
                {
                    indicate(dangerLoc,255,0,255);
                    combatPosition = dangerLoc.add(dangerLoc.directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                    target = nearestDanger;
                    //shoot(nearestDanger);
                }
                else if (canShootMe(nearestDanger) || allyPower<enemyPower)
                {
                    //run
                    indicate(rc.getLocation(),255,0,0);
                    // safeDistance = t.sensorRadius + RobotType.SCOUT.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
                    safeDistance = RobotType.SCOUT.sensorRadius;
                    combatPosition = rc.getLocation().add(dangerLoc.directionTo(myLocation), safeDistance);
                }
                else return false;
            }
        }
        else
        {
            //Not in danger so close with best enemy target

            if (nearestGardener != null)
            {
                indicate(nearestGardener.getLocation(),0,0,255);
                target=nearestGardener;
                safeDistance = RobotType.SCOUT.bodyRadius +target.type.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
                combatPosition = target.getLocation().add(target.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);

                //shoot(nearestGardener);
            }
            else if (nearestArchon != null)
            {
                indicate(nearestArchon.getLocation(),0,0,255);
                if (rc.getTeamBullets()>700)
                {
                    target = nearestArchon;
                    safeDistance+=RobotType.SCOUT.bodyRadius +target.type.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
                    //shoot(nearestArchon);
                    combatPosition = target.getLocation().add(target.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                }
                else
                    return false;


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
