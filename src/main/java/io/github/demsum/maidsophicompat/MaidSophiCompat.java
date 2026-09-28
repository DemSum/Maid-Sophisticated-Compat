package io.github.demsum.maidsophicompat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(MaidSophiCompat.MOD_ID)
public final class MaidSophiCompat {
    public static final String MOD_ID = "maid_sophi_compat";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<WirelessIoExpBauble.WirelessIoExpMenu>>
            WIRELESS_IO_EXP_MENU = MENUS.register(
                    "wireless_io_exp",
                    () -> IMenuTypeExtension.create(WirelessIoExpBauble.WirelessIoExpMenu::new)
            );

    public MaidSophiCompat(IEventBus modEventBus) {
        ModItems.register(modEventBus);
        MENUS.register(modEventBus);
        LOGGER.info("Loaded Maid Sophi Compat");
    }
}
