package org.librarysimplified.audiobook.views.mediacontrols;

import androidx.media3.common.Timeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerMediaTimeline extends Timeline {

  private static final Logger LOG =
    LoggerFactory.getLogger(PlayerMediaTimeline.class);

  PlayerMediaTimeline() {

  }

  @Override
  public int getWindowCount() {
    LOG.debug("getWindowCount");
    return 0;
  }

  @Override
  public Window getWindow(
    int windowIndex,
    Window window,
    long defaultPositionProjectionUs) {
    LOG.debug("getWindow {} {} {}", windowIndex, window, defaultPositionProjectionUs);
    return null;
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
}
