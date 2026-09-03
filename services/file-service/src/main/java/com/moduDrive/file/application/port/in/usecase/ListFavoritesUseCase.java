package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListFavoritesCommand;

import java.util.List;

public interface ListFavoritesUseCase {

    List<FileView> listFavorites(ListFavoritesCommand command);
}
