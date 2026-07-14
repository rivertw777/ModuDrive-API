package com.moduDrive.storage.application.port.out;

import java.util.List;

public interface StoreBlocksPort {

    int storeBlocks(String s3BasePath, List<byte[]> rawBlocks);
}
