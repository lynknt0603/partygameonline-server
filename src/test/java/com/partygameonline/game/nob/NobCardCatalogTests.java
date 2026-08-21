package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.nob.catalog.NobCardCatalog;
import com.partygameonline.game.nob.catalog.NobCardDef;
import com.partygameonline.game.nob.domain.NobEffectCode;
import com.partygameonline.game.nob.domain.NobRoleType;
import org.junit.jupiter.api.Test;

class NobCardCatalogTests {

    @Test
    void hasExactlyThirtyThreeStableCodes() {
        assertThat(NobCardCatalog.all()).hasSize(33);
        assertThat(NobCardCatalog.all()).extracting(NobCardDef::cardCode).doesNotHaveDuplicates();
        assertThat(NobCardCatalog.find("NOB-SS-01")).isPresent();
        assertThat(NobCardCatalog.find("NOB-BS-06")).isPresent();
        assertThat(NobCardCatalog.find("NOB-SH-02")).get().extracting(NobCardDef::effectCode)
                .isEqualTo(NobEffectCode.ECHOES_OF_FALLEN);
        assertThat(NobCardCatalog.require("NOB-SP-VEIL-REVERSAL").roleType()).isEqualTo(NobRoleType.SPECIAL);
        assertThat(NobCardCatalog.require("NOB-SP-LAST-OFFERING").effectCode()).isEqualTo(NobEffectCode.LAST_OFFERING);
        assertThat(NobCardCatalog.require("NOB-SP-LAST-HOPE").effectCode()).isEqualTo(NobEffectCode.LAST_HOPE);
        assertThat(NobCardCatalog.find("UNKNOWN")).isEmpty();
    }
}
