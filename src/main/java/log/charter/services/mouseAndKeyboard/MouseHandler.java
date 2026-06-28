package log.charter.services.mouseAndKeyboard;

import static log.charter.util.CollectionUtils.contains;
import static log.charter.util.CollectionUtils.map;
import static log.charter.util.ScalingUtils.xToPosition;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import log.charter.data.ChartData;
import log.charter.data.config.ZoomUtils;
import log.charter.data.song.BeatsMap.ImmutableBeatsMap;
import log.charter.data.song.ChordTemplate;
import log.charter.data.song.FHP;
import log.charter.data.song.HandShape;
import log.charter.data.song.notes.ChordNote;
import log.charter.data.song.notes.ChordOrNote;
import log.charter.data.song.notes.Note;
import log.charter.data.song.position.FractionalPosition;
import log.charter.util.data.Fraction;
import log.charter.data.song.position.fractional.IConstantFractionalPosition;
import log.charter.data.song.position.time.Position;
import log.charter.data.song.position.virtual.IVirtualConstantPosition;
import log.charter.data.song.position.virtual.IVirtualPosition;
import log.charter.data.song.position.virtual.IVirtualPositionWithEnd;
import log.charter.data.types.PositionType;
import log.charter.data.undoSystem.UndoSystem;
import log.charter.gui.CharterFrame;
import log.charter.gui.chartPanelDrawers.common.DrawerUtils;
import log.charter.gui.components.tabs.chordEditor.ChordTemplatesEditorTab;
import log.charter.gui.panes.songEdits.HandShapePane;
import log.charter.gui.panes.songEdits.VocalPane;
import log.charter.io.Logger;
import log.charter.services.ActionHandler;
import log.charter.services.data.BeatsService;
import log.charter.services.data.ChartTimeHandler;
import log.charter.services.data.LiveLyricsHandler;
import log.charter.services.data.fixers.ArrangementFixer;
import log.charter.services.data.selection.Selection;
import log.charter.services.data.selection.SelectionManager;
import log.charter.services.data.selection.SlideFretLabelHandler;
import log.charter.gui.panes.songEdits.StretchSelectionPane;
import log.charter.services.editModes.ModeManager;
import log.charter.services.mouseAndKeyboard.MouseButtonPressReleaseHandler.MouseButtonPressReleaseData;

public class MouseHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
	private ActionHandler actionHandler;
	private ArrangementFixer arrangementFixer;
	private BeatsService beatsService;
	private ChartData chartData;
	private CharterFrame charterFrame;
	private ChartTimeHandler chartTimeHandler;
	private ChordTemplatesEditorTab chordTemplatesEditorTab;
	private KeyboardHandler keyboardHandler;
	private LiveLyricsHandler liveLyricsHandler;
	private ModeManager modeManager;
	private MouseButtonPressReleaseHandler mouseButtonPressReleaseHandler;
	private SelectionManager selectionManager;
	private SlideFretLabelHandler slideFretLabelHandler;
	private UndoSystem undoSystem;

	private boolean pressCancelsRelease = false;
	private boolean releaseCancelled = false;
	private int mouseX = -1;
	private int mouseY = -1;
	private long lastLeftClickTime = 0;
	private Integer lastClickId = null;

	/*
	 * invoked only when it wasn't dragged
	 */
	@Override
	public void mouseClicked(final MouseEvent e) {
		mouseMoved(e);

		if (releaseCancelled) {
			pressCancelsRelease = false;
			return;
		}

		if (!chartData.isEmpty && e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2
				&& e.getY() < DrawerUtils.lanesTop) {
			final double rawTime = xToPosition(e.getX(), chartTimeHandler.displayTime());
			final IVirtualConstantPosition snapped = chartData.beats()
					.getPositionFromGridClosestTo(new Position(rawTime));
			chartTimeHandler.nextTime(snapped.toPosition(chartData.beats()).position());
		}
	}

	@Override
	public void mouseEntered(final MouseEvent e) {
		mouseMoved(e);
	}

	@Override
	public void mouseExited(final MouseEvent e) {
		mouseMoved(e);
	}

	@Override
	public void mousePressed(final MouseEvent e) {
		Logger.debug("Mouse pressed, key: " + e.getButton() + ", position: (" + e.getX() + "," + e.getY() + ")");

		try {
			if (e.getComponent() != null) {
				e.getComponent().requestFocus();
			}

			mouseMoved(e);

			if (chartData.isEmpty) {
				return;
			}

			if (e.getButton() == MouseEvent.BUTTON1) {
				if (slideFretLabelHandler.handleClick(e.getX(), e.getY())) {
					if (slideFretLabelHandler.hasSelection()) {
						selectionManager.clear();
					}
					pressCancelsRelease = true;
					releaseCancelled = true;
					return;
				}
				slideFretLabelHandler.clearSelection();
			}

			cancelAllActions();
			if (pressCancelsRelease) {
				releaseCancelled = true;
				return;
			} else {
				pressCancelsRelease = true;
				releaseCancelled = false;
			}

			mouseButtonPressReleaseHandler.press(e);
		} catch (final Exception ex) {
			Logger.error("Exception on mouse pressed", ex);
		}
	}

	private void createFHPFromNote(final ChordOrNote sound) {
		final int fret;
		if (sound.isNote()) {
			if (sound.note().fret == 0) {
				return;
			}
			fret = sound.note().fret;
		} else {
			final ChordTemplate template = chartData.currentChordTemplates().get(sound.chord().templateId());
			fret = template.frets.values().stream().filter(f -> f > 0).min(Integer::compareTo).orElse(0);
			if (fret == 0) {
				return;
			}
		}

		undoSystem.addUndo();

		final List<FHP> fhps = chartData.currentFHPs();
		fhps.removeIf(fhp -> fhp.position().equals(sound.position()));
		fhps.add(new FHP(sound.position(), fret));
		fhps.sort(IConstantFractionalPosition::compareTo);
	}

	private void leftClickGuitar(final MouseButtonPressReleaseData clickData, final boolean isDoubleClick) {
		if (keyboardHandler.alt() && !clickData.isXDrag()
				&& clickData.pressHighlight.type == PositionType.GUITAR_NOTE
				&& clickData.pressHighlight.chordOrNote != null) {
			createFHPFromNote(clickData.pressHighlight.chordOrNote);
			return;
		}

		if (!clickData.isXDrag() || keyboardHandler.scrollLock()) {
			selectionManager.click(clickData, keyboardHandler.ctrl(), keyboardHandler.shift());
		} else if (keyboardHandler.shift() && clickData.pressHighlight.type == PositionType.GUITAR_NOTE) {
			stretchSounds(clickData, chartData.currentSounds());
		} else {
			if (clickData.pressHighlight.type == PositionType.EVENT_POINT) {
				dragPositions(PositionType.EVENT_POINT, clickData, chartData.currentEventPoints());
			}
			if (clickData.pressHighlight.type == PositionType.TONE_CHANGE) {
				dragPositions(PositionType.TONE_CHANGE, clickData, chartData.currentToneChanges());
			}
			if (clickData.pressHighlight.type == PositionType.FHP) {
				dragPositions(PositionType.FHP, clickData, chartData.currentFHPs());
			}
			if (clickData.pressHighlight.type == PositionType.GUITAR_NOTE) {
				dragSounds(clickData, chartData.currentSounds());
			}
			if (clickData.pressHighlight.type == PositionType.HAND_SHAPE) {
				// Pass false for fixLengths - overlapping handshapes with DIFFERENT templateIds are valid
				dragPositionsWithLength(PositionType.HAND_SHAPE, clickData, chartData.currentHandShapes(), false);
				// But fix overlaps between handshapes with the SAME templateId
				arrangementFixer.fixSameTemplateHandShapeOverlaps();
			}
		}

		if (isDoubleClick && clickData.pressHighlight.handShape != null) {
			// No addUndo for edit-only: cancel must not call undo (same pattern as FHP / event point panes).
			new HandShapePane(chartData, charterFrame, chordTemplatesEditorTab, clickData.pressHighlight.handShape,
					() -> {});
		}
	}

	private void leftClickVocals(final MouseButtonPressReleaseData clickData, final boolean isDoubleClick) {
		if (!clickData.isXDrag() || keyboardHandler.scrollLock()) {
			selectionManager.click(clickData, keyboardHandler.ctrl(), keyboardHandler.shift());
		} else if (clickData.pressHighlight.type == PositionType.VOCAL) {
			dragPositionsWithLength(PositionType.VOCAL, clickData, chartData.currentVocals().vocals, true);
		}

		if (isDoubleClick && clickData.pressHighlight.vocal != null) {
			new VocalPane(clickData.pressHighlight.id, clickData.pressHighlight.vocal, chartData, charterFrame,
					selectionManager, undoSystem, liveLyricsHandler);
		}
	}

	private boolean isLeftDoubleClick(final MouseButtonPressReleaseData clickData) {
		return lastClickId != null && lastClickId.equals(clickData.pressHighlight.id) //
				&& System.currentTimeMillis() - lastLeftClickTime < 300;
	}

	private void dragTempo(final MouseButtonPressReleaseData clickData) {
		final double to = xToPosition(clickData.releasePosition.x, chartTimeHandler.displayTime());
		beatsService.dragTempo(chartData.songChart.beatsMap, clickData.pressHighlight, to);
	}

	private void handleClick(final MouseButtonPressReleaseData clickData) {
		switch (clickData.button) {
			case LEFT_BUTTON:
				final boolean doubleClick = isLeftDoubleClick(clickData);
				lastLeftClickTime = System.currentTimeMillis();
				lastClickId = clickData.pressHighlight.id;

			switch (modeManager.getMode()) {
					case GUITAR -> leftClickGuitar(clickData, doubleClick);
					case TEMPO_MAP -> dragTempo(clickData);
					case VOCALS -> leftClickVocals(clickData, doubleClick);
					default -> {}
				}
				break;
			case RIGHT_BUTTON:
				modeManager.getHandler().rightClick(clickData);
				break;
			default:
				break;
		}
	}

	@Override
	public void mouseReleased(final MouseEvent e) {
		Logger.debug("Mouse released, key: " + e.getButton() + ", position: (" + e.getX() + "," + e.getY() + ")");

		try {
			mouseMoved(e);

			if (chartData.isEmpty) {
				return;
			}

			pressCancelsRelease = false;
			if (releaseCancelled) {
				return;
			}

			final MouseButtonPressReleaseData clickData = mouseButtonPressReleaseHandler.release(e);
			if (clickData == null) {
				return;
			}

			handleClick(clickData);

			mouseButtonPressReleaseHandler.remove(e);
			cancelAllActions();
			actionHandler.clearNumbers();
		} catch (final Exception ex) {
			Logger.error("Exception on mouse released", ex);
		}
	}

	private void reselectDraggedPositions(final PositionType type,
			final List<? extends IVirtualConstantPosition> moved) {
		selectionManager.clear();
		selectionManager.addSelectionForPositions(type, moved);
	}

	private IVirtualConstantPosition findGridPositionClosestToX(final int x) {
		return chartData.beats().getPositionFromGridClosestTo(new Position(xToPosition(x, chartTimeHandler.displayTime())));
	}

	private <T extends IVirtualPosition> void dragPositions(final PositionType type,
			final MouseButtonPressReleaseData clickData, final List<T> allPositions) {
		List<Selection<T>> selectedPositions = selectionManager.<T>accessor(type).getSelected();

		if (clickData.pressHighlight.existingPosition//
				&& !contains(selectedPositions, s -> s.id == clickData.pressHighlight.id)) {
			selectionManager.clear();
			selectionManager.addSelection(type, clickData.pressHighlight.id);
			selectedPositions = selectionManager.<T>accessor(type)//
					.getSelected();
		}
		if (selectedPositions.isEmpty()) {
			return;
		}

		final List<T> positions = map(selectedPositions, p -> p.selectable);
		undoSystem.addUndo();

		final IConstantFractionalPosition dragFrom = clickData.pressHighlight.toFraction(chartData.beats());
		final IConstantFractionalPosition dragTo = findGridPositionClosestToX(clickData.releasePosition.x)
				.toFraction(chartData.beats());

		chartData.beats().movePositions(positions, dragFrom.movementTo(dragTo));

		allPositions.sort(IVirtualConstantPosition.comparator(chartData.beats()));

		reselectDraggedPositions(type, positions);
	}

	private <T extends IVirtualPositionWithEnd> void dragPositionsWithLength(final PositionType type,
			final MouseButtonPressReleaseData clickData, final List<T> allPositions, final boolean fixLengths) {
		List<Selection<T>> selectedPositions = selectionManager.<T>accessor(clickData.pressHighlight.type)//
				.getSelected();

		if (clickData.pressHighlight.existingPosition//
				&& !contains(selectedPositions, s -> s.id == clickData.pressHighlight.id)) {
			selectionManager.clear();
			selectionManager.addSelection(clickData.pressHighlight.type, clickData.pressHighlight.id);
			selectedPositions = selectionManager.<T>accessor(clickData.pressHighlight.type)//
					.getSelected();
		}
		if (selectedPositions.isEmpty()) {
			return;
		}

		final List<T> positions = map(selectedPositions, p -> p.selectable);

		undoSystem.addUndo();

		final IConstantFractionalPosition dragFrom = clickData.pressHighlight.toFraction(chartData.beats());
		final IConstantFractionalPosition dragTo = findGridPositionClosestToX(clickData.releasePosition.x)
				.toFraction(chartData.beats());
		chartData.beats().movePositions(positions, dragFrom.movementTo(dragTo));

		allPositions.sort(IVirtualConstantPosition.comparator(chartData.beats()));

		// Note: For handshapes, fixLengths should be false because overlapping handshapes 
		// are valid in Rocksmith (used for arpeggios/fingerpicking)
		if (fixLengths) {
			arrangementFixer.fixLengths(allPositions);
		}

		reselectDraggedPositions(type, positions);
	}

	private void moveSoundByDelta(final ChordOrNote sound, final FractionalPosition delta) {
		sound.position(sound.position().add(delta));
		if (sound.isChord()) {
			for (final ChordNote chordNote : sound.chord().chordNotes.values()) {
				chordNote.endPosition(chordNote.endPosition().add(delta));
				chordNote.bendValues.forEach(b -> b.position(b.position().add(delta)));
			}
		} else {
			final Note note = sound.note();
			note.endPosition(note.endPosition().add(delta));
			note.bendValues.forEach(b -> b.position(b.position().add(delta)));
		}
	}

	private void stretchSounds(final MouseButtonPressReleaseData clickData, final List<ChordOrNote> allPositions) {
		List<Selection<ChordOrNote>> selectedPositions = selectionManager
				.<ChordOrNote>accessor(clickData.pressHighlight.type).getSelected();

		if (clickData.pressHighlight.existingPosition
				&& !contains(selectedPositions, s -> s.id == clickData.pressHighlight.id)) {
			selectionManager.clear();
			selectionManager.addSelection(clickData.pressHighlight.type, clickData.pressHighlight.id);
			selectedPositions = selectionManager.<ChordOrNote>accessor(clickData.pressHighlight.type).getSelected();
		}

		if (selectedPositions.size() < 2) {
			dragSounds(clickData, allPositions);
			return;
		}

		final List<ChordOrNote> positions = map(selectedPositions, p -> (ChordOrNote) p.selectable);
		positions.sort(IConstantFractionalPosition::compareTo);

		final ImmutableBeatsMap beats = chartData.beats();
		final double anchorGrid = positions.get(0).position().doubleValue() * 64;
		final double originalLastGrid = positions.get(positions.size() - 1).position().doubleValue() * 64;

		if (originalLastGrid <= anchorGrid) {
			dragSounds(clickData, allPositions);
			return;
		}

		final FractionalPosition newLastFrac = findGridPositionClosestToX(clickData.releasePosition.x)
				.toFraction(beats).position();
		final double newLastGrid = newLastFrac.doubleValue() * 64;
		final double scale = (newLastGrid - anchorGrid) / (originalLastGrid - anchorGrid);

		undoSystem.addUndo();
		final Map<FHP, FractionalPosition> fhpDeltas = new HashMap<>();
		final Map<HandShape, FractionalPosition> handShapeDeltas = new HashMap<>();

		final int lastIndex = positions.size() - 1;
		for (int i = 1; i <= lastIndex; i++) {
			final ChordOrNote sound = positions.get(i);
			final double originalGrid = sound.position().doubleValue() * 64;
			final double newGrid = anchorGrid + (originalGrid - anchorGrid) * scale;

			final FractionalPosition newPos = (i == lastIndex)
					? newLastFrac
					: new FractionalPosition(new Fraction((int) Math.round(newGrid), 64));
			final FractionalPosition delta = sound.position().movementTo(newPos);

			boolean hasSlide = false;
			boolean hasLinkNext = false;
			if (sound.isNote()) {
				hasSlide = sound.note().slideTo != null || sound.note().unpitchedSlide;
				hasLinkNext = sound.note().linkNext;
			} else {
				hasSlide = sound.chord().chordNotes.values().stream()
						.anyMatch(cn -> cn.slideTo != null || cn.unpitchedSlide);
				hasLinkNext = sound.chord().chordNotes.values().stream().anyMatch(cn -> cn.linkNext);
			}
			for (final FHP fhp : chartData.currentFHPs()) {
				if (fhp.position().equals(sound.position())) {
					fhpDeltas.put(fhp, delta);
				}
				if (hasSlide && !hasLinkNext && fhp.position().equals(sound.endPosition())) {
					fhpDeltas.put(fhp, delta);
				}
			}
			for (final HandShape handShape : chartData.currentHandShapes()) {
				if (handShape.position().equals(sound.position())) {
					handShapeDeltas.put(handShape, delta);
				}
			}

			moveSoundByDelta(sound, delta);
		}

		for (final Map.Entry<FHP, FractionalPosition> entry : fhpDeltas.entrySet()) {
			final FHP fhp = entry.getKey();
			fhp.position(fhp.position().add(entry.getValue()));
		}
		if (!fhpDeltas.isEmpty()) {
			chartData.currentFHPs().sort(IVirtualConstantPosition.comparator(chartData.beats()));
			final List<FHP> fhps = chartData.currentFHPs();
			for (int i = fhps.size() - 1; i > 0; i--) {
				if (fhps.get(i).position().equals(fhps.get(i - 1).position())) {
					fhps.remove(i);
				}
			}
		}

		for (final Map.Entry<HandShape, FractionalPosition> entry : handShapeDeltas.entrySet()) {
			final HandShape hs = entry.getKey();
			final FractionalPosition delta = entry.getValue();
			hs.position(hs.position().add(delta));
			hs.endPosition(hs.endPosition().add(delta));
		}
		if (!handShapeDeltas.isEmpty()) {
			chartData.currentHandShapes().sort(IVirtualConstantPosition.comparator(chartData.beats()));
		}

		allPositions.sort(IConstantFractionalPosition::compareTo);
		arrangementFixer.fixNoteLengths(allPositions);
		reselectDraggedPositions(PositionType.GUITAR_NOTE, positions);
	}

	private void dragSounds(final MouseButtonPressReleaseData clickData, final List<ChordOrNote> allPositions) {
		List<Selection<ChordOrNote>> selectedPositions = selectionManager
				.<ChordOrNote>accessor(clickData.pressHighlight.type).getSelected();

		if (clickData.pressHighlight.existingPosition//
				&& !contains(selectedPositions, s -> s.id == clickData.pressHighlight.id)) {
			selectionManager.clear();
			selectionManager.addSelection(clickData.pressHighlight.type, clickData.pressHighlight.id);
			selectedPositions = selectionManager.<ChordOrNote>accessor(clickData.pressHighlight.type)//
					.getSelected();
		}
		if (selectedPositions.isEmpty()) {
			return;
		}

		final List<ChordOrNote> positions = map(selectedPositions, p -> (ChordOrNote) p.selectable);
		undoSystem.addUndo();

		final IConstantFractionalPosition dragFrom = clickData.pressHighlight.toFraction(chartData.beats());
		final IConstantFractionalPosition dragTo = findGridPositionClosestToX(clickData.releasePosition.x)
				.toFraction(chartData.beats());

		final List<FHP> fhpsToMove = new ArrayList<>();
		for (final ChordOrNote sound : positions) {
			boolean hasSlide = false;
			boolean hasLinkNext = false;
			if (sound.isNote()) {
				hasSlide = sound.note().slideTo != null || sound.note().unpitchedSlide;
				hasLinkNext = sound.note().linkNext;
			} else {
				hasSlide = sound.chord().chordNotes.values().stream()
						.anyMatch(cn -> cn.slideTo != null || cn.unpitchedSlide);
				hasLinkNext = sound.chord().chordNotes.values().stream()
						.anyMatch(cn -> cn.linkNext);
			}

			for (final FHP fhp : chartData.currentFHPs()) {
				if (fhp.position().equals(sound.position())) {
					if (!fhpsToMove.contains(fhp)) {
						fhpsToMove.add(fhp);
					}
				}
				if (hasSlide && !hasLinkNext && fhp.position().equals(sound.endPosition())) {
					if (!fhpsToMove.contains(fhp)) {
						fhpsToMove.add(fhp);
					}
				}
			}
		}

		chartData.beats().moveSounds(positions, dragFrom.movementTo(dragTo));
		if (!fhpsToMove.isEmpty()) {
			chartData.beats().movePositions(fhpsToMove, dragFrom.movementTo(dragTo));
			chartData.currentFHPs().sort(IVirtualConstantPosition.comparator(chartData.beats()));
			
			final List<FHP> fhps = chartData.currentFHPs();
			for (int i = fhps.size() - 1; i > 0; i--) {
				if (fhps.get(i).position().equals(fhps.get(i - 1).position())) {
					if (fhpsToMove.contains(fhps.get(i))) {
						fhps.remove(i - 1);
					} else {
						fhps.remove(i);
					}
				}
			}
		}

		allPositions.sort(IConstantFractionalPosition::compareTo);

		arrangementFixer.fixNoteLengths(allPositions);

		reselectDraggedPositions(PositionType.GUITAR_NOTE, positions);
	}

	private void stretchSelectedByFactor(final double scale) {
		final List<Selection<ChordOrNote>> selectedPositions = selectionManager
				.<ChordOrNote>accessor(PositionType.GUITAR_NOTE).getSelected();
		if (selectedPositions.size() < 2) {
			return;
		}

		final List<ChordOrNote> positions = map(selectedPositions, p -> (ChordOrNote) p.selectable);
		positions.sort(IConstantFractionalPosition::compareTo);

		final double anchorGrid = positions.get(0).position().doubleValue() * 64;
		final double originalLastGrid = positions.get(positions.size() - 1).position().doubleValue() * 64;

		if (originalLastGrid <= anchorGrid) {
			return;
		}

		undoSystem.addUndo();

		final Map<FHP, FractionalPosition> fhpDeltas = new HashMap<>();
		final Map<HandShape, FractionalPosition> handShapeDeltas = new HashMap<>();

		for (int i = 1; i < positions.size(); i++) {
			final ChordOrNote sound = positions.get(i);
			final double originalGrid = sound.position().doubleValue() * 64;
			final double newGrid = anchorGrid + (originalGrid - anchorGrid) * scale;
			final FractionalPosition newPos = new FractionalPosition(new Fraction((int) Math.round(newGrid), 64));
			final FractionalPosition delta = sound.position().movementTo(newPos);

			boolean hasSlide = false;
			boolean hasLinkNext = false;
			if (sound.isNote()) {
				hasSlide = sound.note().slideTo != null || sound.note().unpitchedSlide;
				hasLinkNext = sound.note().linkNext;
			} else {
				hasSlide = sound.chord().chordNotes.values().stream()
						.anyMatch(cn -> cn.slideTo != null || cn.unpitchedSlide);
				hasLinkNext = sound.chord().chordNotes.values().stream().anyMatch(cn -> cn.linkNext);
			}
			for (final FHP fhp : chartData.currentFHPs()) {
				if (fhp.position().equals(sound.position())) {
					fhpDeltas.put(fhp, delta);
				}
				if (hasSlide && !hasLinkNext && fhp.position().equals(sound.endPosition())) {
					fhpDeltas.put(fhp, delta);
				}
			}
			for (final HandShape handShape : chartData.currentHandShapes()) {
				if (handShape.position().equals(sound.position())) {
					handShapeDeltas.put(handShape, delta);
				}
			}

			moveSoundByDelta(sound, delta);
		}

		for (final Map.Entry<FHP, FractionalPosition> entry : fhpDeltas.entrySet()) {
			final FHP fhp = entry.getKey();
			fhp.position(fhp.position().add(entry.getValue()));
		}
		if (!fhpDeltas.isEmpty()) {
			chartData.currentFHPs().sort(IVirtualConstantPosition.comparator(chartData.beats()));
			final List<FHP> fhps = chartData.currentFHPs();
			for (int i = fhps.size() - 1; i > 0; i--) {
				if (fhps.get(i).position().equals(fhps.get(i - 1).position())) {
					fhps.remove(i);
				}
			}
		}

		for (final Map.Entry<HandShape, FractionalPosition> entry : handShapeDeltas.entrySet()) {
			final HandShape hs = entry.getKey();
			hs.position(hs.position().add(entry.getValue()));
			hs.endPosition(hs.endPosition().add(entry.getValue()));
		}
		if (!handShapeDeltas.isEmpty()) {
			chartData.currentHandShapes().sort(IVirtualConstantPosition.comparator(chartData.beats()));
		}

		chartData.currentSounds().sort(IConstantFractionalPosition::compareTo);
		arrangementFixer.fixNoteLengths(chartData.currentSounds());
		reselectDraggedPositions(PositionType.GUITAR_NOTE, positions);
	}

	public void openStretchDialog() {
		final List<Selection<ChordOrNote>> selectedPositions = selectionManager
				.<ChordOrNote>accessor(PositionType.GUITAR_NOTE).getSelected();
		if (selectedPositions.size() < 2) {
			return;
		}

		final List<ChordOrNote> positions = map(selectedPositions, p -> (ChordOrNote) p.selectable);
		positions.sort(IConstantFractionalPosition::compareTo);

		final double anchorGrid = positions.get(0).position().doubleValue() * 64;
		final double originalLastGrid = positions.get(positions.size() - 1).position().doubleValue() * 64;

		if (originalLastGrid <= anchorGrid) {
			return;
		}

		final double songGrid = chartTimeHandler.maxTimeFractional().doubleValue() * 64;
		final double maxScale = (songGrid - anchorGrid) / (originalLastGrid - anchorGrid);
		new StretchSelectionPane(charterFrame, maxScale, this::stretchSelectedByFactor);
	}

	@Override
	public void mouseDragged(final MouseEvent e) {
		mouseMoved(e);
	}

	@Override
	public void mouseMoved(final MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
	}

	public int getMouseX() {
		return mouseX;
	}

	public int getMouseY() {
		return mouseY;
	}

	public PositionType getMouseHoverPositionType() {
		return PositionType.fromY(mouseY, modeManager.getMode());
	}

	public void cancelAllActions() {
		mouseButtonPressReleaseHandler.clear();
	}

	@Override
	public void mouseWheelMoved(final MouseWheelEvent e) {
		Logger.debug("Mouse wheel moved, rotation: " + e.getWheelRotation());

		if (chartData.isEmpty) {
			return;
		}

		try {
			final int change = -e.getWheelRotation();
			if (keyboardHandler.ctrl()) {
				final int zoomChange = change * (keyboardHandler.shift() ? 8 : 1);
				ZoomUtils.changeZoom(zoomChange);
				return;
			}

			if (!selectionManager.selectedAccessor().isSelected()) {
				return;
			}

			if (keyboardHandler.alt()) {
				if (keyboardHandler.shift()) {
					modeManager.getHandler().changeBendValue(change);
				} else {
					modeManager.getHandler().changeSlideFret(change);
				}
				return;
			}

			modeManager.getHandler().changeLength(change);
		} catch (final Exception ex) {
			Logger.error("Exception on mouse wheel moved", ex);
		}
	}

}
