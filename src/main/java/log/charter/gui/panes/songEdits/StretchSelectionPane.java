package log.charter.gui.panes.songEdits;

import static log.charter.gui.components.utils.TextInputSelectAllOnFocus.addSelectTextOnFocus;

import java.util.function.Consumer;

import log.charter.data.config.Localization.Label;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.containers.RowedDialog;
import log.charter.gui.components.containers.SaverWithStatus;
import log.charter.gui.components.simple.FieldWithLabel;
import log.charter.gui.components.simple.FieldWithLabel.LabelPosition;
import log.charter.gui.components.simple.TextInputWithValidation;
import log.charter.gui.components.utils.RowedPosition;
import log.charter.gui.components.utils.validators.DoubleValueValidator;

public class StretchSelectionPane extends RowedDialog {
	private static final long serialVersionUID = 1L;

	private final Consumer<Double> onStretch;
	private double scaleFactor = 1.0;

	public StretchSelectionPane(final CharterFrame frame, final double maxScale, final Consumer<Double> onStretch) {
		super(frame, Label.STRETCH_SELECTED, 250);
		this.onStretch = onStretch;

		final RowedPosition position = new RowedPosition(20, panel.sizes);
		addScaleFactorField(position, maxScale);

		position.newRow();
		position.newRow();
		addDefaultFinish(position.y(), SaverWithStatus.defaultFor(this::apply), null, true);
	}

	private void addScaleFactorField(final RowedPosition position, final double maxScale) {
		final TextInputWithValidation input = TextInputWithValidation.generateForDouble(scaleFactor, 60,
				new DoubleValueValidator(0.1, maxScale, false), v -> scaleFactor = v, false);
		addSelectTextOnFocus(input);
		final FieldWithLabel<TextInputWithValidation> field = new FieldWithLabel<>(Label.STRETCH_SCALE_FACTOR, 100, 60,
				25, input, LabelPosition.LEFT);
		panel.add(field, position);
	}

	private boolean apply() {
		onStretch.accept(scaleFactor);
		return true;
	}
}
