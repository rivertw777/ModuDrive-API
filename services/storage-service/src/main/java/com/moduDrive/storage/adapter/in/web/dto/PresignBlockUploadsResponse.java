package com.moduDrive.storage.adapter.in.web.dto;

import java.util.List;

/** One PUT URL per requested block, in the request's order. */
public record PresignBlockUploadsResponse(List<String> urls) {}
