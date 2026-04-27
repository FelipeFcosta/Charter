package log.charter.services.data;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static log.charter.util.CollectionUtils.lastBeforeEqual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import log.charter.data.ChartData;
import log.charter.data.config.values.InstrumentConfig;
import log.charter.data.song.ChordTemplate;
import log.charter.data.song.FHP;
import log.charter.data.song.HandShape;
import log.charter.data.song.notes.Chord;
import log.charter.data.song.notes.ChordOrNote;
import log.charter.data.song.position.fractional.IConstantFractionalPosition;
import log.charter.util.collections.HashMap2;
import log.charter.data.types.PositionType;
import log.charter.data.undoSystem.UndoSystem;
import log.charter.gui.components.tabs.chordEditor.ChordTemplatesEditorTab;
import log.charter.gui.components.tabs.selectionEditor.CurrentSelectionEditor;
import log.charter.services.data.selection.Selection;
import log.charter.services.data.selection.SelectionManager;
import log.charter.util.chordRecognition.ChordNameSuggester;
import log.charter.util.data.IntRange;

public class GuitarSoundsHandler {
	private ChartData chartData;
	private ChordTemplatesEditorTab chordTemplatesEditorTab;
	private CurrentSelectionEditor currentSelectionEditor;
	private GuitarSoundsStatusesHandler guitarSoundsStatusesHandler;
	private SelectionManager selectionManager;
	private StringsChanger stringsChanger;
	private UndoSystem undoSystem;

	private boolean stringsInRange(final List<Selection<ChordOrNote>> selected, final IntRange stringRange) {
		return !selected.stream()//
				.flatMap(selection -> selection.selectable.notes())//
				.anyMatch(note -> !stringRange.inRange(note.string()));
	}

	private List<Selection<ChordOrNote>> getSelectedSoundsWithoutStringAboveBelow(final IntRange stringRange) {
		final List<Selection<ChordOrNote>> selected = selectionManager.<ChordOrNote>accessor(PositionType.GUITAR_NOTE)
				.getSelected();
		if (!stringsInRange(selected, stringRange)) {
			return new ArrayList<>();
		}

		return selected;
	}

	public void moveStringsWithoutFretChange(final int stringChange) {
		final int strings = chartData.currentStrings();
		final IntRange stringRange = new IntRange(max(0, -stringChange), strings - 1 - max(0, stringChange));
		final List<Selection<ChordOrNote>> selected = getSelectedSoundsWithoutStringAboveBelow(stringRange);
		if (selected.isEmpty()) {
			return;
		}

		stringsChanger.new Action(chartData.currentArrangement().tuning, stringRange, stringChange, false)
				.moveStrings(selected);
	}

	public void moveStringsWithFretChange(final int stringChange) {
		final int strings = chartData.currentStrings();
		final IntRange stringRange = new IntRange(max(0, -stringChange), strings - 1 - max(0, stringChange));
		final List<Selection<ChordOrNote>> selected = getSelectedSoundsWithoutStringAboveBelow(stringRange);
		if (selected.isEmpty()) {
			return;
		}
		stringsChanger.new Action(chartData.currentArrangement().tuning, stringRange, stringChange, true)
				.moveStrings(selected);
	}

	private void setChordName(final ChordTemplate template) {
		if (template.chordName == null || template.chordName.isBlank()) {
			return;
		}

		final boolean useETuningNaming = chartData.currentArrangement().chordNameMadnessCapoRelative;
		final int capo = useETuningNaming ? chartData.currentArrangement().capo : 0;
		final List<String> suggestedNames = ChordNameSuggester.suggestChordNames(chartData.currentArrangement().tuning,
				template.frets, capo, useETuningNaming);

		if (!suggestedNames.isEmpty()) {
			template.chordName = suggestedNames.get(0);
		} else {
			template.chordName = "";
		}
	}

	private HandShape findContainingHandShape(final IConstantFractionalPosition position) {
		final List<HandShape> handShapes = chartData.currentHandShapes();
		Integer id = lastBeforeEqual(handShapes, position).findId();
		while (id != null) {
			final HandShape handShape = handShapes.get(id);
			if (handShape.templateId != null && handShape.endPosition().compareTo(position) >= 0) {
				return handShape;
			}
			id = id > 0 ? id - 1 : null;
		}
		return null;
	}

	private boolean applyHandShapeTemplateIfMatches(final IConstantFractionalPosition position, final ChordTemplate chordTemplate) {
		final HandShape handShape = findContainingHandShape(position);
		if (handShape == null) {
			return false;
		}

		final ChordTemplate handShapeTemplate = chartData.currentChordTemplates().get(handShape.templateId);
		
		boolean isSubset = true;
		for (final int string : chordTemplate.frets.keySet()) {
			if (!handShapeTemplate.frets.containsKey(string)
					|| !handShapeTemplate.frets.get(string).equals(chordTemplate.frets.get(string))) {
				isSubset = false;
				break;
			}
		}

		if (isSubset) {
			chordTemplate.fingers.clear();
			for (final int string : chordTemplate.frets.keySet()) {
				if (handShapeTemplate.fingers.containsKey(string)) {
					chordTemplate.fingers.put(string, handShapeTemplate.fingers.get(string));
				}
			}

			if (handShapeTemplate.frets.equals(chordTemplate.frets)) {
				chordTemplate.chordName = handShapeTemplate.chordName;
			}
			return true;
		}
		
		return false;
	}

	private void applyFirstTimelineChordTemplateIfMatches(final ChordTemplate chordTemplate) {
		final List<ChordTemplate> chordTemplates = chartData.currentChordTemplates();
		for (final ChordOrNote sound : chartData.currentSounds()) {
			if (!sound.isChord()) {
				continue;
			}

			final ChordTemplate existing = chordTemplates.get(sound.chord().templateId());
			if (existing.frets.equals(chordTemplate.frets)) {
				chordTemplate.chordName = existing.chordName;
				chordTemplate.fingers = new HashMap2<>(existing.fingers);
				return;
			}
		}
	}

	private boolean validFretChange(final List<Selection<ChordOrNote>> selected, final int fretChange) {
		final IntRange fretRange = new IntRange(max(0, -fretChange), InstrumentConfig.frets - max(0, fretChange));

		for (final Selection<ChordOrNote> selection : selected) {
			final ChordOrNote sound = selection.selectable;
			if (sound.isNote()) {
				if (!fretRange.inRange(sound.note().fret)) {
					return false;
				}
				continue;
			}

			final Chord chord = sound.chord();
			final ChordTemplate template = chartData.currentArrangement().chordTemplates.get(chord.templateId());
			for (final int fret : template.frets.values()) {
				if (!fretRange.inRange(fret)) {
					return false;
				}
			}
		}

		return true;
	}

	private ChordTemplate moveTemplateFrets(final ChordTemplate template, final int fretChange) {
		final ChordTemplate newTemplate = new ChordTemplate();

		newTemplate.fingers.putAll(template.fingers);
		for (final Entry<Integer, Integer> stringFret : template.frets.entrySet()) {
			final int string = stringFret.getKey();
			final int oldFret = stringFret.getValue();
			final int newFret = max(0, min(InstrumentConfig.frets, oldFret + fretChange));

			newTemplate.frets.put(string, newFret);
			if (newFret == 0) {
				newTemplate.fingers.remove(string);
			}
		}

		setChordName(newTemplate);

		return newTemplate;
	}

	private void moveFret(final ChordOrNote sound, final int fretChange) {
		if (sound.isNote()) {
			sound.note().fret += fretChange;
			return;
		}

		final Chord chord = sound.chord();
		final ChordTemplate oldTemplate = chartData.currentArrangement().chordTemplates.get(chord.templateId());
		final ChordTemplate newTemplate = moveTemplateFrets(oldTemplate, fretChange);
		if (!applyHandShapeTemplateIfMatches(sound.position(), newTemplate)) {
			applyFirstTimelineChordTemplateIfMatches(newTemplate);
		}

		final int newTemplateId = chartData.currentArrangement().getChordTemplateIdWithSave(newTemplate);
		chord.updateTemplate(newTemplateId, newTemplate);
		chordTemplatesEditorTab.refreshTemplates();
	}

	private void moveFretForSounds(final int fretChange) {
		final List<Selection<ChordOrNote>> selected = selectionManager.<ChordOrNote>accessor(PositionType.GUITAR_NOTE)
				.getSelected();
		if (selected.isEmpty()) {
			return;
		}

		if (!validFretChange(selected, fretChange)) {
			return;
		}

		undoSystem.addUndo();
		selected.forEach(selection -> moveFret(selection.selectable, fretChange));
		guitarSoundsStatusesHandler
				.updateLinkedNotes(selected.stream().map(s -> s.id).collect(Collectors.toCollection(ArrayList::new)));

		currentSelectionEditor.selectionChanged(false);
	}

	private void moveFretForFHPs(final int fretChange) {
		final List<Selection<FHP>> selected = selectionManager.getSelected(PositionType.FHP);
		if (selected.isEmpty()) {
			return;
		}

		for (final Selection<FHP> fhpSelection : selected) {
			final int newFret = fhpSelection.selectable.fret + fretChange;
			if (newFret < 1 || newFret > InstrumentConfig.frets) {
				return;
			}
		}

		undoSystem.addUndo();

		for (final Selection<FHP> fhpSelection : selected) {
			fhpSelection.selectable.fret += fretChange;
		}

		currentSelectionEditor.selectionChanged(false);
	}

	public void moveFret(final int fretChange) {
		switch (selectionManager.selectedType()) {
			case FHP:
				moveFretForFHPs(fretChange);
				break;
			case GUITAR_NOTE:
				final Integer selectedChordNoteString = selectionManager.getSelectedChordNoteString();
				if (selectedChordNoteString != null) {
					moveFretForChordNote(fretChange, selectedChordNoteString);
				} else {
					moveFretForSounds(fretChange);
				}
				break;
			default:
				break;
		}
	}

	/**
	 * Moves the fret of a single note within a selected chord.
	 */
	private void moveFretForChordNote(final int fretChange, final int string) {
		final List<Selection<ChordOrNote>> selected = selectionManager.<ChordOrNote>accessor(PositionType.GUITAR_NOTE)
				.getSelected();
		if (selected.size() != 1) {
			return;
		}

		final Selection<ChordOrNote> selection = selected.get(0);
		final ChordOrNote sound = selection.selectable;
		if (!sound.isChord()) {
			return;
		}

		final Chord chord = sound.chord();
		final ChordTemplate oldTemplate = chartData.currentArrangement().chordTemplates.get(chord.templateId());
		if (!oldTemplate.frets.containsKey(string)) {
			return;
		}

		final int oldFret = oldTemplate.frets.get(string);
		final int newFret = max(0, min(InstrumentConfig.frets, oldFret + fretChange));
		if (newFret == oldFret) {
			return;
		}

		undoSystem.addUndo();

		final ChordTemplate newTemplate = new ChordTemplate(oldTemplate);
		newTemplate.frets.put(string, newFret);
		if (newFret == 0) {
			newTemplate.fingers.remove(string);
		}
		setChordName(newTemplate);
		if (!applyHandShapeTemplateIfMatches(sound.position(), newTemplate)) {
			applyFirstTimelineChordTemplateIfMatches(newTemplate);
		}

		final int newTemplateId = chartData.currentArrangement().getChordTemplateIdWithSave(newTemplate);
		chord.updateTemplate(newTemplateId, newTemplate);

		guitarSoundsStatusesHandler.updateLinkedNote(selection.id);
		currentSelectionEditor.selectionChanged(false);
		chordTemplatesEditorTab.refreshTemplates();
	}

	/**
	 * Sets the fret of a single note within a selected chord.
	 */
	public void setFretForChordNote(final int fret, final int string) {
		final List<Selection<ChordOrNote>> selected = selectionManager.<ChordOrNote>accessor(PositionType.GUITAR_NOTE)
				.getSelected();
		if (selected.size() != 1) {
			return;
		}

		final Selection<ChordOrNote> selection = selected.get(0);
		final ChordOrNote sound = selection.selectable;
		if (!sound.isChord()) {
			return;
		}

		final Chord chord = sound.chord();
		final ChordTemplate oldTemplate = chartData.currentArrangement().chordTemplates.get(chord.templateId());
		if (!oldTemplate.frets.containsKey(string)) {
			return;
		}

		if (fret < 0 || fret > InstrumentConfig.frets) {
			return;
		}

		final int oldFret = oldTemplate.frets.get(string);
		if (fret == oldFret) {
			return;
		}

		undoSystem.addUndo();

		final ChordTemplate newTemplate = new ChordTemplate(oldTemplate);
		newTemplate.frets.put(string, fret);
		if (fret == 0) {
			newTemplate.fingers.remove(string);
		}
		setChordName(newTemplate);
		if (!applyHandShapeTemplateIfMatches(sound.position(), newTemplate)) {
			applyFirstTimelineChordTemplateIfMatches(newTemplate);
		}

		final int newTemplateId = chartData.currentArrangement().getChordTemplateIdWithSave(newTemplate);
		chord.updateTemplate(newTemplateId, newTemplate);

		guitarSoundsStatusesHandler.updateLinkedNote(selection.id);
		currentSelectionEditor.selectionChanged(false);
		chordTemplatesEditorTab.refreshTemplates();
	}

	private void setFretForFHPs(final int fret) {
		final List<Selection<FHP>> selected = selectionManager.getSelected(PositionType.FHP);
		if (selected.isEmpty()) {
			return;
		}

		undoSystem.addUndo();

		for (final Selection<FHP> fhpSelection : selected) {
			fhpSelection.selectable.fret = fret;
		}
	}

	private void setFretForSounds(final int fret) {
		final List<Selection<ChordOrNote>> selected = selectionManager.getSelected(PositionType.GUITAR_NOTE);
		if (selected.isEmpty()) {
			return;
		}

		undoSystem.addUndo();

		for (final Selection<ChordOrNote> selection : selected) {
			final ChordOrNote sound = selection.selectable;
			if (sound.isNote()) {
				sound.note().fret = fret;
				continue;
			}

			final Chord chord = sound.chord();
			final ChordTemplate oldTemplate = chartData.currentArrangement().chordTemplates.get(chord.templateId());
			final int fretChange = fret - oldTemplate.getLowestFret();
			if (fretChange == 0 || oldTemplate.getHighestFret() + fretChange > InstrumentConfig.frets) {
				continue;
			}

			final ChordTemplate newTemplate = moveTemplateFrets(oldTemplate, fretChange);
			if (!applyHandShapeTemplateIfMatches(sound.position(), newTemplate)) {
				applyFirstTimelineChordTemplateIfMatches(newTemplate);
			}

			final int newTemplateId = chartData.currentArrangement().getChordTemplateIdWithSave(newTemplate);
			chord.updateTemplate(newTemplateId, newTemplate);
		}

		guitarSoundsStatusesHandler
				.updateLinkedNotes(selected.stream().map(s -> s.id).collect(Collectors.toCollection(ArrayList::new)));

		currentSelectionEditor.selectionChanged(false);
		chordTemplatesEditorTab.refreshTemplates();
	}

	public void setFret(final int fret) {
		switch (selectionManager.selectedType()) {
			case FHP:
				setFretForFHPs(fret);
				break;
			case GUITAR_NOTE:
				setFretForSounds(fret);
				break;
			default:
				break;
		}
	}
}
