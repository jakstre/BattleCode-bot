package ImperiousArchon;
import battlecode.common.*;

public class Scout extends AbstractRobot {

    public boolean SHOOTJACKS = false;
    public boolean DEBUG_SCOUT = true;

    public Scout(RobotController rc)
    {
        super(rc);
    }

    void indicate(MapLocation loc, int R,int G, int B)
    {
        if (DEBUG_SCOUT)
            rc.setIndicatorDot(loc,R,G,B);
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
        while (true)
        {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                checkWin();
                sense();
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
                            //debug(2, "Moving to next archon " + archons[(archon_to_visit+nearest)%archons.length]);
                        }
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                System.out.print(Clock.getBytecodesLeft());
                Clock.yield();

            } catch (Exception e) {
                //debug(1, "Scout Exception");
                e.printStackTrace();
            }
        }

    }


    void randomWalk() throws GameActionException {


        //debug(2, "Wandering");
        Direction dir = Utils.randomDirection();

        tryMove(rc.getLocation().add(dir, rc.getType().strideRadius), 0, 0, 0);
    }


    void checkShake() throws GameActionException
    {
        /*if (!rc.canShake())
            return;

        //Check to see if there is a tree in range that we can shake
        //Head to the one with the most resources if we are a scout
        TreeInfo bestTree = null;
        for (TreeInfo t:trees) {
            if (t.getContainedBullets() > 0) {
                if (rc.canShake(t.getID()))
                    rc.shake(t.getID());
                else if (rc.getType() == RobotType.SCOUT && (bestTree == null || t.getContainedBullets() > bestTree.getContainedBullets()))
                    bestTree = t;
            }
        }

        if (bestTree != null)
            tryMove(bestTree.getLocation());*/

        if (!rc.canShake())
            return;
        for (TreeInfo t:trees)
        {
            if (t.getContainedBullets() > 0)
            {
                if (rc.canShake(t.getID()))
                    rc.shake(t.getID());
                else
                    tryMove(t.getLocation());
            }
        }
    }

    boolean tryMove(MapLocation to) throws GameActionException {
        return tryMove(to, 22, 4, 4);
    }

    boolean tryMove(MapLocation to, float degreeOffset, int checksLeft, int checksRight) throws GameActionException {
        if (rc.hasMoved() || to == null)
            return false;

        MapLocation here = rc.getLocation();
        Direction dir = here.directionTo(to);
        float dist = here.distanceTo(to);
        MapLocation dest = to;

        if (dir == null || dist <= 0 || here == to)
            return true;

        if (dist > rc.getType().strideRadius) {
            dist = rc.getType().strideRadius;
            dest = here.add(dir, dist);
        }

        MapLocation bestUnsafe = null;
        float leastDamage = 1000;
        float damage;

        // First, try intended direction
        if (rc.canMove(dest)) {
            damage = damageAtLocation(dest);
            if (damage > 0 && damage < leastDamage) {
                leastDamage = damage;
                bestUnsafe = dest;
            }
            if (damage == 0) {
                rc.move(dest);
                //setIndicator(here, dest, 0, 255, 0);
                return true;
            }
        }

        // Now try a bunch of similar angles
        int currentCheck = 1;
        int checksPerSide = Math.max(checksLeft, checksRight);

        //debug(3, "tryMove: checking " + checksPerSide + " locations (" + checksLeft + " left and " + checksRight + " right)");

        while(currentCheck<=checksPerSide)
        {
            // Try the offset of the left side
            if (currentCheck <= checksLeft)
            {
                dest = here.add(dir.rotateLeftDegrees(degreeOffset*currentCheck), dist);
                if (rc.canMove(dest))
                {
                    damage = damageAtLocation(dest);
                    if (damage > 0 && damage < leastDamage)
                    {
                        leastDamage = damage;
                        bestUnsafe = dest;
                    }
                    if (damage == 0)
                    {
                        rc.move(dest);
                        //setIndicator(here, dest, 0, 255, 0);
                        return true;
                    }
                }
            }

            // Try the offset on the right side
            if (currentCheck <= checksRight)
            {
                dest = here.add(dir.rotateRightDegrees(degreeOffset*currentCheck), dist);
                if (rc.canMove(dest))
                {
                    damage = damageAtLocation(dest);
                    if (damage > 0 && damage < leastDamage)
                    {
                        leastDamage = damage;
                        bestUnsafe = dest;
                    }
                    if (damage == 0)
                    {
                        rc.move(dest);
                        //setIndicator(here, dest, 0, 255, 0);
                        return true;
                    }
                }
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        if (bestUnsafe != null && leastDamage <= damageAtLocation(here) && rc.canMove(bestUnsafe)) { //Not safe here so happy to move to another unsafe place
            rc.move(bestUnsafe);
            //setIndicator(here, bestUnsafe, 255, 0, 0);
            return true;
        }

        // A move never happened, so return false.
        return false;
    }







    /*
    * A tree is safe if we can hide in it
    * That means there has to be room
    */
    boolean isTreeSafe(TreeInfo t) throws GameActionException {
        if (Clock.getBytecodesLeft() < 1000) //This routine can take time so return false if we are short on time
            return false;

        if (t.getHealth() <= 10)
            return false;

        if (t.getRadius() < RobotType.SCOUT.bodyRadius) //Too small to hide in
            return false;

        //For trees the same size as us they are safe if no lumberjack is in range and no unit with bullets is within a stride of the tree edge
        for (RobotInfo r: robots)
        {
            if (r.type==RobotType.SCOUT)
            {
                float distanceToTree = r.getLocation().distanceTo(t.getLocation());
                float gapBetween = distanceToTree - t.getRadius() - r.getType().bodyRadius;
                if (gapBetween < 0)
                { //Occupied
                    indicate(t.getLocation(),255,0,0);
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

        for (RobotInfo r:robots)
        {
            if (r.getTeam() == enemy)
            {
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
        }

        if (nearestEnemy == null)
        { //There are no enemies in sight but we might be being shot at
            indicate(rc.getLocation(),255,255,255);
            return false;
        }
        /*
        //dodge
        if (damageAtLocation(rc.getLocation()) > 0) {
            //We are better off dodging by moving away
            shoot(nearestEnemy);
            safeDistance = nearestEnemy.getType().sensorRadius;// + rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET; //Move out of its sight radius
            MapLocation away = rc.getLocation().add(nearestEnemy.getLocation().directionTo(rc.getLocation()).rotateLeftDegrees(5), safeDistance);
            tryMove(away);
            return true;
        }*/

        MapLocation combatPosition = null;

        // If there is a threat ...
       // TODO this is simplyfication
        if (nearestDanger != null && canSenseMe(nearestDanger)) {
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


            if (nearestTree != null) { //Scouts can hide in trees
                indicate(rc.getLocation(),0,255,0);

                float bulletOffset = GameConstants.BULLET_SPAWN_OFFSET / 2;
                float dist = nearestTree.radius - RobotType.SCOUT.bodyRadius - bulletOffset;
                if (dist >= 0)
                    combatPosition = nearestTree.getLocation().add(nearestTree.getLocation().directionTo(dangerLoc), dist);
                //else
                //    combatPosition = nearestTree.getLocation().add(nearestTree.getLocation().directionTo(dangerLoc).opposite(), -dist);

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
                if (nearestLumberjack != null)
                {
                    if (myLocation.distanceTo(nearestLumberjack.getLocation()) < safeDistance)
                        combatPosition = nearestLumberjack.getLocation().add(nearestLumberjack.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                    else
                    {
                        if (SHOOTJACKS)
                            combatPosition = nearestLumberjack.getLocation().add(nearestLumberjack.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                        /*
                        else
                        {
                            safeDistance = RobotType.SCOUT.sensorRadius;
                            combatPosition = rc.getLocation().add(nearestDanger.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                        }*/
                    }
                    if (SHOOTJACKS)
                        target = nearestDanger;
                    //shoot(nearestLumberjack);
                }
                if (!nearestDanger.equals(nearestLumberjack) && canBeat(nearestDanger))
                {
                    indicate(nearestDanger.location,255,0,255);
                    combatPosition = dangerLoc.add(dangerLoc.directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
                    target = nearestDanger;
                    //shoot(nearestDanger);
                }
                else if (canShootMe(nearestDanger))
                {
                    indicate(rc.getLocation(),255,0,0);
                    RobotType t = nearestDanger.getType();
                    // safeDistance = t.sensorRadius + RobotType.SCOUT.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
                    safeDistance = RobotType.SCOUT.sensorRadius;
                    combatPosition = rc.getLocation().add(nearestDanger.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
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
                safeDistance = 2*RobotType.SCOUT.bodyRadius +target.type.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
                combatPosition = target.getLocation().add(target.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);

                //shoot(nearestGardener);
            }
            else if (nearestArchon != null)
            {
                indicate(nearestArchon.getLocation(),0,0,255);
                if (rc.getTeamBullets()>700)
                {
                    target = nearestArchon;
                    safeDistance+=target.getRadius()/2;
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
