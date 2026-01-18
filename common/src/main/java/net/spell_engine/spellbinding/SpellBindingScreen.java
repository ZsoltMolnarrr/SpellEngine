package net.spell_engine.spellbinding;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DialogScreen;
import net.minecraft.client.gui.screen.ingame.CyclingSlotIcon;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
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

@Environment(value= EnvType.CLIENT)
public class SpellBindingScreen extends HandledScreen<SpellBindingScreenHandler> {
    private static final Identifier Pl = Identifier.of(SpellEngineMod.ID, "textures/gui/" + SpellBinding.name + ".png");
    private static final Identifier PLACEHOLDER_SPELL_BOOK = Identifier.of(SpellEngineMod.ID, "item/placeholder/spell_book");
    private static final Identifier PLACEHOLDER_LAPIS = Identifier.of(SpellEngineMod.ID, "item/placeholder/lapis");
    private static final Identifier PLACEHOLDER_SCROLL = Identifier.of(SpellEngineMod.ID, "item/placeholder/scroll");
    private final CyclingSlotIcon mainSlotIcon = new CyclingSlotIcon(0);
    private final CyclingSlotIcon consumableSlotIcon = new CyclingSlotIcon(1);

    private static final int BACKGROUND_STANDARD_HEIGHT = 166;
    private static final int BACKGROUND_EXTRA_HEIGHT = 18;
    private static final int BACKGROUND_HEIGHT = BACKGROUND_STANDARD_HEIGHT + BACKGROUND_EXTRA_HEIGHT;

    private ItemStack stack;

    public SpellBindingScreen(SpellBindingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.stack = ItemStack.EMPTY;
    }

    private int pageOffset = 0;
    private ButtonWidget upButton;
    private ButtonWidget downButton;

    protected void init() {
        super.init();
        this.backgroundHeight = BACKGROUND_HEIGHT;
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;
        this.titleY = 6 - (BACKGROUND_EXTRA_HEIGHT - 2);

        int originX = (this.width - this.backgroundWidth) / 2;
        int originY = (this.height - this.backgroundHeight) / 2;
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
        this.addDrawableChild(upButton);
        this.addDrawableChild(downButton);
        client.interactionManager.clickButton(this.handler.syncId, SpellBindingScreenHandler.INIT_SYNC_ID);
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        this.mainSlotIcon.updateTexture(List.of(PLACEHOLDER_SPELL_BOOK));
        this.consumableSlotIcon.updateTexture(List.of(PLACEHOLDER_LAPIS, PLACEHOLDER_SCROLL));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        try {
            var mode = handler.getMode();

            if (mode == SpellBinding.Mode.BOOK) {
                // BOOK MODE: Check book clicks
                for (var book : bookViewModels) {
                    if (book.mouseOver((int) mouseX, (int) mouseY)) {
                        client.interactionManager.clickButton(this.handler.syncId, book.originalIndex());
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
                            if (handler.allowUnbinding() && icon.binding().state == SpellBinding.State.ApplyState.ALREADY_APPLIED) {
                                unbindDialog(icon);
                                return true;
                            }

                            client.interactionManager.clickButton(this.handler.syncId, icon.originalIndex());
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void unbindDialog(SpellBindingWidgets.SpellIconViewModel icon) {
        var title = Text.translatable("gui.spell_engine.spell_binding.unbind_dialog.title", icon.spell().name().getString());
        client.setScreen(new UnbindDialog(title, ImmutableList.of(
                new DialogScreen.ChoiceButton(Text.translatable("gui.spell_engine.spell_binding.unbind_dialog.confirm").formatted(Formatting.RED), button -> {
            client.interactionManager.clickButton(this.handler.syncId, icon.originalIndex());
            this.client.setScreen(this);
        }), new DialogScreen.ChoiceButton(Text.translatable("gui.spell_engine.spell_binding.unbind_dialog.cancel"), button -> {
            this.client.setScreen(this);
        }))));
    }

    public static class UnbindDialog extends DialogScreen {
        protected UnbindDialog(Text title, ImmutableList<ChoiceButton> choiceButtons) {
            super(title, List.of(Text.translatable("gui.spell_engine.spell_binding.unbind_dialog.subtitle")), choiceButtons);
        }
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // delta = this.client.getTickDelta();
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        var player = MinecraftClient.getInstance().player;
        var lapisCount = handler.getLapisCount();
        var itemStack = handler.getStacks().get(0);
        var mode = handler.getMode();

        if (mode == SpellBinding.Mode.BOOK) {
            // BOOK MODE: Tooltip for books
            for (var book : bookViewModels) {
                if (!book.mouseOver(mouseX, mouseY)) continue;

                ArrayList<Text> tooltip = Lists.newArrayList();

                switch (book.binding().state) {
                    case APPLICABLE -> {
                        if (book.binding().readyToApply(player, 0)) {  // Books don't use lapis
//                            tooltip.add(Text.translatable("gui.spell_engine.spell_binding.create")
//                                    .formatted(Formatting.GREEN));
                        } else {
                            if (book.binding().requirements.requiredLevel() > 0) {
                                var hasRequiredLevels = book.binding().requirements.metRequiredLevel(player);
                                tooltip.add(Text.translatable("gui.spell_engine.spell_binding.level_req_fail",
                                                book.binding().requirements.requiredLevel())
                                        .formatted(hasRequiredLevels ? Formatting.GRAY : Formatting.RED));
                            }
                            var levelCost = book.binding().requirements.levelCost();
                            if (levelCost > 0) {
                                var hasEnoughLevels = book.binding().requirements.hasEnoughLevelsToSpend(player);
                                MutableText levels = levelCost == 1 ? Text.translatable("container.enchant.level.one")
                                        : Text.translatable("container.enchant.level.many", levelCost);
                                tooltip.add(levels.formatted(hasEnoughLevels ? Formatting.GRAY : Formatting.RED));
                            }
                        }
                    }
                    default -> {}
                }

                // Add book description if available
                if (book.isMouseOverIcon(mouseX, mouseY)) {
                    var key = UniversalSpellBookItem.descriptionKeyFromStack(book.itemStack());
                    if (key != null && Language.getInstance().hasTranslation(key)) {
                        if (!tooltip.isEmpty()) {
                            tooltip.add(Text.literal(" "));
                        }
                        var rawDescription = Language.getInstance().get(key);
                        for (var line : rawDescription.split(System.lineSeparator())) {
                            tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
                        }
                    }
                }

                if (!tooltip.isEmpty()) {
                    context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
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
                        ArrayList<Text> tooltip = Lists.newArrayList();
                        boolean showSpellDetails = true;
                        switch (icon.binding().state) {
                            case ALREADY_APPLIED -> {
                                tooltip.add(Text.translatable("gui.spell_engine.spell_binding.already_bound")
                                        .formatted(Formatting.GRAY));
                            }
                            case NO_MORE_SLOT -> {
                                tooltip.add(Text.translatable("gui.spell_engine.spell_binding.no_more_slots")
                                        .formatted(Formatting.GRAY));
                                showSpellDetails = false;
                            }
                            case TIER_CONFLICT -> {
                                tooltip.add(Text.translatable("gui.spell_engine.spell_binding.tier_conflict")
                                        .formatted(Formatting.GRAY));
                                showSpellDetails = false;
                            }
                            case APPLICABLE -> {
                                if (icon.binding().readyToApply(player, lapisCount)) {
                                    tooltip.add(Text.translatable("gui.spell_engine.spell_binding.available")
                                            .formatted(Formatting.GREEN));
                                }
                                var hasRequiredLevels = icon.binding().requirements.metRequiredLevel(player);
                                if (icon.binding().requirements.requiredLevel() > 0) {
                                    tooltip.add(Text.translatable("gui.spell_engine.spell_binding.level_req_fail",
                                                    icon.binding().requirements.requiredLevel())
                                            .formatted(hasRequiredLevels ? Formatting.GRAY : Formatting.RED));
                                }
                                var lapisCost = icon.binding().requirements.lapisCost();
                                if (lapisCost > 0) {
                                    var hasEnoughLapis = icon.binding().requirements.hasEnoughLapis(lapisCount);
                                    MutableText lapis = lapisCost == 1 ? Text.translatable("container.enchant.lapis.one") : Text.translatable("container.enchant.lapis.many", lapisCost);
                                    tooltip.add(lapis.formatted(hasEnoughLapis ? Formatting.GRAY : Formatting.RED));
                                }
                                var levelCost = icon.binding().requirements.levelCost();
                                if (levelCost > 0) {
                                    var hasEnoughLevels = icon.binding().requirements.hasEnoughLevelsToSpend(player);
                                    MutableText levels = levelCost == 1 ? Text.translatable("container.enchant.level.one") : Text.translatable("container.enchant.level.many", levelCost);
                                    tooltip.add(levels.formatted(hasEnoughLevels ? Formatting.GRAY : Formatting.RED));
                                }
                                showSpellDetails = icon.isDetailsPublic();
                            }
                            case INVALID -> {
                                continue;
                            }
                        }

                        if (showSpellDetails) {
                            tooltip.add(Text.literal(" "));
                            tooltip.addAll(SpellTooltip.spellEntry(icon.spell().id(), player, itemStack, true, 0));
                        } else {
                            tooltip.add(icon.spell().name());
                        }
                        context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
                    }
                    return; // Only one tooltip at a time
                }
            }
        }
    }

    private record SubTexture(int u, int v, int width, int height) { }
    private static final SubTexture PLACEHOLDER_BOOK = new SubTexture(240, 0, 16, 16);

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        DiffuseLighting.disableGuiDepthLighting();
//        RenderSystem.setShader(GameRenderer::getPositionTexShader);
//        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
//        RenderSystem.setShaderTexture(0, TEXTURE);
        int originX = (this.width - this.backgroundWidth) / 2;
        int originY = (this.height - this.backgroundHeight) / 2;

        context.drawTexture(Pl, originX, originY - BACKGROUND_EXTRA_HEIGHT, 0, 0, this.backgroundWidth, this.backgroundHeight);

        this.mainSlotIcon.render(this.handler, context, delta, this.x, this.y);
        this.consumableSlotIcon.render(this.handler, context, delta, this.x, this.y);

        DiffuseLighting.enableGuiDepthLighting();
        this.updatePageControls();
        this.updateButtons(originX, originY);
        this.drawTierRows(context, mouseX, mouseY);
    }

    private static final int PAGE_SIZE = 3;

    private boolean isPagingEnabled() {
        var mode = handler.getMode();
        if (mode == SpellBinding.Mode.BOOK) {
            return bookViewModels.size() > PAGE_SIZE;
        }
        return tierRowViewModels.size() > PAGE_SIZE;
    }

    private int maximalPageOffset() {
        var mode = handler.getMode();
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


    private static final Identifier RUNES_FONT_ID = Identifier.of("minecraft", "alt");
    private static final Style RUNE_STYLE = Style.EMPTY.withFont(RUNES_FONT_ID);

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
        var player = MinecraftClient.getInstance().player;
        var itemStack = handler.getStacks().get(0);
        var lapisCount = handler.getLapisCount();
        var mode = handler.getMode();
        var world = MinecraftClient.getInstance().world;

        int oldSize = (mode == SpellBinding.Mode.BOOK) ? bookViewModels.size() : tierRowViewModels.size();  // Simplified check

        try {
            // Group spells by tier
            Map<Integer, List<SpellData>> spellsByTier = new LinkedHashMap<>();

            for (int i = 0; i < SpellBindingScreenHandler.MAXIMUM_SPELL_COUNT; i++) {
                var rawId = handler.spellId[i];
                if (rawId < 0) continue; // Skip empty slots

                var levelCost = handler.spellLevelCost[i];
                var requirement = handler.spellLevelRequirement[i];
                var lapisCost = handler.spellLapisCost[i];
                var powered = handler.spellPoweredByLib[i] == 1;

                switch (mode) {
                    case SPELL -> {
                        var spellEntry = SpellRegistry.from(world).getEntry(rawId);
                        if (spellEntry.isEmpty()) continue;

                        var id = spellEntry.get().getKey().get().getValue();
                        var spell = spellEntry.get().value();
                        int tier = spell.tier;

                        SpellBinding.State bindingState = SpellBinding.State.of(world, id, itemStack, levelCost, requirement, lapisCost);
                        boolean isDetailsPublic = powered || bindingState.state == SpellBinding.State.ApplyState.ALREADY_APPLIED;
                        boolean isEnabled = powered && bindingState.readyToApply(player, lapisCount);

                        var text = Text.translatable(SpellTooltip.spellTranslationKey(id)).formatted(Formatting.GRAY);
                        if (!powered) {
                            text = text.formatted(Formatting.OBFUSCATED).fillStyle(RUNE_STYLE);
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

                        var spellViewModel = new SpellBindingWidgets.SpellViewModel(null, null, displayStack.getName());

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

    private void drawTierRows(DrawContext context, int mouseX, int mouseY) {
        var mode = handler.getMode();

        if (mode == SpellBinding.Mode.BOOK) {
            // BOOK MODE: Render books
            for (var book : bookViewModels) {
                SpellBindingWidgets.drawSpellBook(context, textRenderer, book, mouseX, mouseY);
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
