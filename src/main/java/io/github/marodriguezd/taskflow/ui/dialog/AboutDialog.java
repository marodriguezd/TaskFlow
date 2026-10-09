package io.github.marodriguezd.taskflow.ui.dialog;

import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.ui.component.Icons;
import io.github.marodriguezd.taskflow.ui.i18n.Messages;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import java.awt.Desktop;
import java.net.URI;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Modal dialog showing application information and TaskFlow legal notices. */
public class AboutDialog {
    private static final String GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html";

    private final Stage stage;
    private double dragOffsetX;
    private double dragOffsetY;

    public AboutDialog(Stage owner, ThemeManager themeManager) {
        this(owner, themeManager, UserPreferences.DEFAULT_GLASS_OPACITY, null);
    }

    public AboutDialog(
            Stage owner,
            ThemeManager themeManager,
            double currentGlassOpacity,
            Consumer<Double> onOpacityChanged) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(Messages.get("about.title"));

        VBox card = new VBox(12);
        card.getStyleClass().add("dialog-card");
        card.setPadding(new Insets(18, 20, 16, 20));
        card.setPrefWidth(460);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(Messages.get("about.title"));
        title.getStyleClass().add("about-dialog-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button closeButton = new Button();
        closeButton.getStyleClass().addAll("icon-button", "close-button");
        closeButton.setGraphic(Icons.close(10));
        closeButton.setTooltip(new Tooltip(Messages.get("about.close")));
        closeButton.setOnAction(e -> stage.close());
        header.getChildren().addAll(title, spacer, closeButton);

        Label appName = new Label(Messages.get("about.app"));
        appName.getStyleClass().add("about-app-name");

        Label description = new Label(Messages.get("about.description"));
        description.getStyleClass().add("about-text");
        description.setWrapText(true);

        VBox opacityBox = new VBox(6);
        Label opacityLabel = new Label(Messages.get("about.glass.opacity"));
        opacityLabel.getStyleClass().add("about-label");

        Slider opacitySlider =
                new Slider(
                        UserPreferences.MIN_GLASS_OPACITY * 100,
                        UserPreferences.MAX_GLASS_OPACITY * 100,
                        currentGlassOpacity * 100);
        opacitySlider.getStyleClass().add("opacity-slider");
        opacitySlider.setBlockIncrement(5);
        HBox.setHgrow(opacitySlider, javafx.scene.layout.Priority.ALWAYS);

        Label opacityValue = new Label(Math.round(opacitySlider.getValue()) + "%");
        opacityValue.getStyleClass().add("about-text");
        opacityValue.setMinWidth(42);

        opacitySlider
                .valueProperty()
                .addListener(
                        (obs, oldVal, newVal) -> {
                            int percent = (int) Math.round(newVal.doubleValue());
                            opacityValue.setText(percent + "%");
                            if (onOpacityChanged != null) {
                                onOpacityChanged.accept(newVal.doubleValue() / 100.0);
                            }
                        });

        HBox opacityRow = new HBox(12, opacitySlider, opacityValue);
        opacityRow.setAlignment(Pos.CENTER_LEFT);
        opacityBox.getChildren().addAll(opacityLabel, opacityRow);

        Label licenseLabel = new Label(Messages.get("about.license.label"));
        licenseLabel.getStyleClass().add("about-label");

        Label licenseValue = new Label(Messages.get("about.license.value"));
        licenseValue.getStyleClass().add("about-license");
        licenseValue.setWrapText(true);

        Label copyright = new Label(Messages.get("about.copyright"));
        copyright.getStyleClass().add("about-text");
        copyright.setWrapText(true);

        Label warranty = new Label(Messages.get("about.warranty"));
        warranty.getStyleClass().add("about-warranty");
        warranty.setWrapText(true);

        Button licenseButton = new Button(Messages.get("about.license.button"));
        licenseButton.getStyleClass().add("btn-secondary");
        licenseButton.setOnAction(e -> openGplPage());

        Button closeTextButton = new Button(Messages.get("about.close"));
        closeTextButton.getStyleClass().add("btn-secondary");
        closeTextButton.setOnAction(e -> stage.close());

        HBox actions = new HBox(8, licenseButton, closeTextButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren()
                .addAll(
                        header,
                        appName,
                        description,
                        opacityBox,
                        licenseLabel,
                        licenseValue,
                        copyright,
                        warranty,
                        actions);

        Scene scene = new Scene(card);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        themeManager.registerScene(scene);
        scene.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        stage.close();
                    }
                });
        card.setOnMousePressed(
                e -> {
                    dragOffsetX = e.getSceneX();
                    dragOffsetY = e.getSceneY();
                });
        card.setOnMouseDragged(
                e -> {
                    stage.setX(e.getScreenX() - dragOffsetX);
                    stage.setY(e.getScreenY() - dragOffsetY);
                });
        stage.setScene(scene);
    }

    private void openGplPage() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(GPL_URL));
            }
        } catch (Exception ignored) {
            // The bundled/source LICENSE remains available as the canonical full text.
        }
    }

    public void showAndWait() {
        if (stage.getOwner() != null) {
            stage.setX(
                    stage.getOwner().getX() + Math.max(0, (stage.getOwner().getWidth() - 460) / 2));
            stage.setY(stage.getOwner().getY() + 50);
        }
        stage.showAndWait();
    }
}
