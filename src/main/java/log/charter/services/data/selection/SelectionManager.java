package log.charter.services.data.selection;

import static log.charter.data.config.Config.selectNotesByTails;
import static log.charter.gui.chartPanelDrawers.common.DrawerUtils.yToString;
import static log.charter.util.CollectionUtils.closest;
import static log.charter.util.ScalingUtils.xToPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import log.charter.data.ChartData;
import log.charter.data.song.BeatsMap.ImmutableBeatsMap;
import log.charter.data.song.BendValue;
import log.charter.data.song.ChordTemplate;
import log.charter.data.song.HandShape;
import log.charter.data.song.notes.Chord;
import log.charter.data.song.notes.ChordNote;
import log.charter.data.song.notes.ChordOrNote;
import log.charter.data.song.notes.Note;
import log.charter.data.song.position.FractionalPosition;
import log.charter.services.editModes.EditMode;
import log.charter.data.song.position.time.ConstantPosition;
import log.charter.data.song.position.time.IConstantPosition;
import log.charter.data.song.position.virtual.IVirtualConstantPosition;
import log.charter.data.song.vocals.Vocal;
import log.charter.data.types.PositionType;
import log.charter.data.types.PositionWithIdAndType;
import log.charter.gui.components.tabs.selectionEditor.CurrentSelectionEditor;
import log.charter.services.CharterContext;
import log.charter.services.CharterContext.Initiable;
import log.charter.services.data.ChartTimeHandler;
import log.charter.services.editModes.ModeManager;
import log.charter.services.mouseAndKeyboard.MouseButtonPressReleaseHandler.MouseButtonPressReleaseData;
import log.charter.services.mouseAndKeyboard.MouseHandler;
import log.charter.util.collections.HashMap2;

public class SelectionManager implements Initiable {
	private CharterContext charterContext;
	private ChartData chartData;
	private ChartTimeHandler chartTimeHandler;
	private CurrentSelectionEditor currentSelectionEditor;
	private ModeManager modeManager;
	private MouseHandler mouseHandler;

	private final Map<PositionType, SelectionList<?, ?, ?>> selectionLists = new HashMap2<>();

	/**
	 * When editing a chord, this holds the specific string number that is selected
	 * for individual note editing. When null, the whole chord is selected.
	 */
	private Integer selectedChordNoteString = null;

	/**
	 * Tracks the last clicked handshape ID for cycling through overlapping handshapes.
	 * When clicking at the same position with overlapping handshapes, we cycle to the next one.
	 */
	private Integer lastClickedHandShapeId = null;

	@Override
	public void init() {
		for (final PositionType type : PositionType.values()) {
			final SelectionList<?, ?, ?> typeSelectionManager = new SelectionList<>(type);
			charterContext.initObject(typeSelectionManager);
			selectionLists.put(type, typeSelectionManager);
		}
	}

	private void clearSelectionsExcept(final PositionType typeNotToClear) {
		selectionLists.forEach((type, manager) -> {
			if (type != typeNotToClear) {
				manager.clear();
			}
		});
	}

	private class PositionWithLink implements IConstantPosition {
		private final double position;
		public final PositionWithIdAndType link;

		public PositionWithLink(final double position, final PositionWithIdAndType link) {
			this.position = position;
			this.link = link;
		}

		@Override
		public double position() {
			return position;
		}
	}

	public List<PositionWithLink> generateLinks(final List<PositionWithIdAndType> positions) {
		final List<PositionWithLink> newPositions = new ArrayList<>(positions.size());

		final ImmutableBeatsMap beats = chartData.beats();
		for (final PositionWithIdAndType position : positions) {
			newPositions.add(new PositionWithLink(position.toPosition(beats).position(), position));
		}

		return newPositions;
	}

	public List<PositionWithLink> generateLinksWithLength(final List<PositionWithIdAndType> positions) {
		final List<PositionWithLink> newPositions = new ArrayList<>(positions.size() * 2);

		final ImmutableBeatsMap beats = chartData.beats();
		for (final PositionWithIdAndType position : positions) {
			newPositions.add(new PositionWithLink(position.toPosition(beats).position(), position));
			newPositions.add(new PositionWithLink(position.endPosition().toPosition(beats).position(), position));
		}

		return newPositions;
	}

	private PositionWithIdAndType findExisting(final int x, final List<PositionWithLink> positionsWithLinks) {
		final double position = xToPosition(x, chartTimeHandler.displayTime());
		final PositionWithLink closestLink = closest(positionsWithLinks, new ConstantPosition(position)).find();
		if (closestLink == null) {
			return null;
		}

		final PositionWithIdAndType closest = closestLink.link;
		final int closestX = chartTimeHandler.positionToX(closest.asConstantPosition().position());
		if (x - closestX < -20 || x - closestX > 20) {
			return null;
		}

		return closest;
	}

	private PositionWithIdAndType findWithLengthExisting(final int x, final List<PositionWithLink> positionsWithLinks) {
		final double position = xToPosition(x, chartTimeHandler.displayTime());
		final PositionWithLink closestLink = closest(positionsWithLinks, new ConstantPosition(position)).find();
		if (closestLink == null) {
			return null;
		}

		final PositionWithIdAndType closest = closestLink.link;
		final int closestX = chartTimeHandler.positionToX(closest.asConstantPosition().position());
		final int closestEndX = chartTimeHandler.positionToX(closest.endPosition().asConstantPosition().position());
		if (x - closestX < -20 || x - closestEndX > 20) {
			return null;
		}

		return closest;
	}

	/**
	 * Finds all handshapes that contain the given x position (for overlapping handshape cycling)
	 */
	private List<PositionWithIdAndType> findAllHandShapesAtPosition(final int x, final List<PositionWithIdAndType> positions) {
		final List<PositionWithIdAndType> result = new ArrayList<>();
		
		for (final PositionWithIdAndType pos : positions) {
			final int startX = chartTimeHandler.positionToX(pos.asConstantPosition().position());
			final int endX = chartTimeHandler.positionToX(pos.endPosition().asConstantPosition().position());
			// Check if x is within the handshape bounds (with some tolerance)
			if (x >= startX - 20 && x <= endX + 20) {
				result.add(pos);
			}
		}
		
		return result;
	}

	/**
	 * For highlighting - returns the handshape with visual precedence (last one in list at position).
	 * Does NOT update any state or change what's displayed.
	 */
	private PositionWithIdAndType findHandShapeForHighlight(final int x, final List<PositionWithIdAndType> positions) {
		final List<PositionWithIdAndType> overlapping = findAllHandShapesAtPosition(x, positions);
		
		if (overlapping.isEmpty()) {
			return null;
		}
		
		// Return the LAST overlapping handshape - this is the one with visual precedence
		// (drawn on top because it comes later in the list)
		return overlapping.get(overlapping.size() - 1);
	}

	/**
	 * For actual clicks - cycles through overlapping handshapes, updates tracking state,
	 * and reorders the list to give the selected handshape visual precedence.
	 */
	public PositionWithIdAndType cycleHandShapeOnClick(final int x, final int y) {
		final PositionType positionType = PositionType.fromY(y, modeManager.getMode());
		if (positionType != PositionType.HAND_SHAPE) {
			return null;
		}
		
		final List<PositionWithIdAndType> positions = positionType.getPositionsWithIdsAndTypes(chartData);
		final List<PositionWithIdAndType> overlapping = findAllHandShapesAtPosition(x, positions);
		
		if (overlapping.isEmpty()) {
			return null;
		}
		
		if (overlapping.size() == 1) {
			lastClickedHandShapeId = overlapping.get(0).id;
			return overlapping.get(0);
		}
		
		// Multiple overlapping - the one with visual precedence is the LAST one in the overlapping list
		// (because it's last in the main list, so drawn on top)
		// Cycle backwards to reveal what's underneath
		final int nextIndex = overlapping.size() - 2; // Second to last, which is "underneath" the current precedent
		final PositionWithIdAndType next = overlapping.get(nextIndex < 0 ? overlapping.size() - 1 : nextIndex);
		
		// Move the 'next' handshape to the end of the main list to give it visual precedence
		final List<HandShape> handShapes = chartData.currentHandShapes();
		final HandShape handShapeToMove = next.handShape;
		handShapes.remove(handShapeToMove);
		handShapes.add(handShapeToMove);
		
		// Update the lastClickedHandShapeId and return with the updated ID
		final int newId = handShapes.indexOf(handShapeToMove);
		lastClickedHandShapeId = newId;
		
		return PositionWithIdAndType.of(chartData.beats(), newId, handShapeToMove);
	}

	public PositionWithIdAndType findExistingPosition(final int x, final int y) {
		final PositionType positionType = PositionType.fromY(y, modeManager.getMode());
		final List<PositionWithIdAndType> positions = positionType.getPositionsWithIdsAndTypes(chartData);

		// Special handling for handshapes - use highlight logic (no cycling)
		if (positionType == PositionType.HAND_SHAPE) {
			return findHandShapeForHighlight(x, positions);
		}

		if (positionType == PositionType.VOCAL//
				|| (positionType == PositionType.GUITAR_NOTE && selectNotesByTails)) {
			return findWithLengthExisting(x, generateLinksWithLength(positions));
		}

		return findExisting(x, generateLinks(positions));
	}

	public void click(final MouseButtonPressReleaseData clickData, final boolean ctrl, final boolean shift) {
		if (chartData.isEmpty) {
			return;
		}

		PositionWithIdAndType highlight = clickData.pressHighlight;
		if (!highlight.existingPosition) {
			final PositionType positionType = PositionType.fromY(clickData.pressPosition.y, modeManager.getMode());
			if (positionType == PositionType.GUITAR_NOTE) {
				final List<PositionWithIdAndType> positions = positionType.getPositionsWithIdsAndTypes(chartData);
				final PositionWithIdAndType bodyHit = findWithLengthExisting(clickData.pressPosition.x,
						generateLinksWithLength(positions));
				if (bodyHit != null) {
					highlight = bodyHit;
				}
			}
		}

		if (!highlight.existingPosition) {
			if (!ctrl) {
				clearSelectionsExcept(PositionType.NONE);
			}
			selectedChordNoteString = null;

			currentSelectionEditor.selectionChanged(true);
			return;
		}

		// Check if clicking on an already-selected chord to select a specific note
		if (highlight.type == PositionType.GUITAR_NOTE && !ctrl && !shift) {
			final Set<Integer> selectedIds = accessor(PositionType.GUITAR_NOTE).getSelectedIdsSet(PositionType.GUITAR_NOTE);
			if (selectedIds.contains(highlight.id) && highlight.chordOrNote != null
					&& highlight.chordOrNote.isChord()) {
				// Clicking on an already-selected chord - select specific string
				final int clickedString = yToString(clickData.pressPosition.y, chartData.currentStrings());
				// Verify the chord has a note on this string
				if (highlight.chordOrNote.chord().chordNotes.containsKey(clickedString)) {
					// If clicking the same string again, deselect it (go back to whole chord selection)
					if (selectedChordNoteString != null && selectedChordNoteString == clickedString) {
						selectedChordNoteString = null;
					} else {
						selectedChordNoteString = clickedString;
					}
					currentSelectionEditor.selectionChanged(false);
					return;
				}
			}
		}

		// Clear chord note selection when selecting different sound
		selectedChordNoteString = null;

		// Special handling for handshapes - cycle through overlapping ones on click
		if (highlight.type == PositionType.HAND_SHAPE && !ctrl && !shift) {
			final PositionWithIdAndType cycledHandShape = cycleHandShapeOnClick(
					clickData.pressPosition.x, clickData.pressPosition.y);
			if (cycledHandShape != null && cycledHandShape.id != null) {
				clearSelectionsExcept(PositionType.HAND_SHAPE);
				final SelectionList<?, ?, ?> selectionList = selectionLists.get(PositionType.HAND_SHAPE);
				if (selectionList != null) {
					selectionList.addSelectablesWithModifiers(cycledHandShape.id, false, false);
				}
				currentSelectionEditor.selectionChanged(true);
				return;
			}
		}

		clearSelectionsExcept(highlight.type);

		final SelectionList<?, ?, ?> selectionList = selectionLists.get(highlight.type);
		if (selectionList == null) {
			currentSelectionEditor.selectionChanged(true);
			return;
		}

		selectionList.addSelectablesWithModifiers(highlight.id, ctrl, shift);
		currentSelectionEditor.selectionChanged(true);
	}

	public void clear() {
		clearSelectionsExcept(PositionType.NONE);
		selectedChordNoteString = null;
		currentSelectionEditor.selectionChanged(true);
	}

	public void restoreSelection(final PositionType type, final List<Integer> ids, final Integer chordNoteString) {
		if (type == PositionType.NONE || ids.isEmpty()) {
			return;
		}

		final int size = type.getPositionsWithIdsAndTypes(chartData).size();
		final List<Integer> validIds = new ArrayList<>();
		for (final int id : ids) {
			if (id >= 0 && id < size) {
				validIds.add(id);
			}
		}

		if (validIds.isEmpty()) {
			return;
		}

		selectionLists.get(type).add(validIds);
		selectedChordNoteString = chordNoteString;
		currentSelectionEditor.selectionChanged(true);
	}

	/**
	 * Gets the currently selected chord note string for individual editing.
	 * @return the string number (0-based) if a specific chord note is selected, null if whole chord is selected
	 */
	public Integer getSelectedChordNoteString() {
		return selectedChordNoteString;
	}

	/**
	 * Sets the specific chord note string for individual editing.
	 * @param string the string number (0-based), or null to select whole chord
	 */
	public void setSelectedChordNoteString(final Integer string) {
		this.selectedChordNoteString = string;
	}

	/**
	 * Clears the selected chord note string, returning to whole-chord selection mode.
	 */
	public void clearSelectedChordNoteString() {
		this.selectedChordNoteString = null;
	}

	@SuppressWarnings("unchecked")
	public <T extends IVirtualConstantPosition> ISelectionAccessor<T> accessor(final PositionType type) {
		final SelectionList<?, ?, ?> selectionList = selectionLists.get(type);
		if (selectionList == null) {
			return new NoneSelectionAccessor<>();
		}

		return (ISelectionAccessor<T>) selectionList.getAccessor();
	}

	public List<Integer> getSelectedIds(final PositionType type) {
		return accessor(type).getSelectedIds(type);
	}

	public <T extends IVirtualConstantPosition> List<Selection<T>> getSelected(final PositionType type) {
		return this.<T>accessor(type).getSelected();
	}

	public <T extends IVirtualConstantPosition> List<T> getSelectedElements(final PositionType type) {
		return this.<T>accessor(type).getSelectedElements();
	}

	public List<Selection<Vocal>> getSelectedVocals() {
		return getSelected(PositionType.VOCAL);
	}

	public PositionType selectedType() {
		for (final SelectionList<?, ?, ?> selectionList : selectionLists.values()) {
			if (selectionList.getAccessor().isSelected()) {
				return selectionList.type;
			}
		}

		return PositionType.NONE;
	}

	@SuppressWarnings("unchecked")
	public <T extends IVirtualConstantPosition> ISelectionAccessor<T> selectedAccessor() {
		for (final SelectionList<?, ?, ?> selectionList : selectionLists.values()) {
			final ISelectionAccessor<T> accessor = (ISelectionAccessor<T>) selectionList.getAccessor();
			if (accessor.isSelected()) {
				return accessor;
			}
		}

		return new NoneSelectionAccessor<T>();
	}

	private FractionalPosition relativeDuration(final FractionalPosition start, final FractionalPosition end) {
		return end.add(start.negate());
	}

	private boolean bendValuesAreEqual(final List<BendValue> a, final List<BendValue> b,
			final FractionalPosition aPosRef, final FractionalPosition bPosRef) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			final BendValue bvA = a.get(i);
			final BendValue bvB = b.get(i);
			if (!relativeDuration(aPosRef, bvA.position()).equals(relativeDuration(bPosRef, bvB.position()))) {
				return false;
			}
			if (!bvA.bendValue.equals(bvB.bendValue)) {
				return false;
			}
		}
		return true;
	}

	private boolean notesAreEqual(final Note a, final Note b) {
		if (a.string != b.string || a.fret != b.fret) {
			return false;
		}
		if (!relativeDuration(a.position(), a.endPosition()).equals(relativeDuration(b.position(), b.endPosition()))) {
			return false;
		}
		return a.bassPicking == b.bassPicking
				&& a.mute == b.mute
				&& a.hopo == b.hopo
				&& a.harmonic == b.harmonic
				&& a.vibrato == b.vibrato
				&& a.tremolo == b.tremolo
				&& a.linkNext == b.linkNext
				&& Objects.equals(a.slideTo, b.slideTo)
				&& a.unpitchedSlide == b.unpitchedSlide
				&& a.accent == b.accent
				&& a.ignore == b.ignore
				&& a.passOtherNotes == b.passOtherNotes
				&& bendValuesAreEqual(a.bendValues, b.bendValues, a.position(), b.position());
	}

	private boolean chordNotesAreEqual(final Chord chordA, final ChordNote cnA,
			final Chord chordB, final ChordNote cnB) {
		if (!relativeDuration(chordA.position(), cnA.endPosition())
				.equals(relativeDuration(chordB.position(), cnB.endPosition()))) {
			return false;
		}
		return cnA.mute == cnB.mute
				&& cnA.hopo == cnB.hopo
				&& cnA.harmonic == cnB.harmonic
				&& cnA.vibrato == cnB.vibrato
				&& cnA.tremolo == cnB.tremolo
				&& cnA.linkNext == cnB.linkNext
				&& Objects.equals(cnA.slideTo, cnB.slideTo)
				&& cnA.unpitchedSlide == cnB.unpitchedSlide
				&& bendValuesAreEqual(cnA.bendValues, cnB.bendValues, chordA.position(), chordB.position());
	}

	private FractionalPosition findHandShapeDuration(final FractionalPosition chordPosition,
			final int templateId, final List<HandShape> handShapes) {
		for (final HandShape hs : handShapes) {
			if (hs.templateId != null && hs.templateId == templateId
					&& hs.position().equals(chordPosition)) {
				return relativeDuration(hs.position(), hs.endPosition());
			}
		}
		return null;
	}

	private boolean chordsAreEqual(final Chord a, final Chord b,
			final List<ChordTemplate> chordTemplates, final List<HandShape> handShapes) {
		final ChordTemplate tA = chordTemplates.get(a.templateId());
		final ChordTemplate tB = chordTemplates.get(b.templateId());
		if (!tA.equals(tB)) {
			return false;
		}
		if (a.splitIntoNotes != b.splitIntoNotes
				|| a.forceNoNotes != b.forceNoNotes
				|| a.accent != b.accent
				|| a.ignore != b.ignore
				|| a.passOtherNotes != b.passOtherNotes) {
			return false;
		}
		if (!a.chordNotes.keySet().equals(b.chordNotes.keySet())) {
			return false;
		}
		for (final int string : a.chordNotes.keySet()) {
			if (!chordNotesAreEqual(a, a.chordNotes.get(string), b, b.chordNotes.get(string))) {
				return false;
			}
		}
		final FractionalPosition hsA = findHandShapeDuration(a.position(), a.templateId(), handShapes);
		final FractionalPosition hsB = findHandShapeDuration(b.position(), b.templateId(), handShapes);
		return Objects.equals(hsA, hsB);
	}

	private boolean handShapesAreEqual(final HandShape a, final HandShape b,
			final List<ChordTemplate> chordTemplates) {
		if (!Objects.equals(a.templateId, b.templateId)) {
			final ChordTemplate tA = a.templateId != null ? chordTemplates.get(a.templateId) : null;
			final ChordTemplate tB = b.templateId != null ? chordTemplates.get(b.templateId) : null;
			if (!Objects.equals(tA, tB)) {
				return false;
			}
		}
		return relativeDuration(a.position(), a.endPosition())
				.equals(relativeDuration(b.position(), b.endPosition()));
	}

	private void selectAllEqualHandShapes() {
		final List<Integer> selectedIds = getSelectedIds(PositionType.HAND_SHAPE);
		if (selectedIds.isEmpty()) {
			return;
		}

		final List<HandShape> handShapes = chartData.currentHandShapes();
		final List<ChordTemplate> chordTemplates = chartData.currentChordTemplates();
		final int selectedId = selectedIds.get(0);
		if (selectedId >= handShapes.size()) {
			return;
		}

		final HandShape reference = handShapes.get(selectedId);
		final List<Integer> equalIds = new ArrayList<>();
		for (int i = 0; i < handShapes.size(); i++) {
			if (handShapesAreEqual(reference, handShapes.get(i), chordTemplates)) {
				equalIds.add(i);
			}
		}

		clearSelectionsExcept(PositionType.NONE);
		selectionLists.get(PositionType.HAND_SHAPE).add(equalIds);
		currentSelectionEditor.selectionChanged(true);
	}

	public void selectAllEqual() {
		if (modeManager.getMode() != EditMode.GUITAR) {
			return;
		}

		final List<Integer> selectedIds = getSelectedIds(PositionType.GUITAR_NOTE);
		if (selectedIds.isEmpty()) {
			selectAllEqualHandShapes();
			return;
		}

		final List<ChordOrNote> sounds = chartData.currentSounds();
		final List<ChordTemplate> chordTemplates = chartData.currentChordTemplates();
		final List<HandShape> handShapes = chartData.currentHandShapes();

		if (selectedIds.size() == 1) {
			final int selectedId = selectedIds.get(0);
			if (selectedId >= sounds.size()) {
				return;
			}

			final ChordOrNote selected = sounds.get(selectedId);
			final List<Integer> equalIds = new ArrayList<>();
			for (int i = 0; i < sounds.size(); i++) {
				final ChordOrNote sound = sounds.get(i);
				if (selected.isNote() && sound.isNote()) {
					if (notesAreEqual(selected.note(), sound.note())) {
						equalIds.add(i);
					}
				} else if (selected.isChord() && sound.isChord()) {
					if (chordsAreEqual(selected.chord(), sound.chord(), chordTemplates, handShapes)) {
						equalIds.add(i);
					}
				}
			}

			clearSelectionsExcept(PositionType.NONE);
			selectionLists.get(PositionType.GUITAR_NOTE).add(equalIds);
			currentSelectionEditor.selectionChanged(true);
			return;
		}

		// Multiple sounds selected: require them to be a contiguous block (consecutive
		// indices) and find every other contiguous block of the same length where each
		// element is pairwise equal.
		final int firstId = selectedIds.get(0);
		for (int j = 0; j < selectedIds.size(); j++) {
			if (selectedIds.get(j) != firstId + j) {
				return;
			}
		}

		final List<ChordOrNote> referenceChain = new ArrayList<>(selectedIds.size());
		for (final int id : selectedIds) {
			if (id >= sounds.size()) {
				return;
			}
			referenceChain.add(sounds.get(id));
		}

		final int chainLength = referenceChain.size();
		final List<Integer> equalIds = new ArrayList<>();

		for (int startId = 0; startId <= sounds.size() - chainLength; startId++) {
			boolean chainValid = true;

			for (int j = 0; j < chainLength; j++) {
				final ChordOrNote candidate = sounds.get(startId + j);
				final ChordOrNote ref = referenceChain.get(j);

				if (ref.isNote() && candidate.isNote()) {
					if (!notesAreEqual(ref.note(), candidate.note())) {
						chainValid = false;
						break;
					}
				} else if (ref.isChord() && candidate.isChord()) {
					if (!chordsAreEqual(ref.chord(), candidate.chord(), chordTemplates, handShapes)) {
						chainValid = false;
						break;
					}
				} else {
					chainValid = false;
					break;
				}
			}

			if (chainValid) {
				for (int j = 0; j < chainLength; j++) {
					equalIds.add(startId + j);
				}
			}
		}

		clearSelectionsExcept(PositionType.NONE);
		selectionLists.get(PositionType.GUITAR_NOTE).add(equalIds);
		currentSelectionEditor.selectionChanged(true);
	}

	public void selectAll() {
		switch (modeManager.getMode()) {
			case VOCALS -> {
				selectionLists.get(PositionType.VOCAL).addAll();
			}
			case GUITAR -> {
				selectionLists.get(PositionType.GUITAR_NOTE).addAll();
				selectionLists.get(PositionType.HAND_SHAPE).addAll();
				selectionLists.get(PositionType.FHP).addAll();
				selectionLists.get(PositionType.EVENT_POINT).addAll();
				selectionLists.get(PositionType.TONE_CHANGE).addAll();
			}
			default -> {
			}
		}

		currentSelectionEditor.selectionChanged(true);
	}

	public void addSelection(final PositionType type, final int id) {
		selectionLists.get(type).add(id);
		currentSelectionEditor.selectionChanged(true);
	}

	public void addSoundSelection(final int id) {
		addSelection(PositionType.GUITAR_NOTE, id);
	}

	public void addSoundSelection(final List<Integer> ids) {
		selectionLists.get(PositionType.GUITAR_NOTE).add(ids);
		currentSelectionEditor.selectionChanged(true);
	}

	public void setSelection(final PositionType type, final int id) {
		clearSelectionsExcept(PositionType.NONE);
		addSelection(type, id);
	}

	@SuppressWarnings("unchecked")
	public <C extends IVirtualConstantPosition> void addSelectionForPositions(final PositionType type,
			final Collection<C> positions) {
		((SelectionList<C, ?, ?>) selectionLists.get(type)).addPositions(positions);
		currentSelectionEditor.selectionChanged(type == PositionType.GUITAR_NOTE);
	}
}
