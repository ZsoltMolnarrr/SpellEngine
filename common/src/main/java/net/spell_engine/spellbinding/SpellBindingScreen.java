package net.spell_engine.spellbinding;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.gui.CustomButton;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.util.SpellRender;
import net.spell_engine.item.SpellEngineItems;
import net.spell_engine.item.UniversalSpellBookItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpellBindingScreen extends AbstractContainerScreen<SpellBindingScreenHandler> {
    private static final Identifier Pl = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "textures/gui/" + SpellBinding.name + ".png");
    private static final Identifier PLACEHOLDER_SPELL_BOOK = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "item/placeholder/spell_book");
    private static final Identifier PLACEHOLDER_LAPIS = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "item/placeholder/lapis");
    private static final Identifier PLACEHOLDER_SCROLL = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "item/placeholder/scroll");
    private final CyclingSlotBackground mainSlotIcon = new CyclingSlotBackground(0);
    private final CyclingSlotBackground consumableSlotIcon = new CyclingSlotBackground(1);

    private static final int BACKGROUND_STANDARD_HEIGHT = 166;
    private static final int BACKGROUND_EXTRA_HEIGHT = 18;
    private static final int BACKGROUND_HEIGHT = BACKGROUND_STANDARD_HEIGHT + BACKGROUND_EXTRA_HEIGHT;

    private ItemStack stack;

    public SpellBindingScreen(SpellBindingScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.stack = ItemStack.EMPTY;
    }

    private int pageOffset = 0;
    private Button upButton;
    private Button downButton;

    protected void init() {
        super.init();
        this.imageHeight = BACKGROUND_HEIGHT;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        this.titleLabelY = 6 - (BACKGROUND_EXTRA_HEIGHT - 2);

        int originX = (this.width - this.imageWidth) / 2;
        int originY = (this.height - this.imageHeight) / 2;
        var x = originX + 156;
        var y = originY - 10;
        var width = 16;
        var height = 10;

        upButton = new CustomButton(x , y,
                CustomButton.Type.SMALL_UP,
                button -> { this.pageUp(); });
        upButton.visible = false;
        downButton = new CustomButton(x , y + (PAGE_SIZE * SpellBindingWidgets.TIER_ROW_HEIGHT) + height + 1,
                CustomButton.Type.SMALL_DOWN,
                button -> { this.pageDown(); });
        downButton.visible = false;
        this.addRenderableWidget(upButton);
        this.addRenderableWidget(downButton);
        minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SpellBindingScreenHandler.INIT_SYNC_ID);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.mainSlotIcon.tick(List.of(PLACEHOLDER_SPELL_BOOK));
        this.consumableSlotIcon.tick(List.of(PLACEHOLDER_LAPIS, PLACEHOLDER_SCROLL));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        try {
            var mode = menu.getMode();

            if (mode == SpellBinding.Mode.BOOK) {
                // BOOK MODE: Check book clicks
                for (var book : bookViewModels) {
                    if (book.mouseOver((int) mouseX, (int) mouseY)) {
                        minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, book.originalIndex());
                        return true;
                    }
                }
            } else {
                // SPELL MODE: Check tier row icon clicks
                for (var tierRow : tierRowViewModels) {
                    if (!tierRow.shown() || !tierRow.mouseOver((int) mouseX, (int) mouseY)) {
                        continue;
                    }

                    for (var icon : tierRow.spellIcons()) {
                        if (icon.mouseOver((int) mouseX, (int) mouseY)) {
                            if (menu.allowUnbinding() && icon.binding().state == SpellBinding.State.ApplyState.ALREADY_APPLIED) {
                                unbindDialog(icon);
                                return true;
                            }

                            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, icon.originalIndex());
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return super.mouseClicked(click, doubled);
    }

    private void unbindDialog(SpellBindingWidgets.SpellIconViewModel icon) {
        var title = Component.translatable("gui.spell_engine.spell_binding.unbind_dialog.title", icon.spell().name().getString());
        // DialogScreen was removed in 1.21.6; ConfirmScreen offers the same two-choice flow
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, icon.originalIndex());
            }
            this.minecraft.setScreen(this);
        }, title, Component.translatable("gui.spell_engine.spell_binding.unbind_dialog.subtitle"),
                Component.translatable("gui.spell_engine.spell_binding.unbind_dialog.confirm").withStyle(ChatFormatting.RED),
                Component.translatable("gui.spell_engine.spell_binding.unbind_dialog.cancel")));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isPagingEnabled()) {
           return false;
        } else {
            for (int i = 0; i < Math.abs(verticalAmount); i++) {
                if (verticalAmount > 0) {
                    pageUp();
                } else {
                    pageDown();
                }
            }
            return true;
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // delta = this.client.getTickDelta();
        super.render(context, mouseX, mouseY, delta); // renders the background itself since 1.21.11 (blurring twice per frame crashes)
        this.renderTooltip(context, mouseX, mouseY);
        var player = Minecraft.getInstance().player;
        var lapisCount = menu.getLapisCount();
        var itemStack = menu.getItems().get(0);
        var mode = menu.getMode();

        if (mode == SpellBinding.Mode.BOOK) {
            // BOOK MODE: Tooltip for books
            for (var book : bookViewModels) {
                if (!book.mouseOver(mouseX, mouseY)) continue;

                ArrayList<Component> tooltip = Lists.newArrayList();

                switch (book.binding().state) {
                    case APPLICABLE -> {
                        if (book.binding().readyToApply(player, 0)) {  // Books don't use lapis
//                            tooltip.add(Text.translatable("gui.spell_engine.spell_binding.create")
//                                    .formatted(Formatting.GREEN));
                        } else {
                            if (book.binding().requirements.requiredLevel() > 0) {
                                var hasRequiredLevels = book.binding().requirements.metRequiredLevel(player);
                                tooltip.add(Component.translatable("gui.spell_engine.spell_binding.level_req_fail",
                                                book.binding().requirements.requiredLevel())
                                        .withStyle(hasRequiredLevels ? ChatFormatting.GRAY : ChatFormatting.RED));
                            }
                            var levelCost = book.binding().requirements.levelCost();
                            if (levelCost > 0) {
                                var hasEnoughLevels = book.binding().requirements.hasEnoughLevelsToSpend(player);
                                MutableComponent levels = levelCost == 1 ? Component.translatable("container.enchant.level.one")
                                        : Component.translatable("container.enchant.level.many", levelCost);
                                tooltip.add(levels.withStyle(hasEnoughLevels ? ChatFormatting.GRAY : ChatFormatting.RED));
                            }
                        }
                    }
                    default -> {}
                }

                // Add book description if available
                if (book.isMouseOverIcon(mouseX, mouseY)) {
                    var key = UniversalSpellBookItem.descriptionKeyFromStack(book.itemStack());
                    if (key != null && Language.getInstance().has(key)) {
                        if (!tooltip.isEmpty()) {
                            tooltip.add(Component.literal(" "));
                        }
                        var rawDescription = Language.getInstance().getOrDefault(key);
                        for (var line : rawDescription.split(System.lineSeparator())) {
                            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                        }
                    }
                }

                if (!tooltip.isEmpty()) {
                    context.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }
                return;  // Only one tooltip at a time
            }
        } else {
            // SPELL MODE: Existing tooltip logic
            for (var tierRow : tierRowViewModels) {
                if (!tierRow.shown()) continue;

                for (var icon : tierRow.spellIcons()) {
                    if (!icon.mouseOver(mouseX, mouseY)) continue;

                    if (icon.spell() != null) {
                        ArrayList<Component> tooltip = Lists.newArrayList();
                        boolean showSpellDetails = true;
                        switch (icon.binding().state) {
                            case ALREADY_APPLIED -> {
                                tooltip.add(Component.translatable("gui.spell_engine.spell_binding.already_bound")
                                        .withStyle(ChatFormatting.GRAY));
                            }
                            case NO_MORE_SLOT -> {
                                tooltip.add(Component.translatable("gui.spell_engine.spell_binding.no_more_slots")
                                        .withStyle(ChatFormatting.GRAY));
                                showSpellDetails = false;
                            }
                            case TIER_CONFLICT -> {
                                tooltip.add(Component.translatable("gui.spell_engine.spell_binding.tier_conflict")
                                        .withStyle(ChatFormatting.GRAY));
                                showSpellDetails = false;
                            }
                            case APPLICABLE -> {
                                if (icon.binding().readyToApply(player, lapisCount)) {
                                    tooltip.add(Component.translatable("gui.spell_engine.spell_binding.available")
                                            .withStyle(ChatFormatting.GREEN));
                                }
                                var hasRequiredLevels = icon.binding().requirements.metRequiredLevel(player);
                                if (icon.binding().requirements.requiredLevel() > 0) {
                                    tooltip.add(Component.translatable("gui.spell_engine.spell_binding.level_req_fail",
                                                    icon.binding().requirements.requiredLevel())
                                            .withStyle(hasRequiredLevels ? ChatFormatting.GRAY : ChatFormatting.RED));
                                }
                                var lapisCost = icon.binding().requirements.lapisCost();
                                if (lapisCost > 0) {
                                    var hasEnoughLapis = icon.binding().requirements.hasEnoughLapis(lapisCount);
                                    MutableComponent lapis = lapisCost == 1 ? Component.translatable("container.enchant.lapis.one") : Component.translatable("container.enchant.lapis.many", lapisCost);
                                    tooltip.add(lapis.withStyle(hasEnoughLapis ? ChatFormatting.GRAY : ChatFormatting.RED));
                                }
                                var levelCost = icon.binding().requirements.levelCost();
                                if (levelCost > 0) {
                                    var hasEnoughLevels = icon.binding().requirements.hasEnoughLevelsToSpend(player);
                                    MutableComponent levels = levelCost == 1 ? Component.translatable("container.enchant.level.one") : Component.translatable("container.enchant.level.many", levelCost);
                                    tooltip.add(levels.withStyle(hasEnoughLevels ? ChatFormatting.GRAY : ChatFormatting.RED));
                                }
                                showSpellDetails = icon.isDetailsPublic();
                            }
                            case INVALID -> {
                                continue;
                            }
                        }

                        if (showSpellDetails) {
                            tooltip.add(Component.literal(" "));
                            tooltip.addAll(SpellTooltip.spellEntry(icon.spell().id(), player, itemStack, true, 0));
                        } else {
                            tooltip.add(icon.spell().name());
                        }
                        context.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                    }
                    return; // Only one tooltip at a time
                }
            }
        }
    }

    private record SubTexture(int u, int v, int width, int height) { }
    private static final SubTexture PLACEHOLDER_BOOK = new SubTexture(240, 0, 16, 16);

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
//        RenderSystem.setShader(GameRenderer::getPositionTexShader);
//        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
//        RenderSystem.setShaderTexture(0, TEXTURE);
        int originX = (this.width - this.imageWidth) / 2;
        int originY = (this.height - this.imageHeight) / 2;

        context.blit(RenderPipelines.GUI_TEXTURED, Pl, originX, originY - BACKGROUND_EXTRA_HEIGHT, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        this.mainSlotIcon.render(this.menu, context, delta, this.leftPos, this.topPos);
        this.consumableSlotIcon.render(this.menu, context, delta, this.leftPos, this.topPos);
        this.updatePageControls();
        this.updateButtons(originX, originY);
        this.drawTierRows(context, mouseX, mouseY);
    }

    private static final int PAGE_SIZE = 3;

    private boolean isPagingEnabled() {
        var mode = menu.getMode();
        if (mode == SpellBinding.Mode.BOOK) {
            return bookViewModels.size() > PAGE_SIZE;
        }
        return tierRowViewModels.size() > PAGE_SIZE;
    }

    private int maximalPageOffset() {
        var mode = menu.getMode();
        if (mode == SpellBinding.Mode.BOOK) {
            return bookViewModels.size() - PAGE_SIZE;
        }
        return tierRowViewModels.size() - PAGE_SIZE;
    }
    private boolean hasPageUp() {
        return pageOffset > 0;
    }

    private boolean hasPageDown() {
        return pageOffset < maximalPageOffset();
    }

    private void pageUp() {
        if (hasPageUp()) {
            pageOffset -= 1;
        }
    }

    private void pageDown() {
        if (hasPageDown()) {
            pageOffset += 1;
        }
    }

    private void restartPaging() {
        this.pageOffset = 0;
    }

    private List<SpellBindingWidgets.TierRowViewModel> tierRowViewModels = List.of();
    private List<SpellBindingWidgets.SpellBookViewModel> bookViewModels = List.of();

    private static final int BUTTONS_ORIGIN_X = 60;
    private static final int BUTTONS_ORIGIN_Y = -1;

    private void updatePageControls() {
        var isPaging = isPagingEnabled();
        upButton.visible = isPaging;
        upButton.active = hasPageUp();
        downButton.visible = isPaging;
        downButton.active = hasPageDown();
    }


    private static final Identifier RUNES_FONT_ID = Identifier.fromNamespaceAndPath("minecraft", "alt");
    private static final Style RUNE_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(RUNES_FONT_ID));

    // Helper record for grouping spells
    private record SpellData(
        int originalIndex,
        SpellBindingWidgets.SpellViewModel spell,
        boolean isEnabled,
        boolean isDetailsPublic,
        SpellBinding.State binding,
        @Nullable ItemStack itemStack
    ) {
        public SpellData(int originalIndex, SpellBindingWidgets.SpellViewModel spell, boolean isEnabled, boolean isDetailsPublic, SpellBinding.State binding) {
            this(originalIndex, spell, isEnabled, isDetailsPublic, binding, null);
        }
    }

    private void updateButtons(int originX, int originY) {
        var tiers = new ArrayList<SpellBindingWidgets.TierRowViewModel>();
        var player = Minecraft.getInstance().player;
        var itemStack = menu.getItems().get(0);
        var lapisCount = menu.getLapisCount();
        var mode = menu.getMode();
        var world = Minecraft.getInstance().level;

        int oldSize = (mode == SpellBinding.Mode.BOOK) ? bookViewModels.size() : tierRowViewModels.size();  // Simplified check

        try {
            // Group spells by tier
            Map<Integer, List<SpellData>> spellsByTier = new LinkedHashMap<>();

            for (int i = 0; i < SpellBindingScreenHandler.MAXIMUM_SPELL_COUNT; i++) {
                var rawId = menu.spellId[i];
                if (rawId < 0) continue; // Skip empty slots

                var levelCost = menu.spellLevelCost[i];
                var requirement = menu.spellLevelRequirement[i];
                var lapisCost = menu.spellLapisCost[i];
                var powered = menu.spellPoweredByLib[i] == 1;

                switch (mode) {
                    case SPELL -> {
                        var spellEntry = SpellRegistry.from(world).get(rawId);
                        if (spellEntry.isEmpty()) continue;

                        var id = spellEntry.get().unwrapKey().get().identifier();
                        var spell = spellEntry.get().value();
                        int tier = spell.tier;

                        SpellBinding.State bindingState = SpellBinding.State.of(world, id, itemStack, levelCost, requirement, lapisCost);
                        boolean isDetailsPublic = powered || bindingState.state == SpellBinding.State.ApplyState.ALREADY_APPLIED;
                        boolean isEnabled = powered && bindingState.readyToApply(player, lapisCount);

                        var text = Component.translatable(SpellTooltip.spellTranslationKey(id)).withStyle(ChatFormatting.GRAY);
                        if (!powered) {
                            text = text.withStyle(ChatFormatting.OBFUSCATED).withStyle(RUNE_STYLE);
                        }

                        var spellViewModel = new SpellBindingWidgets.SpellViewModel(id, SpellRender.iconTexture(id), text);

                        spellsByTier.computeIfAbsent(tier, k -> new ArrayList<>())
                            .add(new SpellData(i, spellViewModel, isEnabled, isDetailsPublic, bindingState));
                    }
                    case BOOK -> {
                        if (rawId < SpellBinding.BOOK_OFFSET) continue;
                        var tags = SpellBinding.availableSpellBookTags(world);
                        var tagIndex = rawId - SpellBinding.BOOK_OFFSET;
                        if (tagIndex >= tags.size()) continue;
                        var tag = tags.get(tagIndex);

                        // Create display stack
                        var displayStack = new ItemStack(SpellEngineItems.SPELL_BOOK.get());
                        UniversalSpellBookItem.applyFromTag(displayStack, tag);

                        SpellBinding.State bindingState = SpellBinding.State.forBook(levelCost, requirement);
                        boolean isEnabled = bindingState.readyToApply(player, lapisCount);

                        var spellViewModel = new SpellBindingWidgets.SpellViewModel(null, null, displayStack.getHoverName());

                        spellsByTier.computeIfAbsent(0, k -> new ArrayList<>())
                            .add(new SpellData(i, spellViewModel, isEnabled, true, bindingState, displayStack));
                    }
                }
            }

            // Build UI elements based on mode
            if (mode == SpellBinding.Mode.BOOK) {
                // BOOK MODE: Build list of SpellBookViewModel (one book per line)
                var books = new ArrayList<SpellBindingWidgets.SpellBookViewModel>();
                int rowIndex = 0;

                for (Map.Entry<Integer, List<SpellData>> entry : spellsByTier.entrySet()) {
                    List<SpellData> bookList = entry.getValue();

                    for (SpellData bookData : bookList) {
                        boolean shown = (rowIndex >= pageOffset) && (rowIndex < (pageOffset + PAGE_SIZE));
                        int rowY = originY + BUTTONS_ORIGIN_Y + ((rowIndex - pageOffset) * SpellBindingWidgets.TIER_ROW_HEIGHT);

                        var book = new SpellBindingWidgets.SpellBookViewModel(
                            bookData.originalIndex,
                            shown,
                            originX + BUTTONS_ORIGIN_X,
                            rowY,
                            SpellBindingWidgets.TIER_ROW_WIDTH,
                            SpellBindingWidgets.TIER_ROW_HEIGHT,
                            bookData.isEnabled,
                            bookData.itemStack,
                            bookData.binding
                        );

                        books.add(book);  // Always add all books
                        rowIndex++;
                    }
                }

                bookViewModels = books;
                tierRowViewModels = List.of();  // Clear tier rows in book mode

            } else {
                // SPELL MODE: Build tierRowViewModels (existing logic)
                int rowIndex = 0;
                for (Map.Entry<Integer, List<SpellData>> entry : spellsByTier.entrySet()) {
                    int tier = entry.getKey();
                    List<SpellData> spells = entry.getValue();

                    if (spells.isEmpty()) continue; // Skip empty tiers

                    boolean shown = (rowIndex >= pageOffset) && (rowIndex < (pageOffset + PAGE_SIZE));
                    int rowY = originY + BUTTONS_ORIGIN_Y + ((rowIndex - pageOffset) * SpellBindingWidgets.TIER_ROW_HEIGHT);

                    // Build spell icons for this tier with equal spacing
                    List<SpellBindingWidgets.SpellIconViewModel> icons = new ArrayList<>();

                    // Calculate equal spacing: total space divided by (iconCount + 1) gaps
                    int iconCount = spells.size();
                    int totalIconWidth = iconCount * SpellBindingWidgets.SPELL_ICON_SIZE;
                    int remainingSpace = SpellBindingWidgets.TIER_ROW_WIDTH - totalIconWidth;
                    int gapCount = iconCount + 1; // edge + between icons + edge
                    int spacing = remainingSpace / gapCount;

                    int tierRowX = originX + BUTTONS_ORIGIN_X + 2;
                    int iconX = tierRowX + spacing;

                    for (SpellData spellData : spells) {
                        var icon = new SpellBindingWidgets.SpellIconViewModel(
                            spellData.originalIndex,
                            iconX,
                            rowY + SpellBindingWidgets.TIER_ROW_ICON_Y_OFFSET,
                            SpellBindingWidgets.SPELL_ICON_SIZE,
                            spellData.isEnabled,
                            spellData.isDetailsPublic,
                            spellData.spell,
                            spellData.binding
                        );
                        icons.add(icon);

                        iconX += spacing + SpellBindingWidgets.SPELL_ICON_SIZE;
                    }

                    var tierRow = new SpellBindingWidgets.TierRowViewModel(
                        tier,
                        shown,
                        originX + BUTTONS_ORIGIN_X,
                        rowY,
                        SpellBindingWidgets.TIER_ROW_WIDTH,
                        SpellBindingWidgets.TIER_ROW_HEIGHT,
                        icons
                    );
                    tiers.add(tierRow);
                    rowIndex++;
                }

                tierRowViewModels = tiers;
                bookViewModels = List.of();  // Clear books in spell mode
            }

        } catch (Exception e) {
            System.err.println("Error when updating Spell Binding Screen UI");
            System.err.println(e.getMessage());
        }

        // Update paging if size changed
        int newSize = (mode == SpellBinding.Mode.BOOK) ? bookViewModels.size() : tierRowViewModels.size();
        if(newSize != oldSize) {
            restartPaging();
        }
    }

    private void drawTierRows(GuiGraphics context, int mouseX, int mouseY) {
        var mode = menu.getMode();

        if (mode == SpellBinding.Mode.BOOK) {
            // BOOK MODE: Render books
            for (var book : bookViewModels) {
                SpellBindingWidgets.drawSpellBook(context, font, book, mouseX, mouseY);
            }
        } else {
            // SPELL MODE: Render tier rows with spell icons
            for (var tierRow : tierRowViewModels) {
                if (!tierRow.shown()) continue;

                // NO tier row background rendering per requirements

                for (var icon : tierRow.spellIcons()) {
                    SpellBindingWidgets.drawSpellIcon(context, icon, mouseX, mouseY);
                }
                for (var icon : tierRow.spellIcons()) {
                    SpellBindingWidgets.drawSpellIconIndicator(context, icon);
                }
            }
        }
    }
}
