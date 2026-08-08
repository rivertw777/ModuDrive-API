package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.UpdateFileFavoriteCommand;
import com.moduDrive.file.domain.model.File;

public interface UpdateFileFavoriteUseCase {

    File updateFavorite(UpdateFileFavoriteCommand command);
}
