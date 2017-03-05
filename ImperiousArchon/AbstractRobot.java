package ImperiousArchon;
import battlecode.common.*;

/**
 * Created by jakub on 02.03.2017.
 */
public abstract class AbstractRobot {
    RobotController rc;
    RobotInfo[] robots; //Cached result from senseNearbyRobots
    TreeInfo[] trees; //Cached result from senseNearbyTree
    BulletInfo[] bullets; //Cached result from senseNearbyBullets
    MapLocation rallyPoint = null;


    public AbstractRobot(RobotController rc)
    {
        this.rc= rc;
    }

    abstract void run() throws GameActionException;

    void checkWin() throws GameActionException {
        // Go for the win if we have enough bullets
        int vps = rc.getTeamVictoryPoints();
        float bullets = rc.getTeamBullets();
        float exchangeRate =  rc.getVictoryPointCost();
        if (rc.getRoundNum() >= rc.getRoundLimit()-1
                || (int)(bullets/exchangeRate) + vps >= GameConstants.VICTORY_POINTS_TO_WIN)
        {
            rc.donate(bullets);
        } else if (bullets > 1000 && rc.getRoundNum() > 200)
        {
            //Only donate when surplus
            int newVps = (int)((bullets - 1000)/exchangeRate);
            rc.donate(newVps*exchangeRate);
        }
    }

    void sense() throws GameActionException {
        robots = rc.senseNearbyRobots();
        trees = rc.senseNearbyTrees();
        bullets = rc.senseNearbyBullets();

    }



    float lumberjackRange() {
        return rc.getType().bodyRadius + RobotType.LUMBERJACK.strideRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS;
    }

    /*
    * shoot works out the optimum fire pattern based on the size of the target and its distance from us then shoots
    * avoiding friendly fire
    *
    * If the enemy is really close we may have a guaranteed hit of one or more bullets depending on its stride
    * If it is further away we may need to fire multiple shots to guarantee hitting it with one bullet
    */
    void shoot(RobotInfo target) throws GameActionException
    {
        //debug(1, "Shooting at " + target);

        if (target == null || !haveAmmo())
            return;

        MapLocation targetLoc = target.getLocation();
        MapLocation myLocation = rc.getLocation();
        Direction dir = myLocation.directionTo(targetLoc);
        float dist = myLocation.distanceTo(targetLoc);
        int shot = processShot(dir, target);

        if (shot < 0)
        {
            //System.out.print("couldnt fire");
            return;
        }
        //Look at the distance to target and its size to determine if it can dodge
        //Pentad fires 5 bullets with 15 degrees between each one (spread originating from the centre of the robot firing)
        //Triad fires 3 bullets with 20 degrees between each one
        //We can work out the angle either side of the centre of the target at which we hit
        float spreadAngle = (float) Math.asin(target.getType().bodyRadius/dist);
        int shotsToFire = 0;
        Direction shotDir = dir;
        //debug(3, "shoot: target " + target +  " dist=" + dist + " spreadAngle = " + spreadAngle + " (" + Math.toDegrees((double)spreadAngle) + ")");
        if (shot == 1)
        { //can be dodged
            if (rc.canFireTriadShot() && dist <= target.getType().bodyRadius / Math.sin(Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES/2)))
            {
                shotsToFire = 3;
                //debug (3, "Firing 3 - 1 should hit");
            }
            else if (rc.canFirePentadShot() && dist <= target.getType().bodyRadius / Math.sin(Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES/2)))
            {
                shotsToFire = 5;
                //debug (3, "Firing 5 - 1 should hit");
            }
        }
        else if (rc.canFirePentadShot() && 2*spreadAngle >= Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*4))
        { //All 5 shots will hit
            shotsToFire = 5;
            //debug (3, "Firing 5 - all should hit");
        }
        else if (rc.canFirePentadShot() && 2*spreadAngle > Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*3))
        { //4 shots will hit
            shotsToFire = 5;
            shotDir.rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES/2);
            //debug (3, "Firing 5 - 4 should hit");
        }
        else if (rc.canFireTriadShot() && 2*spreadAngle > Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES*2))
        { //All 3 triad shots will hit
            shotsToFire = 3;
            //debug (3, "Firing 3 - all should hit");
        }
        /*else if (rc.canFirePentadShot() && 2*spreadAngle > Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*2))
        { //3 of 5 shots will hit)
            shotsToFire = 5;
            //debug (3, "Firing 5 - 3 should hit");
        }*/
        else if (rc.canFireTriadShot() && 2*spreadAngle > Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES*2))
        { //2 of a triad shots will hit
            shotsToFire = 3;
            shotDir.rotateLeftDegrees(GameConstants.TRIAD_SPREAD_DEGREES/2);
            //debug (3, "Firing 3 - 2 should hit");
        }
        /*
        else if (rc.canFirePentadShot() && 2*spreadAngle > Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES))
        { //2 of 5 shots will hit
            shotsToFire = 5;
            shotDir.rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES/2);
            //debug (3, "Firing 5 - 2 should hit");
        }*/
        else if (rc.canFireSingleShot())
        {
            shotsToFire = 1;
            rc.fireSingleShot(shotDir);
            //debug (3, "Firing 1 shot");
        }
        if (shotsToFire == 5)
        {
            rc.firePentadShot(shotDir);
            //debug(2, "Shooting 5 shots at " + target);
        }
        else if (shotsToFire == 3)
        {
            rc.fireTriadShot(shotDir);
            //debug(2, "Shooting 3 shots at " + target);
        }
        if (shotsToFire > 0)
        { //We shot so update bullet info
            bullets = rc.senseNearbyBullets();
        }
    }

    /*
     * Check to see if a bullet fired from here will hit an enemy first (rather than a tree or an ally)
     * 2: hit
     * 1: might miss
     * 0: enemy tree
     * -1: miss
     * -2: neutral tree
     * -3: ally tree
     * -4: ally unit
     */
    int processShot(Direction dir, RobotInfo target)
    {
        float hitDist=-5;
        int result= willHit(rc.getLocation().add(dir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET), dir,hitDist);

        if (result <=0)
            return result;

        int turnsBeforeHit = (int) Math.ceil(hitDist/ rc.getType().bulletSpeed);
        if (hitDist % rc.getType().bulletSpeed == 0)
            turnsBeforeHit--;
        if (turnsBeforeHit * target.getType().strideRadius <= target.getType().bodyRadius)
            return 2;

        return 1; //Bullet will probably miss (can be dodged)
    }

    boolean canShootMe(RobotInfo enemy)
    {
        float hitDist=-5;
        MapLocation loc = enemy.getLocation();
        Direction dir =  loc.directionTo(rc.getLocation());
        float dist = loc.distanceTo(rc.getLocation());
        int result= willHit(loc.add(dir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET), dir,hitDist);

        if (result == -4 && dist > hitDist -0.0001 && dist < hitDist + 0.0001)
            return true;
        return false;
    }
    boolean haveAmmo()
    {
        float ammo = rc.getTeamBullets();
        return (ammo >= 1);
    }

    /*
     * Check to see if a bullet fired from here will hit an enemy first (rather than a tree or an ally)
     * 1: hit
     * 0: enemy tree
     * -1: miss
     * -2: neutral tree
     * -3: ally tree
     * -4: ally unit
     */
    int willHit(MapLocation loc, Direction dir, float dist) {
        TreeInfo nearestTree = null;
        float nearestHitTreeDist = -1;
        RobotInfo nearestUnit = null;
        float nearestHitUnitDist = -1;

        //Check each tree to see if it will hit it
        for (TreeInfo t:trees)
        {
            nearestHitTreeDist = Utils.calcHitDist(loc, loc.add(dir, rc.getType().sensorRadius*2), t.getLocation(), t.getRadius());
            if (nearestHitTreeDist >= 0)
            {
                nearestTree = t;
                break;
            }
        }

        for (RobotInfo r:robots)
        {
            nearestHitUnitDist = Utils.calcHitDist(loc, loc.add(dir, rc.getType().sensorRadius*2), r.getLocation(), r.getRadius());
            if (nearestHitUnitDist >= 0)
            {
                nearestUnit = r;
                break;
            }
        }

        if (nearestUnit != null && (nearestTree == null || nearestHitUnitDist <= nearestHitTreeDist))
        { //We hit a robot
            dist = nearestHitUnitDist;
            if (nearestUnit.getTeam() != rc.getTeam())
                return 1;
            else
                return -4;
        }

        if (nearestTree != null)
        {
            dist = nearestHitTreeDist;
            if (nearestTree.getTeam() == rc.getTeam().opponent())
                return 0;
            else if (nearestTree.getTeam() == rc.getTeam())
                return -3;
            else
                return -2;
        }

        return -1;
    }

    /*
    * Returns the bullet damage at this location
    */
    float bulletDamage(MapLocation loc)
    {
        float damage = 0;
        int cutOff = 7000;

        for (BulletInfo b:bullets) {
            //Will the bullet hit us?
            //Calc nearest point this bullet gets to us
            float angle = Math.abs(b.getLocation().directionTo(loc).radiansBetween(b.getDir()));
            if (angle < Math.PI / 2)
            {
                float hypot = b.getLocation().distanceTo(loc);
                float nearest = (float) (hypot * Math.sin(angle));
                if (nearest <= rc.getType().bodyRadius)
                    damage += b.getDamage();
            }

            if (Clock.getBytecodesLeft() < cutOff)
                break;
        }

        return damage;
    }

    int lumberjacksInRange(MapLocation loc) {
        int enemyLumberjacks = 0;


        float range =lumberjackRange();
        for (RobotInfo r:robots)
        {
            if (r.getType() == RobotType.LUMBERJACK && loc.distanceTo(r.getLocation()) <= range)
            {
                if (r.getTeam() != rc.getTeam())
                    enemyLumberjacks++;
            }
        }
        return enemyLumberjacks;
    }

    /*
    * damageAtLocation
    *
    * Works out if we will be shot at the given location
    * or if a lumberjack is in range
    * or if an enemy going after us can fire and hit us this turn
    *
    * Returns amount of damage we would take at this location
    */
    float damageAtLocation(MapLocation loc) throws GameActionException
    {
        float damage = 0;

        int lumberjacks = lumberjacksInRange(loc);
        if (lumberjacks > 0)
        {
            damage += lumberjacks * RobotType.LUMBERJACK.attackPower;
        }

        float bullets = bulletDamage(loc);
        if (bullets > 0)
        {
            damage += bullets;
        }


        Team enemy = rc.getTeam().opponent();
        float startDamage = damage;
        for (RobotInfo r:robots)
        {
            if (r.getTeam() == enemy && r.getType().bulletSpeed > 0)
            { //Only care about robots that can shoot
                float dist = r.getLocation().distanceTo(loc) - rc.getType().bodyRadius;
                float range = r.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET + r.getType().bulletSpeed + r.getType().strideRadius;
                if (range >= dist)
                {
                    damage += r.getType().attackPower;
                }
            }
        }
        //if (damage > 0)
        //	setIndicator(loc, 128, 0, 0);


        return damage;
    }

    boolean canBeat(RobotInfo enemy)
    {
        if (!rc.getType().canAttack())
            return false;
        if (!enemy.getType().canAttack())
            return true;

        int turnsToKill = (int) (enemy.getHealth() / rc.getType().attackPower);
        int turnsToDie = (int) (rc.getHealth() / enemy.getType().attackPower);
        return turnsToKill <= turnsToDie;
    }

    boolean canBeatEasily(RobotInfo enemy)
    {
        if (!rc.getType().canAttack())
            return false;
        if (!enemy.getType().canAttack())
            return true;
        int turnsToKill = (int) (enemy.getHealth() / rc.getType().attackPower);
        int turnsToDie = (int) (rc.getHealth() / enemy.getType().attackPower);
        return 2*turnsToKill <= turnsToDie;
    }

    boolean canSenseMe(RobotInfo enemy)
    {
        return  (enemy.getType().sensorRadius > enemy.getLocation().distanceTo(rc.getLocation()));
    }
}
