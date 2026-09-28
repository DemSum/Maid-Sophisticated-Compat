package io.github.demsum.maidsophicompat;

import com.github.tartaricacid.touhoulittlemaid.init.InitCreativeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MaidSophiCompat.MOD_ID);

    public static final DeferredItem<WirelessIoExpBauble> WIRELESS_IO_EXP =
            ITEMS.registerItem(
                    "wireless_io_exp",
                    WirelessIoExpBauble::new,
                    new Item.Properties().stacksTo(1)
            );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == InitCreativeTabs.MAIN_TAB.get()) {
            event.accept(WIRELESS_IO_EXP.get());
        }
    }
}
