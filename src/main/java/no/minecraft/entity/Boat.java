package no.minecraft.entity;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

public class Boat {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private float yaw = 0.0f; // degrees
    private boolean dead = false;
    private int health = 4;

    private Player driver = null;
    private Mob passengerMob = null;

    public Boat(float x, float y, float z, float yaw) {
        this.position.set(x, y, z);
        this.yaw = yaw;
    }

    public Vector3f getPosition() { return position; }
    public Vector3f getVelocity() { return velocity; }
    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public boolean isDead() { return dead; }
    public Player getDriver() { return driver; }
    public void setDriver(Player driver) { this.driver = driver; }
    public Mob getPassengerMob() { return passengerMob; }
    public void setPassengerMob(Mob mob) { this.passengerMob = mob; }

    public void update(float dt, World world, Player player) {
        if (dead) return;

        // Check water at current position
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        BlockType currentBlock = world.getBlock(bx, by, bz);
        BlockType belowBlock = world.getBlock(bx, by - 1, bz);

        boolean inWater = currentBlock == BlockType.WATER || belowBlock == BlockType.WATER;

        if (inWater) {
            // Find water surface level
            float targetWaterY = (currentBlock == BlockType.WATER) ? (by + 1.0f) : (float) by;
            targetWaterY -= 0.15f; // sit slightly submerged in water

            // Buoyancy pull
            float dy = targetWaterY - position.y;
            velocity.y += dy * 12.0f * dt;
            velocity.y *= (1.0f - 4.0f * dt); // water damping

            // Low water drag for fast, smooth gliding
            velocity.x *= (1.0f - 1.2f * dt);
            velocity.z *= (1.0f - 1.2f * dt);
        } else {
            // On land / air
            velocity.y -= 22.0f * dt; // Gravity
            velocity.x *= (1.0f - 5.5f * dt); // Land friction
            velocity.z *= (1.0f - 5.5f * dt);
        }

        // Steer / drive if player is onboard
        if (driver != null) {
            float turnSpeed = inWater ? 135.0f : 80.0f; // degrees per second
            boolean turningLeft = driver.isMovingLeft();
            boolean turningRight = driver.isMovingRight();

            if (turningLeft && !turningRight) {
                yaw -= turnSpeed * dt;
                driver.getCamera().rotate(-turnSpeed * dt, 0.0f);
            } else if (turningRight && !turningLeft) {
                yaw += turnSpeed * dt;
                driver.getCamera().rotate(turnSpeed * dt, 0.0f);
            } else {
                // Smoothly align boat yaw with camera look direction (allows seamless mouse steering)
                float camYaw = driver.getCamera().getYaw();
                float diff = camYaw - yaw;
                while (diff < -180.0f) diff += 360.0f;
                while (diff > 180.0f) diff -= 360.0f;
                if (driver.isMovingForward() || driver.isMovingBackward()) {
                    yaw += diff * Math.min(1.0f, 9.0f * dt);
                } else {
                    yaw += diff * Math.min(1.0f, 4.0f * dt);
                }
            }

            float rad = (float) Math.toRadians(yaw);
            float fwdX = (float) Math.cos(rad);
            float fwdZ = (float) Math.sin(rad);

            float accel = inWater ? 22.0f : 8.0f;
            float maxSpeed = inWater ? 9.5f : 2.5f;

            float moveX = 0;
            float moveZ = 0;
            if (driver.isMovingForward()) {
                moveX += fwdX;
                moveZ += fwdZ;
            }
            if (driver.isMovingBackward()) {
                moveX -= fwdX * 0.5f;
                moveZ -= fwdZ * 0.5f;
            }

            velocity.x += moveX * accel * dt;
            velocity.z += moveZ * accel * dt;

            // Reorient existing momentum along the boat's heading so it carves smooth turns
            float hSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (hSpeed > 0.05f) {
                float turnResponse = inWater ? 8.0f : 4.0f;
                float dot = (velocity.x * fwdX + velocity.z * fwdZ) / hSpeed;
                float sign = (dot < -0.3f && driver.isMovingBackward()) ? -1.0f : 1.0f;
                float targetVx = fwdX * hSpeed * sign;
                float targetVz = fwdZ * hSpeed * sign;

                velocity.x += (targetVx - velocity.x) * Math.min(1.0f, turnResponse * dt);
                velocity.z += (targetVz - velocity.z) * Math.min(1.0f, turnResponse * dt);
            }

            // Clamp horizontal speed
            hSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (hSpeed > maxSpeed) {
                velocity.x = (velocity.x / hSpeed) * maxSpeed;
                velocity.z = (velocity.z / hSpeed) * maxSpeed;
            }

            // If player sneaks, dismount
            if (driver.isSneaking()) {
                dismountDriver();
            }
        }

        // Apply movement with simple collision
        moveWithCollision(dt, world);

        // Update driver position
        if (driver != null) {
            driver.getPosition().set(position.x, position.y + 0.35f, position.z);
            driver.getVelocity().set(velocity);
        }

        // Check for passenger mob or trap nearby mob
        if (passengerMob != null) {
            if (passengerMob.isDead()) {
                passengerMob.setRidingBoat(null);
                passengerMob = null;
            } else {
                float rad = (float) Math.toRadians(yaw);
                // Mob sits in front of the driver
                float mobX = position.x + (float) Math.cos(rad) * 0.45f;
                float mobZ = position.z + (float) Math.sin(rad) * 0.45f;
                passengerMob.getPosition().set(mobX, position.y + 0.15f, mobZ);
                passengerMob.getVelocity().set(0, 0, 0);
            }
        } else {
            // Try to catch nearby mobs walking into the boat
            for (Mob m : world.getMobs()) {
                if (m.isDead() || m.getRidingBoat() != null) continue;
                float dx = m.getPosition().x - position.x;
                float dy = m.getPosition().y - position.y;
                float dz = m.getPosition().z - position.z;
                if (dx * dx + dz * dz < 1.4f && Math.abs(dy) < 1.0f) {
                    passengerMob = m;
                    m.setRidingBoat(this);
                    break;
                }
            }
        }
    }

    private void moveWithCollision(float dt, World world) {
        float nextX = position.x + velocity.x * dt;
        float nextY = position.y + velocity.y * dt;
        float nextZ = position.z + velocity.z * dt;

        float halfW = 0.55f;
        // X axis collision
        if (!isSolidAt(world, nextX - halfW, position.y + 0.1f, position.z - halfW) &&
            !isSolidAt(world, nextX + halfW, position.y + 0.1f, position.z - halfW) &&
            !isSolidAt(world, nextX - halfW, position.y + 0.1f, position.z + halfW) &&
            !isSolidAt(world, nextX + halfW, position.y + 0.1f, position.z + halfW)) {
            position.x = nextX;
        } else {
            velocity.x = 0;
        }

        // Z axis collision
        if (!isSolidAt(world, position.x - halfW, position.y + 0.1f, nextZ - halfW) &&
            !isSolidAt(world, position.x + halfW, position.y + 0.1f, nextZ - halfW) &&
            !isSolidAt(world, position.x - halfW, position.y + 0.1f, nextZ + halfW) &&
            !isSolidAt(world, position.x + halfW, position.y + 0.1f, nextZ + halfW)) {
            position.z = nextZ;
        } else {
            velocity.z = 0;
        }

        // Y axis collision
        if (!isSolidAt(world, position.x, nextY, position.z)) {
            position.y = nextY;
        } else {
            position.y = (float) Math.floor(nextY) + 1.0f;
            velocity.y = 0;
        }
    }

    private boolean isSolidAt(World world, float x, float y, float z) {
        BlockType b = world.getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return b != null && b.isSolid() && b != BlockType.WATER && b != BlockType.LAVA;
    }

    public void mountDriver(Player p) {
        if (driver != null && driver != p) return;
        this.driver = p;
        p.setRidingBoat(this);
    }

    public void dismountDriver() {
        if (driver != null) {
            Player p = driver;
            driver = null;
            p.setRidingBoat(null);
            // Place player slightly outside boat
            float rad = (float) Math.toRadians(yaw + 90);
            p.getPosition().add((float) Math.cos(rad) * 0.9f, 0.4f, (float) Math.sin(rad) * 0.9f);
        }
    }

    public void hit(World world) {
        health--;
        if (health <= 0) {
            breakBoat(world);
        }
    }

    public void breakBoat(World world) {
        if (dead) return;
        dead = true;
        if (driver != null) {
            driver.setRidingBoat(null);
            driver = null;
        }
        if (passengerMob != null) {
            passengerMob.setRidingBoat(null);
            passengerMob = null;
        }
        world.spawnItemDrop(position.x, position.y + 0.3f, position.z, BlockType.BOAT, 1);
        no.minecraft.sound.SoundManager.getInstance().play("break_wood", 0.9f);
    }
}
