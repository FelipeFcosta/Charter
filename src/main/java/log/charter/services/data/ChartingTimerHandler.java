package log.charter.services.data;

import log.charter.services.editModes.EditMode;
import log.charter.services.editModes.ModeManager;

public class ChartingTimerHandler {
	private static long nanosToMs(final long nanos) {
		return nanos / 1_000_000L;
	}

	private ModeManager modeManager;

	private long accumulatedMs;
	private boolean running;
	private long runStartNanos;
	private boolean syncWithAudio;
	private boolean pausedByMouseIdle;

	public void loadFromProject(final long savedMs, final boolean syncWithAudio) {
		accumulatedMs = Math.max(0, savedMs);
		running = false;
		this.syncWithAudio = syncWithAudio;
	}

	public void reset() {
		accumulatedMs = 0;
		running = false;
		syncWithAudio = false;
		pausedByMouseIdle = false;
	}

	public void setSyncWithAudio(final boolean syncWithAudio) {
		this.syncWithAudio = syncWithAudio;
	}

	public boolean isSyncWithAudio() {
		return syncWithAudio;
	}

	/**
	 * When auto mode ({@link #syncWithAudio}) is on, runs the timer only while the Charter window is the active
	 * window (same idea as foreground focus — alt-tab away pauses).
	 */
	public void applyAutoSyncFromWindowFocus(final boolean charterWindowIsActive) {
		if (!syncWithAudio) {
			return;
		}
		if (!isEnabled()) {
			pause();
			return;
		}
		if (charterWindowIsActive) {
			play();
		} else {
			pause();
		}
	}

	public void play() {
		if (pausedByMouseIdle || modeManager == null || modeManager.getMode() == EditMode.EMPTY || running) {
			return;
		}
		running = true;
		runStartNanos = System.nanoTime();
	}

	public void pause() {
		if (!running) {
			return;
		}
		accumulatedMs += nanosToMs(System.nanoTime() - runStartNanos);
		running = false;
	}

	/**
	 * Fold any running segment into accumulated time and stop (e.g. on app exit).
	 */
	public void pauseForExit() {
		pause();
	}

	public void stop() {
		pause();
		accumulatedMs = 0;
		pausedByMouseIdle = false;
	}

	/**
	 * Pauses the timer once mouse inactivity timeout is reached.
	 */
	public void applyMouseInactivityTimeout(final boolean inactiveForTooLong) {
		if (!inactiveForTooLong || !running || pausedByMouseIdle) {
			return;
		}

		pause();
		pausedByMouseIdle = true;
	}

	/**
	 * Resumes the timer if it was paused only due to inactivity.
	 */
	public void onMouseMovedAfterInactivityPause() {
		if (!pausedByMouseIdle) {
			return;
		}

		pausedByMouseIdle = false;
		play();
	}

	public boolean isRunning() {
		return running;
	}

	public long currentTotalMs() {
		if (!running) {
			return accumulatedMs;
		}
		return accumulatedMs + nanosToMs(System.nanoTime() - runStartNanos);
	}

	public long getMsToSerialize() {
		return currentTotalMs();
	}

	public boolean isEnabled() {
		return modeManager != null && modeManager.getMode() != EditMode.EMPTY;
	}
}
