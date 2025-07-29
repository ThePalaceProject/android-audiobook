package org.librarysimplified.audiobook.persistence;

import java.io.IOException;
import java.nio.file.Path;

public interface ADatabaseFactoryType {

  ADatabaseType open(
    Path file)
    throws IOException;

}
