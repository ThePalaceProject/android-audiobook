package org.librarysimplified.audiobook.persistence;

import org.librarysimplified.audiobook.api.PlayerBookID;
import org.librarysimplified.audiobook.api.PlayerPosition;

import java.util.Optional;

public interface ADatabaseType extends AutoCloseable {

  Optional<PlayerPosition> lastReadPositionGet(
    PlayerBookID bookID);

  void lastReadPositionSave(
    PlayerBookID bookID,
    PlayerPosition position);

}
