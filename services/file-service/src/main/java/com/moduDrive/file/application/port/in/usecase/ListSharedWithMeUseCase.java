package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListSharedWithMeCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface ListSharedWithMeUseCase {

    List<File> listSharedWithMe(ListSharedWithMeCommand command);
}
