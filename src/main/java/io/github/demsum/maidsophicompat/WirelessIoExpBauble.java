package io.github.demsum.maidsophicompat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.WirelessIOButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public final class WirelessIoExpBauble extends Item implements IMaidBauble, MenuProvider {
    private static final int TRANSFER_INTERVAL = 100;
    private static final int MAX_VANILLA_LEVEL_WITH_INT_EXPERIENCE = 21863;
    private static final int CEI_MILLIBUCKETS_PER_EXPERIENCE = 1;
    private static final int SOPHISTICATED_MILLIBUCKETS_PER_EXPERIENCE = 20;
    private static final int TOGGLE_MODE_BUTTON = 0;
    private static final int RETAIN_LEVEL_BUTTON_OFFSET = 1;
    private static final ResourceLocation CEI_EXPERIENCE = ResourceLocation.fromNamespaceAndPath(
            "create_enchantment_industry",
            "experience"
    );
    private static final ResourceLocation CEI_EXPERIENCE_HATCH = ResourceLocation.fromNamespaceAndPath(
            "create_enchantment_industry",
            "experience_hatch"
    );
    private static final ResourceLocation SOPHISTICATED_EXPERIENCE = ResourceLocation.fromNamespaceAndPath(
            "sophisticatedcore",
            "xp_still"
    );
    private static final String TARGET_DIMENSION_TAG = "TargetDimension";
    private static final String TARGET_SIDE_TAG = "TargetSide";
    private static final String EXPERIENCE_FLUID_TAG = "ExperienceFluid";
    private static final String RETAIN_LEVEL_TAG = "RetainLevel";

    public WirelessIoExpBauble(Properties properties) {
        super(properties);
    }

    static boolean isMaidToContainer(ItemStack stack) {
        return stack.getOrDefault(InitDataComponent.IO_MODE.get(), false);
    }

    static void setMaidToContainer(ItemStack stack, boolean maidToContainer) {
        stack.set(InitDataComponent.IO_MODE.get(), maidToContainer);
    }

    static int getRetainLevel(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return Math.max(0, data.copyTag().getInt(RETAIN_LEVEL_TAG));
    }

    static void setRetainLevel(ItemStack stack, int retainLevel) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putInt(RETAIN_LEVEL_TAG, Math.max(0, retainLevel))
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND || !context.isSecondaryUseActive()) {
            return super.useOn(context);
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockPos capabilityPos = getCapabilityPos(pos, state);
        if (!level.hasChunkAt(capabilityPos)) {
            return super.useOn(context);
        }

        BlockState capabilityState = level.getBlockState(capabilityPos);
        BlockEntity blockEntity = level.getBlockEntity(capabilityPos);
        Direction capabilitySide = isCeiExperienceHatch(state) ? null : context.getClickedFace();
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                capabilityPos,
                capabilityState,
                blockEntity,
                capabilitySide
        );
        ResourceLocation fluidId = findExperienceFluid(handler, state);

        if (fluidId == null) {
            capabilitySide = null;
            handler = level.getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    capabilityPos,
                    capabilityState,
                    blockEntity,
                    null
            );
            fluidId = findExperienceFluid(handler, state);
        }

        if (fluidId == null) {
            return super.useOn(context);
        }

        if (!level.isClientSide) {
            setBinding(context.getItemInHand(), level, pos, capabilitySide, fluidId);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return super.use(level, player, hand);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    this,
                    buffer -> ItemStack.STREAM_CODEC.encode(buffer, serverPlayer.getMainHandItem())
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.maid_sophi_compat.wireless_io_exp");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WirelessIoExpMenu(containerId, inventory, player.getMainHandItem());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        String modeKey = isMaidToContainer(stack)
                ? "tooltip.maid_sophi_compat.wireless_io_exp.mode.maid_to_container"
                : "tooltip.maid_sophi_compat.wireless_io_exp.mode.container_to_maid";
        tooltip.add(Component.translatable(modeKey).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                "tooltip.maid_sophi_compat.wireless_io_exp.retain_level",
                getRetainLevel(stack)
        ).withStyle(ChatFormatting.AQUA));

        BlockPos pos = stack.get(InitDataComponent.BINDING_POS.get());
        if (pos == null) {
            tooltip.add(Component.translatable(
                    "tooltip.maid_sophi_compat.wireless_io_exp.binding.none"
            ).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable(
                    "tooltip.maid_sophi_compat.wireless_io_exp.binding.has",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
            ).withStyle(ChatFormatting.AQUA));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(
                "tooltip.maid_sophi_compat.wireless_io_exp.usage.1"
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.maid_sophi_compat.wireless_io_exp.usage.2"
        ).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack stack) {
        Level level = maid.level();
        if (level.isClientSide || maid.tickCount % TRANSFER_INTERVAL != 0 || maid.guiOpening) {
            return;
        }

        Binding binding = getBinding(stack);
        if (binding == null || !binding.dimension().equals(level.dimension().location())) {
            return;
        }

        BlockPos pos = binding.pos();
        if (!level.hasChunkAt(pos)) {
            return;
        }

        float radius = maid.getRestrictRadius();
        if (maid.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > radius * radius) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        IFluidHandler handler = getBoundFluidHandler(level, pos, state, binding.side());
        if (handler == null) {
            return;
        }

        Fluid fluid = BuiltInRegistries.FLUID.getOptional(binding.fluidId()).orElse(null);
        int millibucketsPerExperience = millibucketsPerExperience(binding.fluidId());
        if (fluid == null || millibucketsPerExperience == 0) {
            return;
        }

        if (isMaidToContainer(stack)) {
            depositExperience(maid, handler, fluid, millibucketsPerExperience, getRetainLevel(stack));
        } else {
            withdrawExperience(maid, handler, fluid, millibucketsPerExperience);
        }
    }

    private static void depositExperience(
            EntityMaid maid,
            IFluidHandler handler,
            Fluid fluid,
            int millibucketsPerExperience,
            int retainLevel
    ) {
        int currentExperience = Math.max(0, maid.getExperience());
        int transferableExperience = currentExperience - experienceAtLevel(retainLevel);
        if (transferableExperience <= 0) {
            return;
        }

        int requestedAmount = fluidAmountForExperience(transferableExperience, millibucketsPerExperience);
        int simulatedAmount = handler.fill(
                new FluidStack(fluid, requestedAmount),
                IFluidHandler.FluidAction.SIMULATE
        );
        int executableAmount = wholeExperienceFluidAmount(
                Math.min(requestedAmount, Math.max(0, simulatedAmount)),
                millibucketsPerExperience
        );
        if (executableAmount == 0) {
            return;
        }

        int executedAmount = handler.fill(
                new FluidStack(fluid, executableAmount),
                IFluidHandler.FluidAction.EXECUTE
        );
        int actualAmount = Math.min(executableAmount, Math.max(0, executedAmount));
        int transferredExperience = actualAmount / millibucketsPerExperience;
        if (transferredExperience > 0) {
            maid.setExperience(currentExperience - transferredExperience);
        }

        if (actualAmount % millibucketsPerExperience != 0) {
            MaidSophiCompat.LOGGER.warn("Experience fluid handler accepted a partial XP unit while depositing");
        }
    }

    private static void withdrawExperience(
            EntityMaid maid,
            IFluidHandler handler,
            Fluid fluid,
            int millibucketsPerExperience
    ) {
        int currentExperience = Math.max(0, maid.getExperience());
        int experienceCapacity = Integer.MAX_VALUE - currentExperience;
        if (experienceCapacity == 0) {
            return;
        }

        int requestedAmount = fluidAmountForExperience(experienceCapacity, millibucketsPerExperience);
        FluidStack simulated = handler.drain(
                new FluidStack(fluid, requestedAmount),
                IFluidHandler.FluidAction.SIMULATE
        );
        if (simulated.isEmpty() || simulated.getFluid() != fluid) {
            return;
        }

        int executableAmount = wholeExperienceFluidAmount(
                Math.min(requestedAmount, simulated.getAmount()),
                millibucketsPerExperience
        );
        if (executableAmount == 0) {
            return;
        }

        FluidStack executed = handler.drain(
                simulated.copyWithAmount(executableAmount),
                IFluidHandler.FluidAction.EXECUTE
        );
        if (executed.isEmpty()) {
            return;
        }
        if (executed.getFluid() != fluid) {
            restoreUnexpectedFluid(handler, executed);
            return;
        }

        int transferredExperience = Math.min(
                experienceCapacity,
                executed.getAmount() / millibucketsPerExperience
        );
        if (transferredExperience > 0) {
            maid.setExperience(currentExperience + transferredExperience);
        }

        int creditedAmount = transferredExperience * millibucketsPerExperience;
        int uncreditedAmount = executed.getAmount() - creditedAmount;
        if (uncreditedAmount > 0) {
            restoreUnexpectedFluid(handler, executed.copyWithAmount(uncreditedAmount));
        }
    }

    private static void restoreUnexpectedFluid(IFluidHandler handler, FluidStack fluid) {
        int restoredAmount = handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        if (restoredAmount < fluid.getAmount()) {
            MaidSophiCompat.LOGGER.warn(
                    "Experience fluid handler did not restore {} mB after an invalid drain result",
                    fluid.getAmount() - Math.max(0, restoredAmount)
            );
        }
    }

    private static int fluidAmountForExperience(int experience, int millibucketsPerExperience) {
        long amount = (long) experience * millibucketsPerExperience;
        int limitedAmount = (int) Math.min(Integer.MAX_VALUE, amount);
        return wholeExperienceFluidAmount(limitedAmount, millibucketsPerExperience);
    }

    private static int wholeExperienceFluidAmount(int amount, int millibucketsPerExperience) {
        return amount - amount % millibucketsPerExperience;
    }

    private static int millibucketsPerExperience(ResourceLocation fluidId) {
        if (CEI_EXPERIENCE.equals(fluidId)) {
            return CEI_MILLIBUCKETS_PER_EXPERIENCE;
        }
        if (SOPHISTICATED_EXPERIENCE.equals(fluidId)) {
            return SOPHISTICATED_MILLIBUCKETS_PER_EXPERIENCE;
        }
        return 0;
    }

    private static int experienceAtLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level > MAX_VANILLA_LEVEL_WITH_INT_EXPERIENCE) {
            return Integer.MAX_VALUE;
        }

        long value;
        if (level <= 16) {
            value = (long) level * level + 6L * level;
        } else if (level <= 31) {
            value = (5L * level * level - 81L * level + 720L) / 2L;
        } else {
            value = (9L * level * level - 325L * level + 4440L) / 2L;
        }
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static Binding getBinding(ItemStack stack) {
        BlockPos pos = stack.get(InitDataComponent.BINDING_POS.get());
        if (pos == null) {
            return null;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TARGET_DIMENSION_TAG));
        ResourceLocation fluidId = ResourceLocation.tryParse(tag.getString(EXPERIENCE_FLUID_TAG));
        if (dimension == null || fluidId == null || !isExperienceFluid(fluidId)) {
            return null;
        }

        String sideName = tag.getString(TARGET_SIDE_TAG);
        Direction side = sideName.isEmpty() ? null : Direction.byName(sideName);
        if (!sideName.isEmpty() && side == null) {
            return null;
        }
        return new Binding(pos, dimension, side, fluidId);
    }

    private static ResourceLocation findExperienceFluid(IFluidHandler handler, BlockState state) {
        if (handler == null) {
            return null;
        }

        boolean hasFluid = false;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isEmpty()) {
                continue;
            }

            hasFluid = true;
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
            if (isExperienceFluid(fluidId)) {
                return fluidId;
            }
        }

        if (hasFluid) {
            return null;
        }

        String blockNamespace = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
        ResourceLocation fluidId = switch (blockNamespace) {
            case "create_enchantment_industry" -> CEI_EXPERIENCE;
            case "sophisticatedbackpacks", "sophisticatedstorage" -> SOPHISTICATED_EXPERIENCE;
            default -> null;
        };
        if (fluidId == null) {
            return null;
        }

        Fluid fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
        if (fluid == null) {
            return null;
        }

        int amount = fluidId.equals(SOPHISTICATED_EXPERIENCE) ? 20 : 1;
        return handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.SIMULATE) > 0
                ? fluidId
                : null;
    }

    private static boolean isExperienceFluid(ResourceLocation fluidId) {
        return CEI_EXPERIENCE.equals(fluidId) || SOPHISTICATED_EXPERIENCE.equals(fluidId);
    }

    private static IFluidHandler getBoundFluidHandler(
            Level level,
            BlockPos bindingPos,
            BlockState bindingState,
            Direction side
    ) {
        BlockPos capabilityPos = getCapabilityPos(bindingPos, bindingState);
        if (!level.hasChunkAt(capabilityPos)) {
            return null;
        }

        BlockState capabilityState = level.getBlockState(capabilityPos);
        BlockEntity blockEntity = level.getBlockEntity(capabilityPos);
        Direction capabilitySide = isCeiExperienceHatch(bindingState) ? null : side;
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                capabilityPos,
                capabilityState,
                blockEntity,
                capabilitySide
        );
        if (handler == null && capabilitySide != null) {
            handler = level.getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    capabilityPos,
                    capabilityState,
                    blockEntity,
                    null
            );
        }
        return handler;
    }

    private static BlockPos getCapabilityPos(BlockPos bindingPos, BlockState bindingState) {
        if (isCeiExperienceHatch(bindingState)
                && bindingState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return bindingPos.relative(bindingState.getValue(HorizontalDirectionalBlock.FACING));
        }
        return bindingPos;
    }

    private static boolean isCeiExperienceHatch(BlockState state) {
        return CEI_EXPERIENCE_HATCH.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static void setBinding(
            ItemStack stack,
            Level level,
            BlockPos pos,
            Direction capabilitySide,
            ResourceLocation fluidId
    ) {
        stack.set(InitDataComponent.BINDING_POS.get(), pos.immutable());
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    tag.putString(TARGET_DIMENSION_TAG, level.dimension().location().toString());
                    tag.putString(TARGET_SIDE_TAG, capabilitySide == null ? "" : capabilitySide.getName());
                    tag.putString(EXPERIENCE_FLUID_TAG, fluidId.toString());
                }
        );
    }

    public static final class WirelessIoExpMenu extends AbstractContainerMenu {
        private final ItemStack wirelessIo;
        private final DataSlot mode;
        private final DataSlot retainLevel;

        public WirelessIoExpMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
            this(containerId, inventory, ItemStack.STREAM_CODEC.decode(buffer));
        }

        private WirelessIoExpMenu(int containerId, Inventory inventory, ItemStack wirelessIo) {
            super(MaidSophiCompat.WIRELESS_IO_EXP_MENU.get(), containerId);
            this.wirelessIo = wirelessIo;
            this.mode = addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return WirelessIoExpBauble.isMaidToContainer(wirelessIo) ? 1 : 0;
                }

                @Override
                public void set(int value) {
                    setMaidToContainer(wirelessIo, value != 0);
                }
            });
            this.retainLevel = addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return WirelessIoExpBauble.getRetainLevel(wirelessIo);
                }

                @Override
                public void set(int value) {
                    setRetainLevel(
                            wirelessIo,
                            Math.max(0, Math.min(MAX_VANILLA_LEVEL_WITH_INT_EXPERIENCE, value))
                    );
                }
            });
            addPlayerSlots(inventory);
        }

        boolean isMaidToContainer() {
            return mode.get() != 0;
        }

        int getRetainLevel() {
            return retainLevel.get();
        }

        @Override
        public boolean clickMenuButton(Player player, int buttonId) {
            if (!stillValid(player)) {
                return false;
            }
            if (buttonId == TOGGLE_MODE_BUTTON) {
                setMaidToContainer(wirelessIo, !isMaidToContainer());
                return true;
            }
            if (buttonId >= RETAIN_LEVEL_BUTTON_OFFSET
                    && buttonId <= RETAIN_LEVEL_BUTTON_OFFSET + MAX_VANILLA_LEVEL_WITH_INT_EXPERIENCE) {
                setRetainLevel(wirelessIo, buttonId - RETAIN_LEVEL_BUTTON_OFFSET);
                return true;
            }
            return false;
        }

        @Override
        public boolean stillValid(Player player) {
            return player.getMainHandItem().is(ModItems.WIRELESS_IO_EXP.get());
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        private void addPlayerSlots(Inventory inventory) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(
                            inventory,
                            column + row * 9 + 9,
                            8 + column * 18,
                            84 + row * 18
                    ));
                }
            }
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column, 8 + column * 18, 142));
            }
        }
    }

    @EventBusSubscriber(
            modid = MaidSophiCompat.MOD_ID,
            value = Dist.CLIENT
    )
    public static final class ClientModEvents {
        private ClientModEvents() {
        }

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(MaidSophiCompat.WIRELESS_IO_EXP_MENU.get(), WirelessIoExpScreen::new);
        }

        // Rendering flow adapted from Touhou Little Maid's WirelessIORenderEvent (MIT).
        @SubscribeEvent
        public static void renderBinding(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }

            ItemStack stack = minecraft.player.getMainHandItem();
            if (!stack.is(ModItems.WIRELESS_IO_EXP.get())) {
                return;
            }

            Binding binding = getBinding(stack);
            if (binding == null
                    || !binding.dimension().equals(minecraft.level.dimension().location())) {
                return;
            }

            Vec3 cameraOffset = event.getCamera().getPosition().reverse();
            AABB bounds = new AABB(binding.pos()).move(cameraOffset);
            VertexConsumer buffer = minecraft.renderBuffers()
                    .bufferSource()
                    .getBuffer(RenderType.LINES);
            LevelRenderer.renderLineBox(
                    event.getPoseStack(),
                    buffer,
                    bounds,
                    0.75F,
                    1.0F,
                    0.1F,
                    1.0F
            );
        }
    }

    public static final class WirelessIoExpScreen extends AbstractContainerScreen<WirelessIoExpMenu> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
                "touhou_little_maid",
                "textures/gui/wireless_io.png"
        );
        private WirelessIOButton modeButton;
        private EditBox retainLevelInput;

        public WirelessIoExpScreen(
                WirelessIoExpMenu menu,
                Inventory inventory,
                Component title
        ) {
            super(menu, inventory, title);
            imageWidth = 176;
            imageHeight = 166;
        }

        @Override
        protected void init() {
            super.init();
            clearWidgets();

            modeButton = new WirelessIOButton(
                    leftPos + 23,
                    topPos + 34,
                    18,
                    18,
                    menu.isMaidToContainer(),
                    (mouseX, mouseY) -> sendMenuButton(TOGGLE_MODE_BUTTON),
                    (graphics, mouseX, mouseY) -> graphics.renderTooltip(
                            font,
                            Component.translatable(menu.isMaidToContainer()
                                    ? "gui.maid_sophi_compat.wireless_io_exp.mode.maid_to_container"
                                    : "gui.maid_sophi_compat.wireless_io_exp.mode.container_to_maid"),
                            mouseX,
                            mouseY
                    )
            );
            modeButton.initTextureValues(194, 32, -18, 18, TEXTURE);
            addRenderableWidget(modeButton);

            retainLevelInput = new EditBox(
                    font,
                    leftPos + 64,
                    topPos + 41,
                    50,
                    18,
                    Component.translatable("gui.maid_sophi_compat.wireless_io_exp.retain_level")
            );
            retainLevelInput.setMaxLength(5);
            retainLevelInput.setFilter(value -> value.chars().allMatch(character ->
                    character >= '0' && character <= '9'));
            retainLevelInput.setValue(Integer.toString(menu.getRetainLevel()));
            addRenderableWidget(retainLevelInput);
        }

        @Override
        protected void containerTick() {
            super.containerTick();
            modeButton.setStateTriggered(menu.isMaidToContainer());
            if (!retainLevelInput.isFocused()) {
                String syncedValue = Integer.toString(menu.getRetainLevel());
                if (!syncedValue.equals(retainLevelInput.getValue())) {
                    retainLevelInput.setValue(syncedValue);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean wasFocused = retainLevelInput.isFocused();
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            if (wasFocused && !retainLevelInput.isFocused()) {
                commitRetainLevel();
            }
            return handled;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean wasFocused = retainLevelInput.isFocused();
            if (wasFocused && (keyCode == 257 || keyCode == 335)) {
                commitRetainLevel();
                retainLevelInput.setFocused(false);
                return true;
            }

            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (wasFocused && !retainLevelInput.isFocused()) {
                commitRetainLevel();
            }
            return handled;
        }

        @Override
        public void onClose() {
            commitRetainLevel();
            super.onClose();
        }

        @Override
        public void removed() {
            commitRetainLevel();
            super.removed();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            renderTooltip(graphics, mouseX, mouseY);
        }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
            renderTransparentBackground(graphics);
            graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        }

        @Override
        protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.maid_sophi_compat.wireless_io_exp.retain_level.short"),
                    89,
                    26,
                    0x404040
            );
        }

        private void commitRetainLevel() {
            if (retainLevelInput == null) {
                return;
            }

            int value;
            try {
                value = retainLevelInput.getValue().isEmpty()
                        ? 0
                        : Integer.parseInt(retainLevelInput.getValue());
            } catch (NumberFormatException ignored) {
                value = MAX_VANILLA_LEVEL_WITH_INT_EXPERIENCE;
            }
            value = Math.max(0, Math.min(MAX_VANILLA_LEVEL_WITH_INT_EXPERIENCE, value));

            String normalizedValue = Integer.toString(value);
            if (!normalizedValue.equals(retainLevelInput.getValue())) {
                retainLevelInput.setValue(normalizedValue);
            }
            if (value != menu.getRetainLevel()) {
                sendMenuButton(RETAIN_LEVEL_BUTTON_OFFSET + value);
            }
        }

        private void sendMenuButton(int buttonId) {
            if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
                return;
            }
            if (menu.clickMenuButton(minecraft.player, buttonId)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            }
        }
    }

    private record Binding(
            BlockPos pos,
            ResourceLocation dimension,
            Direction side,
            ResourceLocation fluidId
    ) {
    }
}
