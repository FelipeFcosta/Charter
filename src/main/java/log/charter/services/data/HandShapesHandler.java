package log.charter.services.data;

import java.util.List;

import log.charter.data.ChartData;
import log.charter.data.ChordLibrary;
import log.charter.data.song.ChordTemplate;
import log.charter.data.song.HandShape;
import log.charter.data.song.notes.ChordOrNote;
import log.charter.data.song.position.fractional.IConstantFractionalPosition;
import log.charter.data.types.PositionType;
import log.charter.data.undoSystem.UndoSystem;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.tabs.chordEditor.ChordTemplatesEditorTab;
import log.charter.gui.panes.songEdits.HandShapePane;
import log.charter.services.data.selection.ISelectionAccessor;
import log.charter.services.data.selection.Selection;
import log.charter.services.data.selection.SelectionManager;

public class HandShapesHandler {
	private ChartData chartData;
	private CharterFrame charterFrame;
	private ChordTemplatesEditorTab chordTemplatesEditorTab;
	private SelectionManager selectionManager;
	private UndoSystem undoSystem;

	public void markHandShape() {
		final ISelectionAccessor<ChordOrNote> selectionAccessor = selectionManager.accessor(PositionType.GUITAR_NOTE);
		if (!selectionAccessor.isSelected()) {
			return;
		}

		undoSystem.addUndo();

		final List<HandShape> handShapes = chartData.currentHandShapes();
		final List<Selection<ChordOrNote>> selected = selectionAccessor.getSelected();
		final ChordOrNote firstSelected = selected.get(0).selectable;

		final IConstantFractionalPosition position = firstSelected.toFraction(chartData.beats());
		final IConstantFractionalPosition endPosition = selected.get(selected.size() - 1).selectable.endPosition()
				.toFraction(chartData.beats());

		// Note: Overlapping handshapes are allowed in Rocksmith (used for arpeggios/fingerpicking)
		// so we no longer delete existing handshapes that overlap with the new one

		ChordTemplate chordTemplate = new ChordTemplate();
		if (selected.get(0).selectable.isChord()) {
			chordTemplate = chartData.currentArrangement().chordTemplates
					.get(selected.get(0).selectable.chord().templateId());
		}

		final HandShape handShape = new HandShape(position.position(), endPosition.position());
		handShape.templateId = chartData.currentArrangement().getChordTemplateIdWithSave(chordTemplate);
		chordTemplatesEditorTab.refreshTemplates();

		handShapes.add(handShape);
		handShapes.sort(IConstantFractionalPosition::compareTo);

		new HandShapePane(chartData, charterFrame, chordTemplatesEditorTab, handShape, () -> {
			undoSystem.undo();
			undoSystem.removeRedo();
			chordTemplatesEditorTab.refreshTemplates();
		});
	}

	public void markHandShapeFromNotes() {
		final ISelectionAccessor<ChordOrNote> selectionAccessor = selectionManager.accessor(PositionType.GUITAR_NOTE);
		if (!selectionAccessor.isSelected()) {
			return;
		}

		undoSystem.addUndo();

		final List<HandShape> handShapes = chartData.currentHandShapes();
		final List<Selection<ChordOrNote>> selected = selectionAccessor.getSelected();
		final ChordOrNote firstSelected = selected.get(0).selectable;

		final IConstantFractionalPosition position = firstSelected.toFraction(chartData.beats());
		final IConstantFractionalPosition endPosition = selected.get(selected.size() - 1).selectable.endPosition()
				.toFraction(chartData.beats());

		ChordTemplate chordTemplate = new ChordTemplate();

		for (final Selection<ChordOrNote> selection : selected) {
			final ChordOrNote sound = selection.selectable;
			if (sound.isNote()) {
				chordTemplate.frets.put(sound.note().string, sound.note().fret);
			} else {
				final ChordTemplate existingTemplate = chartData.currentArrangement().chordTemplates.get(sound.chord().templateId());
				for (final int string : sound.chord().chordNotes.keySet()) {
					if (existingTemplate.frets.containsKey(string)) {
						chordTemplate.frets.put(string, existingTemplate.frets.get(string));
					}
				}
			}
		}

		boolean snapped = false;
		for (final ChordTemplate existingTemplate : chartData.currentArrangement().chordTemplates) {
			if (existingTemplate.frets.equals(chordTemplate.frets)) {
				chordTemplate = new ChordTemplate(existingTemplate);
				snapped = true;
				break;
			}
		}

		if (!snapped) {
			for (final ChordTemplate existingTemplate : ChordLibrary.getInstance().getChords()) {
				if (existingTemplate.frets.equals(chordTemplate.frets)) {
					chordTemplate = new ChordTemplate(existingTemplate);
					break;
				}
			}
		}

		final HandShape handShape = new HandShape(position.position(), endPosition.position());
		handShape.templateId = chartData.currentArrangement().getChordTemplateIdWithSave(chordTemplate);
		chordTemplatesEditorTab.refreshTemplates();

		handShapes.add(handShape);
		handShapes.sort(IConstantFractionalPosition::compareTo);

		new HandShapePane(chartData, charterFrame, chordTemplatesEditorTab, handShape, () -> {
			undoSystem.undo();
			undoSystem.removeRedo();
			chordTemplatesEditorTab.refreshTemplates();
		});
	}
}
