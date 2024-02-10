package frc.robot.subsystems.intake;

import org.littletonrobotics.junction.AutoLog;

public interface IntakeIO {
    @AutoLog
    public static class IntakeIOInputs {
        public double appliedVoltage = 0.0;
        public double velocityRotPerSecond = 0.0;
        public double currentAmperage = 0.0;
    }

    public default void updateInputs(IntakeIOInputs inputs) {
    }

    public default void setVelocity(double velocityRotPerSecond) {
    }

    public default void setVoltage(double voltage) {
    }
}
