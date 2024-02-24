package frc.robot.subsystems.pivot;

import edu.wpi.first.math.geometry.Rotation2d;

public enum PivotLocation {
    INITIAL(Rotation2d.fromDegrees(26.0)),
    k36(Rotation2d.fromDegrees(36)),
    k45(Rotation2d.fromDegrees(45.0)),
    kSoftMin(Rotation2d.fromDegrees(28.0)),
    kSoftMax(Rotation2d.fromDegrees(85.0)),
    ;

    public Rotation2d angle;

    PivotLocation(Rotation2d angle) {
        this.angle = angle;
    }
}
