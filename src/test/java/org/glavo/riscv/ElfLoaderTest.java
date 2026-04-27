package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests ELF loading and validation behavior.
@NotNullByDefault
public final class ElfLoaderTest {
    /// Verifies that a minimal executable ELF is accepted.
    @Test
    public void loadsMinimalExecutable() {
        ElfImage image = ElfLoader.load(ElfTestImages.executable(ElfTestImages.ecall()));

        assertEquals(ElfTestImages.BASE_ADDRESS, image.entryPoint());
        assertEquals(1, image.loadSegments().size());
        assertEquals(ElfTestImages.BASE_ADDRESS, image.loadSegments().getFirst().virtualAddress());
        assertEquals(Integer.BYTES, image.loadSegments().getFirst().contents().length);
    }

    /// Verifies that non-ELF input is rejected.
    @Test
    public void rejectsInvalidElfMagic() {
        assertThrows(RiscVException.class, () -> ElfLoader.load(new byte[]{0, 1, 2, 3}));
    }
}
