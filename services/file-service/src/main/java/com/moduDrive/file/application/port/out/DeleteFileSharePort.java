package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileShare.FileShareId;

public interface DeleteFileSharePort {

    void deleteFileShare(FileShareId shareId);
}
