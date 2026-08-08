package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListFavoritesCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface ListFavoritesUseCase {

    List<File> listFavorites(ListFavoritesCommand command);
}
