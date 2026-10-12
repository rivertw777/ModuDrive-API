package com.moduDrive.storage.application.port.out;

import java.io.OutputStream;
import java.util.List;

public interface RetrieveBlocksPort {

    /** The blocks at {@code keys}, in order. */
    List<byte[]> retrieveBlocks(List<String> keys);

    /** Writes the blocks to {@code out} one at a time, so memory holds one block regardless of file size. */
    void streamBlocks(List<String> keys, OutputStream out);
}
