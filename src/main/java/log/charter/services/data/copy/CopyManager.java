package log.charter.services.data.copy;

import static log.charter.util.CollectionUtils.getFromTo;
import static log.charter.util.CollectionUtils.map;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.thoughtworks.xstream.io.StreamException;

import log.charter.data.ChartData;
import log.charter.data.song.Arrangement;
import log.charter.data.song.BeatsMap.ImmutableBeatsMap;
import log.charter.data.song.ChordTemplate;
import log.charter.data.song.EventPoint;
import log.charter.data.song.FHP;
import log.charter.data.song.HandShape;
import log.charter.data.song.Phrase;
import log.charter.data.song.ToneChange;
import log.charter.data.song.notes.ChordOrNote;
import log.charter.data.song.position.FractionalPosition;
import log.charter.data.song.vocals.Vocal;
import log.charter.data.song.position.fractional.IConstantFractionalPosition;
import log.charter.data.song.position.fractional.IConstantFractionalPositionWithEnd;
import log.charter.data.song.position.virtual.IVirtualConstantPosition;
import log.charter.data.types.PositionType;
import log.charter.data.undoSystem.UndoSystem;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.tabs.chordEditor.ChordTemplatesEditorTab;
import log.charter.gui.panes.songEdits.GuitarSpecialPastePane;
import log.charter.io.ClipboardHandler;
import log.charter.io.Logger;
import log.charter.io.rsc.xml.ChartProjectXStreamHandler;
import log.charter.services.data.ChartTimeHandler;
import log.charter.services.data.LiveLyricsHandler;
import log.charter.services.data.copy.data.CopyData;
import log.charter.services.data.copy.data.EventPointsCopyData;
import log.charter.services.data.copy.data.FHPsCopyData;
import log.charter.services.data.copy.data.FullCopyData;
import log.charter.services.data.copy.data.FullGuitarCopyData;
import log.charter.services.data.copy.data.HandShapesCopyData;
import log.charter.services.data.copy.data.ICopyData;
import log.charter.services.data.copy.data.SoundsCopyData;
import log.charter.services.data.copy.data.ToneChangesCopyData;
import log.charter.services.data.copy.data.VocalsCopyData;
import log.charter.services.data.copy.data.positions.Copied;
import log.charter.services.data.copy.data.positions.CopiedEventPoint;
import log.charter.services.data.copy.data.positions.CopiedFHP;
import log.charter.services.data.copy.data.positions.CopiedHandShape;
import log.charter.services.data.copy.data.positions.CopiedSound;
import log.charter.services.data.copy.data.positions.CopiedToneChange;
import log.charter.services.data.copy.data.positions.CopiedVocalPosition;
import log.charter.services.data.selection.ISelectionAccessor;
import log.charter.services.data.selection.SelectionManager;
import log.charter.services.editModes.EditMode;
import log.charter.services.editModes.ModeManager;

public class CopyManager {
	private static interface CopyMakerSimple<T, V extends Copied<T>> {
		V make(FractionalPosition basePosition, T item);
	}

	private ChartData chartData;
	private CharterFrame charterFrame;
	private ChartTimeHandler chartTimeHandler;
	private ChordTemplatesEditorTab chordTemplatesEditorTab;
	private LiveLyricsHandler liveLyricsHandler;
	private ModeManager modeManager;
	private SelectionManager selectionManager;
	private UndoSystem undoSystem;

	private <T extends IConstantFractionalPosition, V extends Copied<T>> List<V> makeCopy(final List<T> selected,
			final CopyMakerSimple<T, V> copyMaker) {
		final FractionalPosition basePosition = selected.get(0).position();
		return map(selected, e -> copyMaker.make(basePosition, e));
	}

	private <T extends IConstantFractionalPosition, V extends Copied<T>> List<V> makeCopy(final List<T> selected,
			final FractionalPosition basePosition, final CopyMakerSimple<T, V> copyMaker) {
		return map(selected, e -> copyMaker.make(basePosition, e));
	}

	private <T extends IConstantFractionalPosition, V extends Copied<T>> List<V> copyPositionsFromTo(
			final FractionalPosition from, final FractionalPosition to, final List<T> positions,
			final CopyMakerSimple<T, V> copyMaker) {
		return map(getFromTo(positions, from, to), p -> copyMaker.make(from, p));
	}

	private FullCopyData getFullCopyData(final IVirtualConstantPosition fromVirtual,
			final IVirtualConstantPosition toVirtual) {
		if (modeManager.getMode() != EditMode.GUITAR) {
			return null;
		}

		final Arrangement arrangement = chartData.currentArrangement();
		final ImmutableBeatsMap beats = chartData.beats();
		final FractionalPosition from = fromVirtual.toFraction(beats).position();
		final FractionalPosition to = toVirtual.toFraction(beats).position();

		final Map<String, Phrase> copiedPhrases = map(arrangement.phrases, k -> k, Phrase::new);
		final List<CopiedEventPoint> copiedArrangementEventsPoints = copyPositionsFromTo(from, to,
				arrangement.eventPoints, CopiedEventPoint::new);
		final List<ChordTemplate> copiedChordTemplates = map(chartData.currentChordTemplates(), ChordTemplate::new);
		final List<CopiedToneChange> copiedToneChanges = copyPositionsFromTo(from, to, arrangement.toneChanges,
				CopiedToneChange::new);
		final List<CopiedFHP> copiedFHPs = copyPositionsFromTo(from, to, chartData.currentArrangementLevel().fhps,
				CopiedFHP::new);
		final List<CopiedSound> copiedSounds = copyPositionsFromTo(from, to, chartData.currentSounds(),
				CopiedSound::copy);
		final List<CopiedHandShape> copiedHandShapes = copyPositionsFromTo(from, to, chartData.currentHandShapes(),
				CopiedHandShape::new);

		return new FullGuitarCopyData(copiedPhrases, copiedArrangementEventsPoints, copiedChordTemplates,
				copiedToneChanges, copiedFHPs, copiedSounds, copiedHandShapes);
	}

	private CopyData getGuitarCopyDataEventPoints() {
		final List<EventPoint> selected = selectionManager.getSelectedElements(PositionType.EVENT_POINT);
		final FractionalPosition from = selected.get(0).position();
		final FractionalPosition to = selected.get(selected.size() - 1).position();
		final Arrangement arrangement = chartData.currentArrangement();

		final Map<String, Phrase> copiedPhrases = map(arrangement.phrases, phraseName -> phraseName, Phrase::new);
		final List<CopiedEventPoint> copiedArrangementEventsPoints = copyPositionsFromTo(from, to,
				arrangement.eventPoints, CopiedEventPoint::new);

		final ICopyData copyData = new EventPointsCopyData(copiedPhrases, copiedArrangementEventsPoints);
		return new CopyData(copyData, getFullCopyData(from, to));
	}

	private CopyData getGuitarCopyDataGuitarNotes() {
		final List<ChordOrNote> selected = selectionManager.getSelectedElements(PositionType.GUITAR_NOTE);
		final List<EventPoint> selectedEventPoints = selectionManager.getSelectedElements(PositionType.EVENT_POINT);
		final List<ToneChange> selectedToneChanges = selectionManager.getSelectedElements(PositionType.TONE_CHANGE);
		final List<FHP> selectedFHPs = selectionManager.getSelectedElements(PositionType.FHP);
		final List<HandShape> selectedHandShapes = selectionManager.getSelectedElements(PositionType.HAND_SHAPE);

		// Calculate the earliest and latest positions across all selected types first,
		// so that all layers share the same base position when copied and pasted.
		FractionalPosition from = selected.get(0).position();
		FractionalPosition to = selected.get(selected.size() - 1).endPosition().position();

		if (!selectedEventPoints.isEmpty()) {
			final FractionalPosition eventFrom = selectedEventPoints.get(0).position();
			final FractionalPosition eventTo = selectedEventPoints.get(selectedEventPoints.size() - 1).position();
			if (eventFrom.compareTo(from) < 0) {
				from = eventFrom;
			}
			if (eventTo.compareTo(to) > 0) {
				to = eventTo;
			}
		}

		if (!selectedToneChanges.isEmpty()) {
			final FractionalPosition toneFrom = selectedToneChanges.get(0).position();
			final FractionalPosition toneTo = selectedToneChanges.get(selectedToneChanges.size() - 1).position();
			if (toneFrom.compareTo(from) < 0) {
				from = toneFrom;
			}
			if (toneTo.compareTo(to) > 0) {
				to = toneTo;
			}
		}

		if (!selectedFHPs.isEmpty()) {
			final FractionalPosition fhpFrom = selectedFHPs.get(0).position();
			final FractionalPosition fhpTo = selectedFHPs.get(selectedFHPs.size() - 1).position();
			if (fhpFrom.compareTo(from) < 0) {
				from = fhpFrom;
			}
			if (fhpTo.compareTo(to) > 0) {
				to = fhpTo;
			}
		}

		if (!selectedHandShapes.isEmpty()) {
			final FractionalPosition hsFrom = selectedHandShapes.get(0).position();
			final FractionalPosition hsTo = selectedHandShapes.get(selectedHandShapes.size() - 1).endPosition().position();
			if (hsFrom.compareTo(from) < 0) {
				from = hsFrom;
			}
			if (hsTo.compareTo(to) > 0) {
				to = hsTo;
			}
		}

		final List<ChordTemplate> copiedChordTemplates = chartData.currentArrangement().chordTemplates//
				.stream().map(ChordTemplate::new).collect(Collectors.toList());
		final List<CopiedSound> copiedSounds = makeCopy(selected, from, CopiedSound::copy);

		final ICopyData copyData = new SoundsCopyData(copiedChordTemplates, copiedSounds);
		
		FullGuitarCopyData fullData = (FullGuitarCopyData) getFullCopyData(from, to);
		final java.util.Set<FractionalPosition> selectedNotePos = new java.util.HashSet<>();
		for (final ChordOrNote sound : selected) {
			selectedNotePos.add(sound.position());
			boolean hasSlide = false;
			if (sound.isNote()) {
				hasSlide = sound.note().slideTo != null || sound.note().unpitchedSlide;
			} else {
				hasSlide = sound.chord().chordNotes.values().stream()
						.anyMatch(cn -> cn.slideTo != null || cn.unpitchedSlide);
			}
			if (hasSlide) {
				selectedNotePos.add(sound.endPosition());
			}
		}

		final FractionalPosition finalFrom = from;
		fullData.fhps.fhps.removeIf(copiedFHP -> !selectedNotePos.contains(copiedFHP.fp.add(finalFrom)));

		return new CopyData(copyData, fullData);
	}

	private CopyData getGuitarCopyDataHandShapes() {
		final List<HandShape> selectedHandShapes = selectionManager.getSelectedElements(PositionType.HAND_SHAPE);

		final List<ChordTemplate> copiedChordTemplates = map(chartData.currentChordTemplates(), ChordTemplate::new);
		final List<CopiedHandShape> copiedHandShapes = makeCopy(selectedHandShapes, CopiedHandShape::new);
		final FractionalPosition from = selectedHandShapes.get(0).position();
		final FractionalPosition to = selectedHandShapes.get(selectedHandShapes.size() - 1).endPosition().position();

		final ICopyData copyData = new HandShapesCopyData(copiedChordTemplates, copiedHandShapes);
		return new CopyData(copyData, getFullCopyData(from, to));
	}

	private <T extends IConstantFractionalPositionWithEnd, V extends Copied<T>> CopyData getCopyDataWithEnd(
			final PositionType type, final CopyMakerSimple<T, V> copiedPositionMaker,
			final Function<List<V>, ICopyData> copyDataMaker) {
		final ISelectionAccessor<T> selectionAccessor = selectionManager.accessor(type);
		if (!selectionAccessor.isSelected()) {
			return null;
		}

		final List<T> selected = selectionAccessor.getSelectedElements();
		final List<V> copied = makeCopy(selected, copiedPositionMaker);

		final FractionalPosition from = selected.get(0).position();
		final FractionalPosition to = selected.get(selected.size() - 1).endPosition();

		final FullCopyData fullCopyData = getFullCopyData(from, to);

		return new CopyData(copyDataMaker.apply(copied), fullCopyData);
	}

	private <T extends IConstantFractionalPosition, V extends Copied<T>> CopyData getCopyData(final PositionType type,
			final CopyMakerSimple<T, V> copiedPositionMaker, final Function<List<V>, ICopyData> copyDataMaker) {
		final ISelectionAccessor<T> selectionAccessor = selectionManager.accessor(type);
		if (!selectionAccessor.isSelected()) {
			return null;
		}

		final List<T> selected = selectionAccessor.getSelectedElements();
		final List<V> copied = makeCopy(selected, copiedPositionMaker);

		final FractionalPosition from = selected.get(0).position();
		final FractionalPosition to = selected.get(selected.size() - 1).position();

		final FullCopyData fullCopyData = getFullCopyData(from, to);

		return new CopyData(copyDataMaker.apply(copied), fullCopyData);
	}

	private CopyData getGuitarCopyData() {
		// Prioritize GUITAR_NOTE because it triggers full paste (including FHP, hand shapes, etc.)
		if (selectionManager.accessor(PositionType.GUITAR_NOTE).isSelected()) {
			return getGuitarCopyDataGuitarNotes();
		}
		if (selectionManager.accessor(PositionType.FHP).isSelected()) {
			return getCopyData(PositionType.FHP, CopiedFHP::new, FHPsCopyData::new);
		}
		if (selectionManager.accessor(PositionType.EVENT_POINT).isSelected()) {
			return getGuitarCopyDataEventPoints();
		}
		if (selectionManager.accessor(PositionType.HAND_SHAPE).isSelected()) {
			return getGuitarCopyDataHandShapes();
		}
		if (selectionManager.accessor(PositionType.TONE_CHANGE).isSelected()) {
			return getCopyData(PositionType.TONE_CHANGE, CopiedToneChange::new, ToneChangesCopyData::new);
		}

		return null;
	}

	private CopyData getCopyData() {
		switch (modeManager.getMode()) {
			case GUITAR:
				return getGuitarCopyData();
			case VOCALS:
				return getCopyDataWithEnd(PositionType.VOCAL, CopiedVocalPosition::new, VocalsCopyData::new);
			case TEMPO_MAP:
			default:
				return null;
		}
	}

	public void copy() {
		final CopyData copyData = getCopyData();
		if (copyData == null) {
			return;
		}

		final String xml = ChartProjectXStreamHandler.writeCopyData(copyData);
		try {
			ClipboardHandler.setClipboardBytes(xml.getBytes("UTF-8"));
		} catch (final UnsupportedEncodingException e) {
			Logger.error("Couldn't copy data", e);
		}
	}

	private String getClipboardXml() {
		try {
			final String xml = new String(ClipboardHandler.readClipboardBytes(), "UTF-8");
			return xml.isEmpty() ? null : xml;
		} catch (final UnsupportedEncodingException e) {
			Logger.error("Couldn't read clipboard data", e);
			return null;
		}
	}

	private CopyData parseClipboardXml(final String xml) {
		if (xml == null) {
			return null;
		}

		try {
			return ChartProjectXStreamHandler.readCopyData(xml);
		} catch (final Exception e) {
			if (e instanceof StreamException) {
				return null;
			}

			Logger.debug("xml parse failed:\n" + xml, e);
			return null;
		}
	}

	private CopyData getDataFromClipboard() {
		return parseClipboardXml(getClipboardXml());
	}

	private void pasteVocals(final CopyData copyData) {
		final ICopyData selectedCopy = copyData.selectedCopy;
		if (selectedCopy.isEmpty() || selectedCopy.type() != PositionType.VOCAL) {
			return;
		}

		undoSystem.addUndo();
		selectionManager.clear();
		selectedCopy.paste(chartData, selectionManager, chartTimeHandler.displayTimeFractional(), true);

		if (liveLyricsHandler != null) {
			liveLyricsHandler.pasteVocals(selectionManager.getSelectedElements(PositionType.VOCAL));
		}
	}

	private void pasteGuitar(final CopyData copyData, final String clipboardXml) {
		final ICopyData selectedCopy = copyData.selectedCopy;
		if (selectedCopy.isEmpty()) {
			return;
		}
		switch (selectedCopy.type()) {
			case EVENT_POINT:
			case TONE_CHANGE:
			case FHP:
			case GUITAR_NOTE:
			case HAND_SHAPE:
				break;
			case NONE:
			case BEAT:
			case VOCAL:
			default:
				return;
		}

		// When multiple guitar notes are selected AND the timeline position is snapped
		// to one of them, paste a copy at every selected position (replacing each one).
		// Sounds that are the receiving end of a link-next are skipped — they are
		// considered part of the primary note, not independent paste targets.
		// Each iteration deserializes a fresh copy of the clipboard data because
		// CopiedSound.prepareValue() mutates internal state.
		if (selectedCopy.type() == PositionType.GUITAR_NOTE) {
			final List<ChordOrNote> selectedSounds = selectionManager.getSelectedElements(PositionType.GUITAR_NOTE);
			if (selectedSounds.size() > 1) {
				final FractionalPosition currentTime = chartTimeHandler.displayTimeFractional();
				final boolean snappedToSelection = selectedSounds.stream()
						.anyMatch(s -> s.position().compareTo(currentTime) == 0);

				if (snappedToSelection) {
					final List<ChordOrNote> allSounds = chartData.currentSounds();
					final List<FractionalPosition> targetPositions = new java.util.ArrayList<>();
					for (final ChordOrNote sound : selectedSounds) {
						final int idx = allSounds.indexOf(sound);
						if (idx >= 0 && !ChordOrNote.isLinkedToPrevious(sound, idx, allSounds)) {
							targetPositions.add(sound.position());
						}
					}

					undoSystem.addUndo();
					selectionManager.clear();

					for (final FractionalPosition targetPosition : targetPositions) {
						final CopyData freshData = parseClipboardXml(clipboardXml);
						if (freshData == null || freshData.selectedCopy == null || freshData.selectedCopy.isEmpty()) {
							continue;
						}
						freshData.selectedCopy.paste(chartData, selectionManager, targetPosition, true);
					}

					chordTemplatesEditorTab.refreshTemplates();
					return;
				}
			}
		}

		undoSystem.addUndo();
		selectionManager.clear();

		final FractionalPosition currentTime = chartTimeHandler.displayTimeFractional();
		if (selectedCopy.type() == PositionType.GUITAR_NOTE) {
			final FullCopyData fullCopy = copyData.fullCopy;
			if (fullCopy instanceof FullGuitarCopyData) {
				final FullGuitarCopyData fullGuitarCopyData = (FullGuitarCopyData) fullCopy;
				fullGuitarCopyData.beats.paste(chartData, selectionManager, currentTime, true);
				fullGuitarCopyData.toneChanges.paste(chartData, selectionManager, currentTime, true);
				fullGuitarCopyData.fhps.paste(chartData, selectionManager, currentTime, true);
				fullGuitarCopyData.handShapes.paste(chartData, selectionManager, currentTime, true);
			}
		}

		selectedCopy.paste(chartData, selectionManager, currentTime, true);

		chordTemplatesEditorTab.refreshTemplates();
	}

	public void paste() {
		if (modeManager.getMode() == EditMode.EMPTY || modeManager.getMode() == EditMode.TEMPO_MAP) {
			return;
		}

		final String clipboardXml = getClipboardXml();
		final CopyData copyData = parseClipboardXml(clipboardXml);
		if (copyData == null || copyData.selectedCopy == null) {
			return;
		}

		if (modeManager.getMode() == EditMode.VOCALS) {
			pasteVocals(copyData);
			return;
		}
		if (modeManager.getMode() == EditMode.GUITAR) {
			pasteGuitar(copyData, clipboardXml);
			return;
		}

		final ICopyData selectedCopy = copyData.selectedCopy;
		if (selectedCopy.isEmpty()) {
			return;
		}

		final boolean isVocalsEditMode = modeManager.getMode() == EditMode.VOCALS;
		final boolean isVocalsCopyData = selectedCopy instanceof VocalsCopyData;
		if (isVocalsEditMode != isVocalsCopyData) {
			return;
		}

		undoSystem.addUndo();
		selectionManager.clear();
		selectedCopy.paste(chartData, selectionManager, chartTimeHandler.displayTimeFractional(), true);
	}

	public void specialPaste() {
		final CopyData copyData = getDataFromClipboard();
		if (copyData == null || copyData.selectedCopy == null) {
			return;
		}

		final FullCopyData fullCopy = copyData.fullCopy;
		if (fullCopy == null || fullCopy.isEmpty()) {
			return;
		}

		if (fullCopy instanceof FullGuitarCopyData) {
			new GuitarSpecialPastePane(chartData, charterFrame, chordTemplatesEditorTab, selectionManager, undoSystem,
					chartTimeHandler.displayTimeFractional(), (FullGuitarCopyData) fullCopy);
			return;
		}
	}
}
