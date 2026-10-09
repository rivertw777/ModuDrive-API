package com.moduDrive.storage.domain.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BlocksTest {

    @Test
    void hashesToLowercaseHexSha256() {
        assertThat(Blocks.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void keysABlockByOwnerAndHash() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(Blocks.key(owner, "ab")).isEqualTo("blocks/00000000-0000-0000-0000-000000000001/ab");
    }
}
