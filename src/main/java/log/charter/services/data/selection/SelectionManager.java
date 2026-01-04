package log.charter.services.data.selection;

import static log.charter.data.config.Config.selectNotesByTails;
import static log.charter.gui.chartPanelDrawers.common.DrawerUtils.yToString;
import static log.charter.util.CollectionUtils.closest;
import static log.charter.util.ScalingUtils.xToPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import log.charter.data.ChartData;
import log.charter.data.song.BeatsMap.ImmutableBeatsMap;
import log.charter.data.song.notes.ChordOrNote;
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

	public PositionWithIdAndType findExistingPosition(final int x, final int y) {
		final PositionType positionType = PositionType.fromY(y, modeManager.getMode());
		final List<PositionWithIdAndType> positions = positionType.getPositionsWithIdsAndTypes(chartData);

		if (positionType == PositionType.VOCAL//
				|| positionType == PositionType.HAND_SHAPE//
				|| (positionType == PositionType.GUITAR_NOTE && selectNotesByTails)) {
			return findWithLengthExisting(x, generateLinksWithLength(positions));
		}

		return findExisting(x, generateLinks(positions));
	}

	public void click(final MouseButtonPressReleaseData clickData, final boolean ctrl, final boolean shift) {
		if (chartData.isEmpty) {
			return;
		}

		if (!clickData.pressHighlight.existingPosition) {
			if (!ctrl) {
				clearSelectionsExcept(PositionType.NONE);
			}
			selectedChordNoteString = null;

			currentSelectionEditor.selectionChanged(true);
			return;
		}

		// Check if clicking on an already-selected chord to select a specific note
		if (clickData.pressHighlight.type == PositionType.GUITAR_NOTE && !ctrl && !shift) {
			final Set<Integer> selectedIds = accessor(PositionType.GUITAR_NOTE).getSelectedIdsSet(PositionType.GUITAR_NOTE);
			if (selectedIds.contains(clickData.pressHighlight.id) && clickData.pressHighlight.chordOrNote != null
					&& clickData.pressHighlight.chordOrNote.isChord()) {
				// Clicking on an already-selected chord - select specific string
				final int clickedString = yToString(clickData.pressPosition.y, chartData.currentStrings());
				// Verify the chord has a note on this string
				if (clickData.pressHighlight.chordOrNote.chord().chordNotes.containsKey(clickedString)) {
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

		clearSelectionsExcept(clickData.pressHighlight.type);

		final SelectionList<?, ?, ?> selectionList = selectionLists.get(clickData.pressHighlight.type);
		if (selectionList == null) {
			currentSelectionEditor.selectionChanged(true);
			return;
		}

		selectionList.addSelectablesWithModifiers(clickData.pressHighlight.id, ctrl, shift);
		currentSelectionEditor.selectionChanged(true);
	}

	public void clear() {
		clearSelectionsExcept(PositionType.NONE);
		selectedChordNoteString = null;
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

	@SuppressWarnings("unchecked")
	public <C extends IVirtualConstantPosition> void addSelectionForPositions(final PositionType type,
			final Collection<C> positions) {
		((SelectionList<C, ?, ?>) selectionLists.get(type)).addPositions(positions);
		currentSelectionEditor.selectionChanged(type == PositionType.GUITAR_NOTE);
	}
}
