package com.moduDrive.storage.application.port.out;

import java.io.OutputStream;
import java.util.List;

public interface RetrieveBlocksPort {

    /** The blocks at {@code keys}, in order. */
    List<byte[]> retrieveBlocks(List<String> keys);

    /** Same blocks as {@link #retrieveBlocks}, written out one at a time instead of fully
     * materialized in a returned List — bounds memory to a single (decrypted, decompressed)
     * block regardless of file size. */
    void streamBlocks(List<String> keys, OutputStream out);
}
