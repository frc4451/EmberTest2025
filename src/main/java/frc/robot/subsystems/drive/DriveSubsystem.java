// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.drive;

import java.util.function.Supplier;

import org.littletonrobotics.junction.Logger;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
import com.pathplanner.lib.util.PIDConstants;
import com.pathplanner.lib.util.ReplanningConfig;

import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.AdvantageKitConstants;
import frc.robot.Constants.DriveConstants;
import frc.robot.subsystems.vision.VisionSubsystem.VisionMeasurement;
import frc.utils.GarageUtils;

public class DriveSubsystem extends SubsystemBase {
    // Swerve Modules
    private final SwerveModuleIO[] m_modules = new SwerveModuleIO[4]; // FL, FR, RL, RR
    private final SwerveModuleIOInputsAutoLogged[] m_moduleInputs = new SwerveModuleIOInputsAutoLogged[] {
            new SwerveModuleIOInputsAutoLogged(),
            new SwerveModuleIOInputsAutoLogged(),
            new SwerveModuleIOInputsAutoLogged(),
            new SwerveModuleIOInputsAutoLogged(),
    };

    // Gyro
    private final SwerveGyroIO m_gyro;
    private final SwerveGyroIOInputsAutoLogged m_gyroInputs = new SwerveGyroIOInputsAutoLogged();

    // Odometry for tracking robot pose
    private Rotation2d m_trackedRotation = new Rotation2d();
    private final SwerveDrivePoseEstimator m_poseEstimator = new SwerveDrivePoseEstimator(
            DriveConstants.kDriveKinematics,
            m_trackedRotation,
            getModulePositions(),
            new Pose2d());

    private final Supplier<VisionMeasurement> m_visionSupplier;

    /** Creates a new DriveSubsystem. */
    public DriveSubsystem(Supplier<VisionMeasurement> visionSupplier) {
        m_visionSupplier = visionSupplier;

        // Configure AutoBuilder for PathPlanner
        AutoBuilder.configureHolonomic(
                this::getPose,
                this::resetPose,
                () -> DriveConstants.kDriveKinematics.toChassisSpeeds(getModuleStates()),
                this::runVelocity,
                new HolonomicPathFollowerConfig(
                        new PIDConstants(5.0, 0.0, 0.0), // Translation PID constants
                        new PIDConstants(5.0, 0.0, 0.0), // Rotation PID constants
                        DriveConstants.kMaxSpeedMetersPerSecond,
                        DriveConstants.kWheelBase / 2,
                        new ReplanningConfig()),
                () -> GarageUtils.isRedAlliance(),
                this);

        switch (AdvantageKitConstants.getMode()) {
            case REAL:
                m_modules[0] = new SwerveModuleSparks(
                        DriveConstants.kFrontLeftDrivingCanId,
                        DriveConstants.kFrontLeftTurningCanId,
                        DriveConstants.kFrontLeftChassisAngularOffset);

                m_modules[1] = new SwerveModuleSparks(
                        DriveConstants.kFrontRightDrivingCanId,
                        DriveConstants.kFrontRightTurningCanId,
                        DriveConstants.kFrontRightChassisAngularOffset);

                m_modules[2] = new SwerveModuleSparks(
                        DriveConstants.kRearLeftDrivingCanId,
                        DriveConstants.kRearLeftTurningCanId,
                        DriveConstants.kBackLeftChassisAngularOffset);

                m_modules[3] = new SwerveModuleSparks(
                        DriveConstants.kRearRightDrivingCanId,
                        DriveConstants.kRearRightTurningCanId,
                        DriveConstants.kBackRightChassisAngularOffset);

                m_gyro = new SwerveGyroPigeon2();

                break;

            case SIM:
                m_modules[0] = new SwerveModuleSim(DriveConstants.kFrontLeftChassisAngularOffset);
                m_modules[1] = new SwerveModuleSim(DriveConstants.kFrontRightChassisAngularOffset);
                m_modules[2] = new SwerveModuleSim(DriveConstants.kBackLeftChassisAngularOffset);
                m_modules[3] = new SwerveModuleSim(DriveConstants.kBackRightChassisAngularOffset);
                m_gyro = new SwerveGyroIO() {
                };
                break;

            case REPLAY:
            default:
                for (int i = 0; i < m_modules.length; i++) {
                    m_modules[i] = new SwerveModuleIO() {
                    };
                }
                m_gyro = new SwerveGyroIO() {
                };
                break;
        }
    }

    public SwerveModuleState[] getModuleStates() {
        SwerveModuleState[] states = new SwerveModuleState[m_modules.length];
        for (int i = 0; i < m_moduleInputs.length; i++) {
            states[i] = m_moduleInputs[i].state;
        }
        return states;
    }

    public SwerveModulePosition[] getModulePositions() {
        SwerveModulePosition[] positions = new SwerveModulePosition[m_modules.length];
        for (int i = 0; i < m_moduleInputs.length; i++) {
            positions[i] = m_moduleInputs[i].position;
        }
        return positions;
    }

    @Override
    public void periodic() {
        m_gyro.updateInputs(m_gyroInputs);
        Logger.processInputs("Drive/Gyro", m_gyroInputs);

        for (int i = 0; i < m_modules.length; i++) {
            m_modules[i].updateInputs(m_moduleInputs[i]);
            Logger.processInputs("Drive/Module" + Integer.toString(i), m_moduleInputs[i]);
        }

        SwerveModuleState[] states = getModuleStates();
        SwerveModulePosition[] positions = getModulePositions();

        Logger.recordOutput("SwerveStates/Measured", states);

        // If a gyro is connected we'll just read that directly.
        // Otherwise add to our tracked value by calculating a twist from modules.
        if (m_gyroInputs.isConnected) {
            m_trackedRotation = new Rotation2d(m_gyroInputs.yawPositionRad);
        } else {
            // The ChassisSpeeds represents the motion of the robot since the last
            // loop cycle in x, y, and theta based on only the modules,
            // without the gyro. The gyro is always disconnected in simulation.
            ChassisSpeeds speeds = DriveConstants.kDriveKinematics.toChassisSpeeds(states);
            m_trackedRotation = m_trackedRotation.plus(new Rotation2d(speeds.omegaRadiansPerSecond * 0.02));
        }

        m_poseEstimator.update(m_trackedRotation, positions);
        addVisionMeasurements();

        Logger.recordOutput("Odometry/Robot", getPose());
    }

    private void addVisionMeasurements() {
        Pose2d currentPose = getPose();

        VisionMeasurement visionMeasurement;
        while ((visionMeasurement = m_visionSupplier.get()) != null) {
            Pose2d visionPose = visionMeasurement.estimation().estimatedPose.toPose2d();
            m_poseEstimator.addVisionMeasurement(
                    // Ignore the vision pose's rotation
                    new Pose2d(visionPose.getTranslation(), currentPose.getRotation()),
                    visionMeasurement.estimation().timestampSeconds,
                    visionMeasurement.confidence());
        }
    }

    /**
     * Returns the currently-estimated pose of the robot.
     *
     * @return The pose.
     */
    public Pose2d getPose() {
        return m_poseEstimator.getEstimatedPosition();
    }

    /**
     * Resets the odometry to the specified pose.
     *
     * @param pose The pose to which to set the odometry.
     */
    public void resetPose(Pose2d pose) {
        m_poseEstimator.resetPosition(m_trackedRotation, getModulePositions(), pose);
    }

    /**
     * Runs the drive at the desired velocity.
     *
     * @param speeds Speeds in meters/sec
     */
    public void runVelocity(ChassisSpeeds speeds) {
        // Calculate module setpoints
        ChassisSpeeds discreteSpeeds = ChassisSpeeds.discretize(speeds, 0.02);
        SwerveModuleState[] setpointStates = DriveConstants.kDriveKinematics.toSwerveModuleStates(discreteSpeeds);

        // Send setpoints to modules
        setModuleStates(setpointStates);
    }

    /**
     * Runs the drive at the desired velocity.
     */
    public void runVelocity(
            double vxMetersPerSecond,
            double vyMetersPerSecond,
            double omegaRadiansPerSecond) {
        runVelocity(new ChassisSpeeds(vxMetersPerSecond, vyMetersPerSecond, omegaRadiansPerSecond));
    }

    /**
     * Runs the drive at the desired velocity. (Field Relative)
     *
     * @param speeds Speeds in meters/sec
     */
    public void runVelocityField(ChassisSpeeds speeds) {
        runVelocity(ChassisSpeeds.fromFieldRelativeSpeeds(speeds, m_trackedRotation));
    }

    /**
     * Runs the drive at the desired velocity. (Field Relative)
     *
     */
    public void runVelocityField(
            double vxMetersPerSecond,
            double vyMetersPerSecond,
            double omegaRadiansPerSecond) {
        runVelocityField(new ChassisSpeeds(vxMetersPerSecond, vyMetersPerSecond, omegaRadiansPerSecond));
    }

    /**
     * Sets the wheels into an X formation to prevent movement.
     */
    public void setCross() {
        SwerveModuleState[] desiredStates = new SwerveModuleState[] {
                new SwerveModuleState(0, Rotation2d.fromDegrees(45)),
                new SwerveModuleState(0, Rotation2d.fromDegrees(-45)),
                new SwerveModuleState(0, Rotation2d.fromDegrees(-45)),
                new SwerveModuleState(0, Rotation2d.fromDegrees(45)),
        };

        setModuleStates(desiredStates);
    }

    /**
     * Sets the swerve ModuleStates.
     *
     * @param desiredStates The desired SwerveModule states.
     */
    public void setModuleStates(SwerveModuleState[] desiredStates) {
        SwerveDriveKinematics.desaturateWheelSpeeds(
                desiredStates, DriveConstants.kMaxSpeedMetersPerSecond);
        for (int i = 0; i < desiredStates.length; i++) {
            m_modules[i].setDesiredState(desiredStates[i]);
        }

        // Log setpoint states
        Logger.recordOutput("SwerveStates/Setpoints", desiredStates);
    }

    /** Zeroes the heading of the robot. */
    public void zeroHeading() {
        m_gyro.zero();
        // If no gyro is connected we have to manually reset our tracked rotation.
        if (!m_gyroInputs.isConnected) {
            m_trackedRotation = new Rotation2d();
        }
    }

    /**
     * Returns the gyro heading. If none is connected always return 0.
     */
    public Rotation2d getHeading() {
        return new Rotation2d(m_gyroInputs.isConnected ? m_gyroInputs.yawPositionRad : 0);
    }

    /**
     * Returns the turn rate of the robot in radians per second.
     */
    public double getTurnRate() {
        return m_gyroInputs.yawVelocityRadPerSec;
    }
}