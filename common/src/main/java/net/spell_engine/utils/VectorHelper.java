package net.spell_engine.utils;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VectorHelper {
    public static double angleBetween(Vec3 a, Vec3 b) {
        var cosineTheta = a.dot(b) / (a.length() * b.length());
        var angle = Math.acos(cosineTheta) * (180.0 / Math.PI);
        if (Double.isNaN(angle)) {
            return 0;
        }
        return angle;
    }

    public static double angleWithSignBetween(Vec3 a, Vec3 b, Vec3 planeNormal) {
        var cosineTheta = a.dot(b) / (a.length() * b.length());
        var angle = Math.toDegrees(Math.acos(cosineTheta));
        var cross = a.cross(b);
        angle *= Math.signum(cross.dot(planeNormal));
        if (Double.isNaN(angle)) {
            return 0;
        }
        return angle;
    }

    /**
     * Calculates distance vector FROM the given point TO the given box.
     */
    public static Vec3 distanceVector(Vec3 point, AABB box) {
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
        return new Vec3(dx, dy, dz);
    }

    /**
     * Rotates a vector towards another, by the maximum of a given amount.
     * @param vector
     * @param towards
     * @param angleToRotate angle in degrees
     * @return
     */
    public static Vec3 rotateTowards(Vec3 vector, Vec3 towards, double angleToRotate) {
        if (angleToRotate == 0) {
            return vector;
        }
        var originalVector = new Vec3(vector.x, vector.y, vector.z);
        vector = vector.normalize();
        towards = towards.normalize();
        Vec3 rotated;
        var angleBetween = angleWithSignBetween(vector, towards, vector.cross(towards));
        // System.out.println("Pre Angle between vectors: " + angleBetween);
        if (angleBetween == 0) {
            return originalVector;
        }
        if (angleBetween <= angleToRotate) {
            rotated = towards;
        } else {
            var v1 = vector;
            var towardsLength = Math.sin(Math.toRadians(angleToRotate)) / Math.cos(Math.toRadians(90.0 - angleBetween + angleToRotate));
            var v2 = towards.scale(towardsLength);
           // System.out.println("Angle: " + angleBetween + " T':" + towardsLength);
            rotated = v1.add(v2).normalize();
        }
        rotated = rotated.scale(originalVector.length());
        // System.out.println("Post Angle between vectors: " + angleBetween(rotated, towards));
        return rotated;
    }

    public static Vec3 axisFromRotation(float yaw, float pitch) {
        double yawRadians = Math.toRadians(-yaw);
        double pitchRadians = Math.toRadians(-pitch);

        double x = -Math.sin(yawRadians) * Math.cos(pitchRadians);
        double y = -Math.sin(pitchRadians);
        double z = Math.cos(yawRadians) * Math.cos(pitchRadians);

        return new Vec3(x, y, z).normalize();
    }

    public static Vec3 rotateAround(Vec3 vector, float angleDegrees, float yaw, float pitch) {
        Vec3 axisOfRotation = axisFromRotation(yaw, pitch);
        // Now, rotate the vector around this axis by the given angle
        return rotateAround(vector, axisOfRotation, angleDegrees);
    }

    public static Vec3 rotateAround(Vec3 vector, Vec3 axisOfRotation, double angleDegrees) {
        double angleRadians = Math.toRadians(angleDegrees);
        double sinHalfAngle = Math.sin(angleRadians / 2);
        double cosHalfAngle = Math.cos(angleRadians / 2);

        // Quaternion components for rotation
        double rx = axisOfRotation.x * sinHalfAngle;
        double ry = axisOfRotation.y * sinHalfAngle;
        double rz = axisOfRotation.z * sinHalfAngle;
        double rw = cosHalfAngle;

        // Inverse of the quaternion for rotation
        double invRx = -rx, invRy = -ry, invRz = -rz, invRw = rw;

        // Rotate vector using p' = qpq^(-1)
        double[] q = multiplyQuaternions(new double[]{rx, ry, rz, rw}, new double[]{vector.x, vector.y, vector.z, 0});
        double[] p = multiplyQuaternions(q, new double[]{invRx, invRy, invRz, invRw});

        // Return the rotated vector
        return new Vec3(p[0], p[1], p[2]);
    }

    private static double[] multiplyQuaternions(double[] q1, double[] q2) {
        double x = q1[3] * q2[0] + q1[0] * q2[3] + q1[1] * q2[2] - q1[2] * q2[1];
        double y = q1[3] * q2[1] + q1[1] * q2[3] + q1[2] * q2[0] - q1[0] * q2[2];
        double z = q1[3] * q2[2] + q1[2] * q2[3] + q1[0] * q2[1] - q1[1] * q2[0];
        double w = q1[3] * q2[3] - q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2];
        return new double[]{x, y, z, w};
    }

    public static double yawFromNormalized(Vec3 vector) {
        return Math.toDegrees(Math.atan2(-vector.x, vector.z));
    }

    public static double pitchFromNormalized(Vec3 vector) {
        return Math.toDegrees(-Math.asin(vector.y));
    }
}
