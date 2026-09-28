package io.github.demsum.maidsophicompat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;

@LittleMaidExtension
public final class MaidSophiLittleMaidExtension implements ILittleMaid {
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ModItems.WIRELESS_IO_EXP.get(), ModItems.WIRELESS_IO_EXP.get());
    }
    @Override
    public void addChestType(ChestManager manager) {
        MaidSophiCompat.LOGGER.info("Registering Maid Sophi Compat chest types");
        manager.add(new SophisticatedChestType());
    }
}
