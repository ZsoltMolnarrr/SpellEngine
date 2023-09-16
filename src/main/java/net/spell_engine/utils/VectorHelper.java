package net.spell_engine.utils;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class VectorHelper {
    public static double angleBetween(Vec3d a, Vec3d b) {
        var cosineTheta = a.dotProduct(b) / (a.length() * b.length());
        var angle = Math.acos(cosineTheta) * (180.0 / Math.PI);
        if (Double.isNaN(angle)) {
            return 0;
        }
        return angle;
    }

    public static double angleWithSignBetween(Vec3d a, Vec3d b, Vec3d planeNormal) {
        var cosineTheta = a.dotProduct(b) / (a.length() * b.length());
        var angle = Math.toDegrees(Math.acos(cosineTheta));
        var cross = a.crossProduct(b);
        angle *= Math.signum(cross.dotProduct(planeNormal));
        if (Double.isNaN(angle)) {
            return 0;
        }
        return angle;
    }

    /**
     * Calculates distance vector FROM the given point TO the given box.
     */
    public static Vec3d distanceVector(Vec3d point, Box box) {
        double dx = 0;
        if (box.minX > point.x) {
            dx = box.minX - point.x;
        } else if (box.maxX < point.x) {
            dx = box.maxX - point.x;
        }
        double dy = 0;
        if (box.minY > point.y) {
            dy = box.minY - point.y;
        } else if (box.maxY < point.y) {
            dy = box.maxY - point.y;
        }
        double dz = 0;
        if (box.minZ > point.z) {
            dz = box.minZ - point.z;
        } else if (box.maxZ < point.z) {
            dz = box.maxZ - point.z;
        }
        return new Vec3d(dx, dy, dz);
    }

    /**
     * Rotates a vector towards another, by the maximum of a given amount.
     * @param vector
     * @param towards
     * @param angleToRotate angle in degrees
     * @return
     */
    public static Vec3d rotateTowards(Vec3d vector, Vec3d towards, double angleToRotate) {
        if (angleToRotate == 0) {
            return vector;
        }
        var originalVector = new Vec3d(vector.x, vector.y, vector.z);
        vector = vector.normalize();
        towards = towards.normalize();
        Vec3d rotated;
        var angleBetween = angleWithSignBetween(vector, towards, vector.crossProduct(towards));
        // System.out.println("Pre Angle between vectors: " + angleBetween);
        if (angleBetween == 0) {
            return originalVector;
        }
        if (angleBetween <= angleToRotate) {
            rotated = towards;
        } else {
            var v1 = vector;
            var towardsLength = Math.sin(Math.toRadians(angleToRotate)) / Math.cos(Math.toRadians(90.0 - angleBetween + angleToRotate));
            var v2 = towards.multiply(towardsLength);
           // System.out.println("Angle: " + angleBetween + " T':" + towardsLength);
            rotated = v1.add(v2).normalize();
        }
        rotated = rotated.multiply(originalVector.length());
        // System.out.println("Post Angle between vectors: " + angleBetween(rotated, towards));
        return rotated;
    }
}
