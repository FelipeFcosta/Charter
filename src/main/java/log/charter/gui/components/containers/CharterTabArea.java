package log.charter.gui.components.containers;

import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JLayeredPane;

import log.charter.gui.components.simple.ChartingTimerPanel;

public class CharterTabArea extends JLayeredPane {
	private static final long serialVersionUID = 1L;

	private final CharterTabbedPane tabs;
	private final ChartingTimerPanel timerPanel;

	public CharterTabArea(final CharterTabbedPane tabs, final ChartingTimerPanel timerPanel) {
		this.tabs = tabs;
		this.timerPanel = timerPanel;

		add(tabs, JLayeredPane.DEFAULT_LAYER);
		add(timerPanel, JLayeredPane.PALETTE_LAYER);

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(final ComponentEvent e) {
				layoutLayers();
			}
		});
	}

	private void layoutLayers() {
		final int w = getWidth();
		final int h = getHeight();
		tabs.setBounds(0, 0, w, h);

		final Dimension pd = timerPanel.getPreferredSize();
		final int ty = 4;
		// Full width so centered timer row is never clipped at the right (was min(pref,w) → "…" on auto).
		timerPanel.setBounds(0, ty, w, pd.height);
	}

	public CharterTabbedPane tabs() {
		return tabs;
	}

	@Override
	public void doLayout() {
		super.doLayout();
		layoutLayers();
	}

	@Override
	public void setBounds(final int x, final int y, final int width, final int height) {
		super.setBounds(x, y, width, height);
		layoutLayers();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		layoutLayers();
	}
}
