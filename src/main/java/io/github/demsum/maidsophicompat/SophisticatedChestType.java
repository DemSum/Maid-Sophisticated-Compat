package io.github.demsum.maidsophicompat;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class SophisticatedChestType implements IChestType {
    private static final String STORAGE_PACKAGE = "net.p3pp3rf1y.sophisticatedstorage.";
    private static final String BACKPACKS_PACKAGE = "net.p3pp3rf1y.sophisticatedbackpacks.";

    @Override
    public boolean isChest(BlockEntity chest) {
        if (!isSophisticatedBlockEntity(chest)) {
            return false;
        }

        Level level = chest.getLevel();
        if (level == null) {
            return false;
        }

        return level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                chest.getBlockPos(),
                chest.getBlockState(),
                chest,
                null
        ) != null;
    }

    @Override
    public boolean canOpenByPlayer(BlockEntity chest, Player player) {
        return isChest(chest);
    }

    @Override
    public int getOpenCount(BlockGetter level, BlockPos pos, BlockEntity chest) {
        return isChest(chest) ? ALLOW_COUNT : DENY_COUNT;
    }

    private static boolean isSophisticatedBlockEntity(BlockEntity chest) {
        String className = chest.getClass().getName();
        return className.startsWith(STORAGE_PACKAGE) || className.startsWith(BACKPACKS_PACKAGE);
    }
}