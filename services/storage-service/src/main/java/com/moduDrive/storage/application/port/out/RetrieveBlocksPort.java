package com.moduDrive.storage.application.port.out;

import java.util.List;

public interface RetrieveBlocksPort {

    List<byte[]> retrieveBlocks(String s3BasePath, int blockCount);
}
