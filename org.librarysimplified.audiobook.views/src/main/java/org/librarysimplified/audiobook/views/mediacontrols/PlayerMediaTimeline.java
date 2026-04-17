package org.librarysimplified.audiobook.views.mediacontrols;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerMediaTimeline
  extends Timeline {

  private static final Logger LOG =
    LoggerFactory.getLogger(PlayerMediaTimeline.class);

  private MediaItem mediaItem;
  private Window window;

  PlayerMediaTimeline() {
    this.window = new Window();
    this.window.isSeekable = true;
    this.window.mediaItem = this.mediaItem;
  }

  @Override
  public int getWindowCount() {
    return 1;
  }

  @Override
  public Window getWindow(
    int windowIndex,
    Window newWindow,
    long defaultPositionProjectionUs) {
    return this.window;
  }

  @Override
  public int getPeriodCount() {
    LOG.debug("getPeriodCount");
    return 0;
  }

  @Override
  public Period getPeriod(
    int periodIndex,
    Period period,
    boolean setIds) {
    LOG.debug("getPeriod {} {} {}", periodIndex, period, setIds);
    return null;
  }

  @Override
  public int getIndexOfPeriod(
    Object uid) {
    LOG.debug("getIndexOfPeriod {}", uid);
    return 0;
  }

  @Override
  public Object getUidOfPeriod(
    int periodIndex) {
    LOG.debug("getUidOfPeriod {}", periodIndex);
    return null;
  }

  public void setMetadataItem(
    final MediaItem mediaItem,
    final long durationMs) {
    this.mediaItem = mediaItem;
    this.window.durationUs = durationMs * 1000L;
    this.window.mediaItem = this.mediaItem;
  }
}
