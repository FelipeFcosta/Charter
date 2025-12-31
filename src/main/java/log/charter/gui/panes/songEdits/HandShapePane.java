package log.charter.gui.panes.songEdits;

import javax.swing.JButton;
import javax.swing.JCheckBox;

import log.charter.data.ChartData;
import log.charter.data.ChordLibrary;
import log.charter.data.config.Localization.Label;
import log.charter.data.song.ChordTemplate;
import log.charter.data.song.HandShape;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.containers.RowedDialog;
import log.charter.gui.components.containers.SaverWithStatus;
import log.charter.gui.components.simple.FieldWithLabel;
import log.charter.gui.components.simple.FieldWithLabel.LabelPosition;
import log.charter.gui.components.tabs.chordEditor.ChordTemplatesEditorTab;
import log.charter.gui.components.tabs.selectionEditor.chords.ChordLibrarySidebar;
import log.charter.gui.components.tabs.selectionEditor.chords.ChordTemplateEditor;
import log.charter.gui.components.utils.RowedPosition;

public class HandShapePane extends RowedDialog {
	private static final long serialVersionUID = -4754359602173894487L;

	private static ChordTemplate prepareTemplateFromData(final ChartData data, final HandShape handShape) {
		return handShape.templateId == null ? new ChordTemplate()
				: new ChordTemplate(data.currentArrangement().chordTemplates.get(handShape.templateId));
	}

	private final ChartData data;
	private final ChordTemplatesEditorTab chordTemplatesEditorTab;

	private final HandShape handShape;
	private final ChordTemplate template;

	private ChordTemplateEditor editor;
	private JCheckBox arpeggioCheckBox;
	private JCheckBox forceArpeggioInRSCheckbox;
	private ChordLibrarySidebar chordLibrarySidebar;
	private JButton addToLibraryButton;

	public HandShapePane(final ChartData data, final CharterFrame frame,
			final ChordTemplatesEditorTab chordTemplatesEditorTab, final HandShape handShape, final Runnable onCancel) {
		super(frame, Label.HAND_SHAPE_PANE, 800);

		this.data = data;
		this.chordTemplatesEditorTab = chordTemplatesEditorTab;

		this.handShape = handShape;
		template = prepareTemplateFromData(data, handShape);

		initChordTemplateEditor();
		initChordLibrarySidebar();

		final RowedPosition position = new RowedPosition(20, panel.sizes);
		editor.addChordNameSuggestionButton(position.addX(80).x(), position.row());

		position.newRow();
		editor.addChordNameInput(position.x(), position.row());

		position.newRow();
		addArpeggio(position);
		addForceArpeggioInRS(position);
		addAddToLibraryButton(position);

		position.newRows(2);
		addEditor(position);

		position.newRows(2 + data.currentArrangement().tuning.strings());
		addDefaultFinish(position.y(), SaverWithStatus.defaultFor(this::saveAndExit),
				SaverWithStatus.defaultFor(onCancel), false);

		// Add sidebar to the right side of the dialog
		addSidebarToDialog();

		finishInit();
	}

	private void onChordTemplateChange() {
		arpeggioCheckBox.setSelected(template.arpeggio);
		updateLibrarySidebar();
	}

	private void updateLibrarySidebar() {
		if (chordLibrarySidebar != null) {
			chordLibrarySidebar.updateChordList(template.frets);
		}
	}

	private void initChordTemplateEditor() {
		editor = new ChordTemplateEditor(panel);
		editor.init(data, frame, null, () -> template, this::onChordTemplateChange);
	}

	private void initChordLibrarySidebar() {
		chordLibrarySidebar = new ChordLibrarySidebar(this::onChordSelectedFromLibrary);
		chordLibrarySidebar.setStrings(data.currentArrangement().tuning.strings());
		chordLibrarySidebar.updateChordList(template.frets);
	}

	private void addSidebarToDialog() {
		// Add sidebar directly to the panel on the right side
		final int sidebarX = 590;
		final int sidebarY = 10;
		final int sidebarWidth = 190;
		final int sidebarHeight = 280;
		
		chordLibrarySidebar.setBounds(sidebarX, sidebarY, sidebarWidth, sidebarHeight);
		panel.add(chordLibrarySidebar);
	}

	private void onChordSelectedFromLibrary(final ChordTemplate libraryChord) {
		// Apply the library chord to the current template
		template.chordName = libraryChord.chordName;
		template.arpeggio = libraryChord.arpeggio;
		template.frets.clear();
		template.frets.putAll(libraryChord.frets);
		template.fingers.clear();
		template.fingers.putAll(libraryChord.fingers);

		// Update UI
		editor.setCurrentValuesInInputs();
		arpeggioCheckBox.setSelected(template.arpeggio);
		forceArpeggioInRSCheckbox.setEnabled(template.arpeggio);
	}

	private void onArpeggioChanged(final boolean newArpeggio) {
		template.arpeggio = newArpeggio;

		if (!newArpeggio) {
			template.forceArpeggioInRS = false;
			forceArpeggioInRSCheckbox.setSelected(false);
		}

		forceArpeggioInRSCheckbox.setEnabled(newArpeggio);
	}

	private void addArpeggio(final RowedPosition position) {
		arpeggioCheckBox = new JCheckBox();
		arpeggioCheckBox.setSelected(template.arpeggio);
		arpeggioCheckBox.addActionListener(e -> onArpeggioChanged(arpeggioCheckBox.isSelected()));

		final FieldWithLabel<JCheckBox> field = new FieldWithLabel<>(Label.ARPEGGIO, 70, 20, 20, arpeggioCheckBox,
				LabelPosition.LEFT);

		panel.add(field, position);
	}

	private void onForceArpeggioInRSChanged(final boolean newValue) {
		template.forceArpeggioInRS = newValue;
	}

	private void addForceArpeggioInRS(final RowedPosition position) {
		forceArpeggioInRSCheckbox = new JCheckBox();
		forceArpeggioInRSCheckbox.setSelected(template.forceArpeggioInRS);
		forceArpeggioInRSCheckbox.setEnabled(template.arpeggio);
		forceArpeggioInRSCheckbox
				.addActionListener(e -> onForceArpeggioInRSChanged(forceArpeggioInRSCheckbox.isSelected()));

		final FieldWithLabel<JCheckBox> field = new FieldWithLabel<>(Label.FORCE_ARPEGGIO_IN_RS, 30, 20, 20,
				forceArpeggioInRSCheckbox, LabelPosition.LEFT_CLOSE);

		panel.add(field, position);
	}

	private void addAddToLibraryButton(final RowedPosition position) {
		addToLibraryButton = new JButton(Label.CHORD_LIBRARY_ADD.label());
		addToLibraryButton.addActionListener(e -> onAddToLibrary());
		panel.addWithSettingSize(addToLibraryButton, position.x() + 180, panel.sizes.getY(position.row()), 120, 20);
	}

	private void onAddToLibrary() {
		if (template.frets.isEmpty()) {
			return;
		}

		final ChordLibrary library = ChordLibrary.getInstance();
		final boolean added = library.addChord(template);

		if (added) {
			chordLibrarySidebar.updateChordList(template.frets);
		}
	}

	private void addEditor(final RowedPosition position) {
		editor.addChordTemplateEditor(position.x(), position.row());
		editor.showFields();
		editor.setCurrentValuesInInputs();
	}

	private void saveAndExit() {
		handShape.templateId = data.currentArrangement().getChordTemplateIdWithSave(template);
		chordTemplatesEditorTab.refreshTemplates();
	}
}
