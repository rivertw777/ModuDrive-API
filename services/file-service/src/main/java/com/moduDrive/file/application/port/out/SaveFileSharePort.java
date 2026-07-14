package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileShare;

public interface SaveFileSharePort {

    FileShare saveFileShare(FileShare fileShare);
}
