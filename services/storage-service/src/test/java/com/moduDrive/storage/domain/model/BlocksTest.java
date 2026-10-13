package com.moduDrive.storage.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BlocksTest {

    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void acceptsOnlyLowercaseHexSha256() {
        assertThat(Blocks.isHash(ABC_SHA256)).isTrue();
        assertThat(Blocks.isHash(ABC_SHA256.toUpperCase())).isFalse();
        assertThat(Blocks.isHash(ABC_SHA256.substring(1))).isFalse();
        assertThat(Blocks.isHash("../" + ABC_SHA256.substring(3))).isFalse();
        assertThat(Blocks.isHash(null)).isFalse();
    }

    @Test
    void encodesTheHashTheWayS3ReportsIt() {
        // echo -n abc | openssl dgst -sha256 -binary | base64
        assertThat(Blocks.sha256Base64(ABC_SHA256)).isEqualTo("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=");
    }

    @Test
    void keysABlockByOwnerAndHash() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(Blocks.key(owner, "ab")).isEqualTo("blocks/00000000-0000-0000-0000-000000000001/ab");
    }
}
