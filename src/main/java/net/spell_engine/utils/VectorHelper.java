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

    public static Vec3d axisFromRotation(float yaw, float pitch) {
        double yawRadians = Math.toRadians(-yaw);
        double pitchRadians = Math.toRadians(-pitch);

        double x = -Math.sin(yawRadians) * Math.cos(pitchRadians);
        double y = -Math.sin(pitchRadians);
        double z = Math.cos(yawRadians) * Math.cos(pitchRadians);

        return new Vec3d(x, y, z).normalize().negate();
    }

    public static Vec3d rotateAround(Vec3d vector, float angleDegrees, float yaw, float pitch) {
        Vec3d axisOfRotation = axisFromRotation(yaw, pitch);
        // Now, rotate the vector around this axis by the given angle
        return rotateAround(vector, axisOfRotation, angleDegrees);
    }

    public static Vec3d rotateAround(Vec3d vector, Vec3d axisOfRotation, double angleDegrees) {
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
        return new Vec3d(p[0], p[1], p[2]);
    }

    private static double[] multiplyQuaternions(double[] q1, double[] q2) {
        double x = q1[3] * q2[0] + q1[0] * q2[3] + q1[1] * q2[2] - q1[2] * q2[1];
        double y = q1[3] * q2[1] + q1[1] * q2[3] + q1[2] * q2[0] - q1[0] * q2[2];
        double z = q1[3] * q2[2] + q1[2] * q2[3] + q1[0] * q2[1] - q1[1] * q2[0];
        double w = q1[3] * q2[3] - q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2];
        return new double[]{x, y, z, w};
    }



    public static Vec3d rotateVectorToSpace(Vec3d vector, Vec3d spaceAxis, Vec3d targetAxis) {
        // Calculate rotation axis and angle
        Vec3d rotationAxis = spaceAxis.crossProduct(targetAxis);
        double angle = Math.acos(spaceAxis.dotProduct(targetAxis));

        // Convert angle-axis to quaternion
        Vec3d quaternion = angleAxisToQuaternion(rotationAxis, angle);

        // Apply quaternion rotation to vector
        return rotateVectorByQuaternion(vector, quaternion);
    }

    private static Vec3d angleAxisToQuaternion(Vec3d axis, double angle) {
        double halfAngle = angle / 2.0;
        double sinHalfAngle = Math.sin(halfAngle);
        return new Vec3d(
                axis.x * sinHalfAngle,
                axis.y * sinHalfAngle,
                axis.z * sinHalfAngle
        ).normalize(); // The W component of the quaternion is cos(halfAngle), but it's not needed for this rotation formula
    }

    private static Vec3d rotateVectorByQuaternion(Vec3d vector, Vec3d quaternion) {
        // Simplified rotation using quaternion (ignoring W component simplification)
        Vec3d cross = quaternion.crossProduct(vector);
        return vector.add(cross.multiply(2));
    }

}
