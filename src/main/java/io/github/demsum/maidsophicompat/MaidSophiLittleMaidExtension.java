package io.github.demsum.maidsophicompat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;

@LittleMaidExtension
public final class MaidSophiLittleMaidExtension implements ILittleMaid {
    @Override
    public void addChestType(ChestManager manager) {
        MaidSophiCompat.LOGGER.info("Registering Maid Sophi Compat chest types");
        manager.add(new SophisticatedChestType());
    }
}