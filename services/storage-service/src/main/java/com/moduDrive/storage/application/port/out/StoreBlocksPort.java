package com.moduDrive.storage.application.port.out;

public interface StoreBlocksPort {

    /** Writes the block at {@code key}, overwriting it if it's already there — a block's key is
     * its hash, so an overwrite stores the same bytes. */
    void storeBlock(String key, byte[] rawBlock);
}
