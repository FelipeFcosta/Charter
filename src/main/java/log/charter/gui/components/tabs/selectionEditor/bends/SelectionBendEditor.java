package log.charter.gui.components.tabs.selectionEditor.bends;

import static log.charter.data.config.ChartPanelColors.getStringBasedColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JScrollPane;

import log.charter.data.ChartData;
import log.charter.data.config.ChartPanelColors.StringColorLabelType;
import log.charter.data.config.values.InstrumentConfig;
import log.charter.data.song.BendValue;
import log.charter.data.song.notes.ChordOrNote;
import log.charter.data.song.notes.CommonNoteWithFret;
import log.charter.data.types.PositionType;
import log.charter.data.undoSystem.UndoSystem;
import log.charter.gui.components.containers.CharterScrollPane;
import log.charter.gui.components.containers.RowedPanel;
import log.charter.gui.components.utils.PaneSizesBuilder;
import log.charter.gui.lookAndFeel.CharterCheckBox;
import log.charter.services.data.GuitarSoundsStatusesHandler;
import log.charter.services.data.selection.ISelectionAccessor;
import log.charter.services.data.selection.Selection;
import log.charter.services.data.selection.SelectionManager;

public class SelectionBendEditor extends RowedPanel {
	private static final long serialVersionUID = 6095874968137603127L;

	private static void invert(final boolean[] values) {
		for (int i = 0; i < values.length; i++) {
			values[i] = !values[i];
		}
	}

	private final ChartData chartData;
	private final GuitarSoundsStatusesHandler guitarSoundsStatusesHandler;
	private final SelectionManager selectionManager;
	private final UndoSystem undoSystem;

	private final BendEditorGraph bendEditorGraph;

	private List<JCheckBox> strings;
	private final List<Integer> selectedStrings = new ArrayList<>();
	private int lastStringsAmount = InstrumentConfig.maxStrings;

	private Selection<ChordOrNote> getCurrentSelection() {
		final ISelectionAccessor<ChordOrNote> selectionAccessor = selectionManager.accessor(PositionType.GUITAR_NOTE);
		return selectionAccessor.getSelected().get(0);
	}

	public SelectionBendEditor(final RowedPanel parent, final ChartData chartData,
			final GuitarSoundsStatusesHandler guitarSoundsStatusesHandler, final SelectionManager selectionManager,
			final UndoSystem undoSystem) {
		super(new PaneSizesBuilder(500).build(), 2);

		this.chartData = chartData;
		this.guitarSoundsStatusesHandler = guitarSoundsStatusesHandler;
		this.selectionManager = selectionManager;
		this.undoSystem = undoSystem;

		addCheckBoxes();

		bendEditorGraph = new BendEditorGraph(this::onChangeBends);

		final CharterScrollPane scrollPane = new CharterScrollPane(bendEditorGraph,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.validate();
		this.addWithSettingSize(scrollPane, 20, sizes.getY(1), 440, BendEditorGraph.height + 20);

		setSize(500, sizes.getY(2) + BendEditorGraph.height);
		setMinimumSize(getSize());
		setPreferredSize(getSize());
		setMaximumSize(getSize());
	}

	private void addCheckBoxes() {
		strings = new ArrayList<>();
		for (int i = 0; i < InstrumentConfig.maxStrings; i++) {
			final int string = i;
			final JCheckBox checkBox = new JCheckBox((string + 1) + "");
			checkBox.addActionListener(e -> onToggleString(string));
			this.addWithSettingSize(checkBox, 20 + 40 * i, sizes.getY(0), 40, 20);
			strings.add(checkBox);
		}
	}

	private void onToggleString(final int string) {
		if (strings.get(string).isSelected()) {
			if (!selectedStrings.contains(string)) {
				selectedStrings.add(string);
			}
			// Graph stays on the reference (first selected) string; no update needed
		} else {
			final boolean wasReference = !selectedStrings.isEmpty() && selectedStrings.get(0).equals(string);
			selectedStrings.remove((Integer) string);
			if (wasReference && !selectedStrings.isEmpty()) {
				final int newReference = selectedStrings.get(0);
				final Selection<ChordOrNote> selection = getCurrentSelection();
				if (selection.selectable.isChord()) {
					bendEditorGraph.setBendValues(newReference,
							selection.selectable.chord().chordNotes.get(newReference).bendValues);
				}
			}
		}
	}

	private void doActionOnStringButtons(final boolean[] shouldActionBeDone,
			final BiConsumer<JCheckBox, Integer> action) {
		for (int i = 0; i < InstrumentConfig.maxStrings; i++) {
			if (shouldActionBeDone[i]) {
				action.accept(strings.get(i), i);
			}
		}
	}

	private void doActionOnStringButtons(final boolean[] shouldActionBeDone, final Consumer<JCheckBox> action) {
		for (int i = 0; i < InstrumentConfig.maxStrings; i++) {
			if (shouldActionBeDone[i]) {
				action.accept(strings.get(i));
			}
		}
	}

	public void enableAndSelectStrings(final ChordOrNote sound) {
		selectedStrings.clear();
		strings.forEach(cb -> cb.setSelected(false));

		final boolean[] stringsForAction = new boolean[InstrumentConfig.maxStrings];
		final int lowestString = sound.notesWithFrets(chartData.currentArrangement().chordTemplates)//
				.map(CommonNoteWithFret::string)//
				.peek(string -> stringsForAction[string] = true)//
				.collect(Collectors.minBy(Integer::compare)).get();

		doActionOnStringButtons(stringsForAction, button -> button.setEnabled(true));
		invert(stringsForAction);
		doActionOnStringButtons(stringsForAction, button -> button.setEnabled(false));

		strings.get(lowestString).setSelected(true);
		selectedStrings.add(lowestString);

		if (sound.isNote()) {
			bendEditorGraph.setNote(sound.note(), chartData.currentStrings());
			return;
		} else {
			bendEditorGraph.setChord(sound.chord(), lowestString, chartData.currentStrings());
		}
	}

	private void resetColors() {
		if (lastStringsAmount != chartData.currentStrings()) {
			lastStringsAmount = chartData.currentStrings();
			final boolean[] stringsForAction = new boolean[InstrumentConfig.maxStrings];
			for (int i = 0; i < lastStringsAmount; i++) {
				stringsForAction[i] = true;
			}
			doActionOnStringButtons(stringsForAction, (button, i) -> {
				button.setIcon(new CharterCheckBox.CheckBoxIcon(
						getStringBasedColor(StringColorLabelType.NOTE, i, lastStringsAmount)));
			});
		}
	}

	private void resetVisibleStrings() {
		final boolean[] stringsForAction = new boolean[InstrumentConfig.maxStrings];
		for (int i = 0; i < lastStringsAmount; i++) {
			stringsForAction[i] = true;
		}
		doActionOnStringButtons(stringsForAction, button -> button.setVisible(true));
		for (int i = 0; i < InstrumentConfig.maxStrings; i++) {
			stringsForAction[i] = !stringsForAction[i];
		}
		doActionOnStringButtons(stringsForAction, button -> button.setVisible(false));
	}

	public void onChangeSelection(final ChordOrNote selected) {
		strings.forEach(button -> button.setVisible(false));

		resetColors();
		resetVisibleStrings();
		enableAndSelectStrings(selected);
	}

	private void onChangeBends(final int string, final List<BendValue> newBends) {
		undoSystem.addUndo();

		final Selection<ChordOrNote> selection = getCurrentSelection();
		for (final int selectedString : selectedStrings) {
			selection.selectable.getString(selectedString).ifPresent(note -> note.bendValues(newBends));
		}
		guitarSoundsStatusesHandler.updateLinkedNote(selection.id);
	}
}
