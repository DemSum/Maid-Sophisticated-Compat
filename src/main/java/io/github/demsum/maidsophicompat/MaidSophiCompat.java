package io.github.demsum.maidsophicompat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MaidSophiCompat.MOD_ID)
public final class MaidSophiCompat {
    public static final String MOD_ID = "maid_sophi_compat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidSophiCompat() {
        LOGGER.info("Loaded Maid Sophi Compat");
    }
}