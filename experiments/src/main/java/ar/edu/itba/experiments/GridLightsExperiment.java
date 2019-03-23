package ar.edu.itba.experiments;

import static java.lang.Math.abs;

import ar.edu.itba.model.CounterPane;
import ar.edu.itba.model.LightsGridPane;
import ar.edu.itba.model.StartScreen;
import ar.edu.itba.model.movementPatterns.*;
import ar.edu.itba.senders.StimulusSender;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class GridLightsExperiment extends Application {

  private static final Random RANDOM = ThreadLocalRandom.current();
  private static final List<int[]> movements = Arrays.asList(new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1},
          new int[]{0, -1});
  private static final List<MovementPattern> MOVEMENT_PATTERNS = Arrays.asList(new LeftRightPattern(), new UpDownPattern(),
          (p,r,c) -> new int[]{0,0}, new ClockwisePattern(), new CounterClockwisePattern());
  private static final int MAX_ITERATION = 10;
  private static final int ROWS = 6;
  private static final int COLS = 6;
  private static final boolean SEND = false;
  private static final Duration STEP_DURATION = Duration.seconds(2);
  private static final String HOST = "10.17.2.185";
  private static final int PORT = 15361;

  private int iteration = 1;
  private int[] persecutorPosition;
  private int[] pursuedPosition;

  private BorderPane pane;
  private CounterPane counterPane;
  private LightsGridPane currentGrid;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    final StartScreen startScreen = new StartScreen();
    startScreen.setOnStart(() -> {
      pane.setCenter(counterPane);
      counterPane.startTimer();
    });

    counterPane = new CounterPane();
    counterPane.setOnTimerFinished(this::startExperiment);

    pane = new BorderPane(startScreen);
    pane.setBackground(
            new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

    primaryStage.setTitle("Square Lights");
    primaryStage.setScene(new Scene(pane));
    primaryStage.setResizable(false);
    primaryStage.setMaximized(true);
    setMaxSize(primaryStage);
    primaryStage.setFullScreen(true);
    primaryStage.show();
  }

  private static void setMaxSize(final Stage primaryStage) {
    final Screen screen = Screen.getPrimary();
    final Rectangle2D bounds = screen.getVisualBounds();

    primaryStage.setX(bounds.getMinX());
    primaryStage.setY(bounds.getMinY());
    primaryStage.setWidth(bounds.getWidth());
    primaryStage.setHeight(bounds.getHeight());
  }

  private void startExperiment() {
    persecutorPosition = newStartingPosition();
    pursuedPosition = newPursuedPosition();
    final MovementPattern movementPattern = MOVEMENT_PATTERNS.get(RANDOM.nextInt(MOVEMENT_PATTERNS.size()));
    currentGrid = new LightsGridPane(ROWS, COLS, persecutorPosition, pursuedPosition);
    pane.setCenter(currentGrid);

    final StimulusSender sender = new StimulusSender();
    if (SEND) {
      try {
        sender.open(HOST, PORT);
        sender.send(3L, 0L);
      } catch (final IOException e) {
        e.printStackTrace();
        return;
      }
    }

    final Timeline timeline = new Timeline();
    final KeyFrame keyFrame = new KeyFrame(STEP_DURATION, e -> {
      final int prevDistance = distanceToPursued(pursuedPosition);
      final List<int[]> validMovements = movements.stream()
              .filter(currentGrid::isValidOffset)
              .collect(Collectors.toList());
      final int[] movement = validMovements.get(RANDOM.nextInt(validMovements.size()));
      moveLightWithOffset(persecutorPosition, movement, currentGrid::movePersecutorWithOffset);
      if(!Arrays.equals(persecutorPosition, pursuedPosition)){
        moveLightWithOffset(pursuedPosition, movementPattern.getOffset(pursuedPosition, ROWS, COLS),
                currentGrid::movePursuedWithOffset);
      }


      if (distanceToPursued(pursuedPosition) == 0) {
        timeline.stop();
        if (SEND) {
          try {
            sender.send(4L, 0L);
            sender.close();
          } catch (Exception e1) {
            e1.printStackTrace();
          }
        }
        if (iteration < MAX_ITERATION) {
          iteration++;
          final Timeline tl = new Timeline(new KeyFrame(STEP_DURATION, oe -> {
            pane.setCenter(counterPane);
            counterPane.startTimer();
          }));
          tl.play();
        }
      } else {
        final int currentDistance = distanceToPursued (pursuedPosition);
        final long distanceDifference = prevDistance - currentDistance;
        if (SEND) {
          try {
            sender.send(distanceDifference > 0 ? 1 : 2, 0L);
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        }
      }
    });
    timeline.getKeyFrames().add(keyFrame);
    timeline.setCycleCount(Timeline.INDEFINITE);
    timeline.play();
  }

  private void moveLightWithOffset(int[] position, final int[] offset, final Consumer<int[]> consumer){
    position[0] += offset[0];
    position[1] += offset[1];
    consumer.accept(offset);
  }

  private int[] newStartingPosition() {
    return new int[]{RANDOM.nextInt(ROWS), RANDOM.nextInt(COLS)};
  }

  private int[] newPursuedPosition() {
    int[] pursuedPosition;

    do {
      pursuedPosition = new int[]{RANDOM.nextInt(ROWS), RANDOM.nextInt(COLS)};
    } while (distanceToPursued(pursuedPosition) <= 1);

    return pursuedPosition;
  }

  private int distanceToPursued(final int[] goalPosition) {
    return abs(persecutorPosition[0] - goalPosition[0]) + abs(persecutorPosition[1] - goalPosition[1]);
  }
}
