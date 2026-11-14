package com.unclejaspie.emibookmarks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.unclejaspie.emibookmarks.Emibookmarks;
import com.unclejaspie.emibookmarks.bookmark.*;
import com.unclejaspie.emibookmarks.client.emi.*;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.screen.EmiScreenBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Inventory-side bookmark panel backed by independent world/server-local records. */
@Mod.EventBusSubscriber(modid = Emibookmarks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BookmarkSidebar {
    private static final BookmarkGestures GESTURES = new BookmarkGestures();
    private static final BookmarkReorder REORDER = new BookmarkReorder();
    private static final BookmarkDragPaging DRAG_PAGING = new BookmarkDragPaging();
    private static double pressX, pressY;
    private static double grabX, grabY;
    private static boolean reorderArmed;
    private static boolean foldArmed;
    private static boolean fillArmed;
    private static boolean dropAllowed;
    private static UUID pressedGroup;
    private static BookmarkLayout.Drop lastDrop;
    public static BookmarkCollection displayCollection() { return REORDER.view(BookmarkWorkspace.collection()); }
    private static int page;
    private static Screen owner;
    private static UUID pressedRecipe, pressedSlot;
    private static int pressedButton;
    private static final Set<InputConstants.Key> HELD_KEYS = new HashSet<>();

    private static BookmarkCollection collection() { return displayCollection(); }
    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) { BookmarkWorkspace.open(); page = 0; cancel(); }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BookmarkWorkspace.close(); cancel(); HELD_KEYS.clear(); }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) BookmarkWorkspace.tick();
    }
    public static BookmarkLayout layout(Screen screen) {
        return BookmarkDock.layout(screen, page);
    }
    private static BookmarkLayout layoutAt(Screen screen, double x, double y) {
        var hit = BookmarkDock.layoutAt(screen, page, x, y);
        return hit != null || (!GESTURES.active() && pressedRecipe == null && pressedGroup == null) ? hit : layout(screen);
    }
    public static int page() { return page; }
    private static void cancel() {
        GESTURES.cancel(); REORDER.cancel(); DRAG_PAGING.reset(); reorderArmed = false; foldArmed = false; fillArmed = false;
        pressedRecipe = null; pressedSlot = null; pressedGroup = null; lastDrop = null; dropAllowed = false;
    }
    private static String bookmarkKey() { return BookmarkKeys.BOOKMARK.getTranslatedKeyMessage().getString(); }
    private static boolean editing() {
        if (BookmarkWorkspace.writable()) return true;
        BookmarkWorkspace.message("Bookmark editing is unavailable; the existing save is preserved. See the log.");
        return false;
    }
    private static EmiIngredient ingredient(BookmarkCollection.Recipe record, BookmarkLayout.Cell cell) {
        var ingredient = BookmarkCapture.ingredient(record.icons().get(cell.icon()), record.batches());
        if (!ingredient.isEmpty()) ingredient.setAmount(cell.amount());
        return ingredient;
    }
    private static long displayBatches(BookmarkLayout l, BookmarkCollection.Recipe record) {
        var calculation = l.calculations.get(record.id());
        return calculation != null && calculation.valid() ? calculation.batches().get(record.id()) : record.batches();
    }
    public static EmiStackInteraction stackAt(Screen screen, int x, int y) {
        var l = layoutAt(screen, x, y); var cell = l == null ? null : l.cell(x, y);
        if (cell == null || cell.blank()) return EmiStackInteraction.EMPTY;
        var r = collection().recipes.get(cell.recipeIndex()); var slot = r.icons().get(cell.icon());
        return new EmiStackInteraction(ingredient(r, cell),
                slot.role == BookmarkSlot.Role.OUTPUT ? BookmarkCapture.resolve(r) : null, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (REORDER.active() || reorderArmed) {
            if (event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) cancel();
            event.setCanceled(true); return;
        }
        var inputClient = Minecraft.getInstance();
        int inputX = (int) (inputClient.mouseHandler.xpos() * screen.width / inputClient.getWindow().getScreenWidth());
        int inputY = (int) (inputClient.mouseHandler.ypos() * screen.height / inputClient.getWindow().getScreenHeight());
        boolean recipeItemCapture = Screen.hasControlDown() && !Screen.hasShiftDown() && !BookmarkRecipeContext.ingredientAt(screen, inputX, inputY).isEmpty();
        if (Minecraft.getInstance().player == null || (EmiApi.isSearchFocused() && !recipeItemCapture) ||
                (screen.getFocused() instanceof EditBox edit && edit.canConsumeInput())) return;
        var key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        if (BookmarkKeys.matches(BookmarkKeys.TOGGLE, key) && !EmiScreenBase.of(screen).isEmpty()) {
            if (HELD_KEYS.add(key)) { BookmarkDock.toggle(); cancel(); owner = screen; }
            event.setCanceled(true); return;
        }
        if (event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_C && Screen.hasControlDown() && Screen.hasShiftDown()) {
            event.setCanceled(true);
            if (HELD_KEYS.add(key)) {
                if (screen instanceof dev.emi.emi.screen.BoMScreen) BookmarkTreeActions.importNew();
                else BookmarkWorkspace.message("Open a recipe group with Shift+C; Ctrl+Shift+C saves a new group inside EMI's tree");
            }
            return;
        }
        if (BookmarkKeys.matches(BookmarkKeys.TREE, key)) {
            event.setCanceled(true);
            if (!HELD_KEYS.add(key)) return;
            var client = Minecraft.getInstance();
            int x = (int) (client.mouseHandler.xpos() * screen.width / client.getWindow().getScreenWidth());
            int y = (int) (client.mouseHandler.ypos() * screen.height / client.getWindow().getScreenHeight());
            var layout = layoutAt(screen, x, y); var cell = layout == null ? null : layout.cell(x, y);
            var record = cell == null || cell.blank() ? null : collection().recipes.get(cell.recipeIndex());
            BookmarkTreeActions.openNative(screen, x, y, record, record == null ? null : record.icons().get(cell.icon()));
            return;
        }
        if (!BookmarkKeys.matches(BookmarkKeys.BOOKMARK, key)) return;
        event.setCanceled(true);
        if (!HELD_KEYS.add(key)) return;
        var client = Minecraft.getInstance();
        int x = (int) (client.mouseHandler.xpos() * screen.width / client.getWindow().getScreenWidth());
        int y = (int) (client.mouseHandler.ypos() * screen.height / client.getWindow().getScreenHeight());
        var l = layoutAt(screen, x, y); var cell = l == null ? null : l.cell(x, y);
        if (Screen.hasControlDown() && Screen.hasAltDown()) {
            BookmarkWorkspace.message("Use Shift+C to edit a group, or Ctrl+Shift+C in EMI's tree to save a new group");
            return;
        }
        if (cell != null && !cell.blank()) {
            if (editing()) {
                if (Screen.hasShiftDown()) collection().removeScope(cell.group());
                else collection().remove(cell.recipe());
                BookmarkWorkspace.changed();
            }
            event.setCanceled(true); return;
        }
        if (l != null && l.bounds.contains(x, y)) {
            if (Screen.hasShiftDown() && editing()) {
                var row = l.gutter(x, y);
                if (row != null || l.label.contains(x, y)) {
                    collection().removeScope(row == null ? null : row.group()); BookmarkWorkspace.changed();
                }
            }
            return;
        }
        try {
        var hovered = EmiApi.getHoveredStack(x, y, true);
        // Recipe input widgets may not advertise a slot recipe context. The containing
        // visible WidgetGroup still identifies the exact chosen recipe, including empty areas.
        var visibleRecipe = Screen.hasShiftDown() ? BookmarkRecipeContext.at(screen, x, y) : null;
        if (visibleRecipe != null) {
            captureRecipe(visibleRecipe, BookmarkRecipeContext.outputIndexAt(screen, x, y, visibleRecipe), BookmarkRecipeContext.outputAt(screen, x, y, visibleRecipe));
            return;
        }
        var viewerIngredient = BookmarkRecipeContext.ingredientAt(screen, x, y);
        var captureIngredient = !viewerIngredient.isEmpty() ? viewerIngredient : hovered.getStack();
        if (captureIngredient.isEmpty()) return;
        event.setCanceled(true);
        if (!editing()) return;
            BookmarkCollection.Recipe record;
            if (Screen.hasShiftDown()) {
                BookmarkWorkspace.message("Open the chosen EMI recipe before using Shift+" + bookmarkKey());
                return;
            } else record = BookmarkCapture.item(captureIngredient, Screen.hasControlDown());
            boolean added = BookmarkIdentity.toggle(collection(), record);
            BookmarkDock.show(); BookmarkWorkspace.changed();
            BookmarkWorkspace.message(added ? "Item bookmarked" : "Item bookmark removed");
        } catch (RuntimeException | LinkageError e) {
            com.mojang.logging.LogUtils.getLogger().warn("Bookmark capture failed", e);
            BookmarkWorkspace.message("Could not bookmark this resource: " + e.getMessage());
        }
    }
    public static void captureRecipe(EmiRecipe recipe, EmiIngredient hovered) {
        captureRecipe(recipe, -1, hovered);
    }
    public static void captureRecipe(EmiRecipe recipe, int primary, EmiIngredient hovered) {
        captureRecipe(recipe, primary, hovered, Screen.hasControlDown());
    }
    public static void captureRecipe(EmiRecipe recipe, int primary, EmiIngredient hovered, boolean counted) {
        if (!editing()) return;
        try {
        if (primary < 0) primary = BookmarkCapture.outputIndex(recipe, hovered);
        int count = (int) recipe.getOutputs().stream().filter(s -> !s.isEmpty()).count();
        if (primary < 0 && count == 1) primary = 0;
        if (primary < 0 && count > 1) {
            Minecraft.getInstance().setScreen(new BookmarkOutputScreen(Minecraft.getInstance().screen, recipe, counted)); return;
        }
            var record = BookmarkCapture.recipe(recipe, primary, hovered);
            record.counted(counted);
            boolean added = BookmarkIdentity.toggle(collection(), record);
            BookmarkDock.show(); BookmarkWorkspace.changed();
            BookmarkWorkspace.message(added ? "Recipe bookmarked" : "Recipe bookmark removed");
        } catch (RuntimeException | LinkageError e) {
            com.mojang.logging.LogUtils.getLogger().warn("Selected recipe capture failed", e);
            BookmarkWorkspace.message("Could not bookmark this recipe: " + e.getMessage());
        }
    }
    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) { cancel(); HELD_KEYS.clear(); owner = event.getScreen(); }
    @SubscribeEvent
    public static void treeHint(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof dev.emi.emi.screen.BoMScreen)) return;
        var font = Minecraft.getInstance().font;
        String hint = "Ctrl + Shift + C: Save as NEW recipe group | Gold +: Save/update";
        event.getGuiGraphics().drawString(font, font.plainSubstrByWidth(hint, Math.max(0, event.getScreen().width - 30)),
                6, event.getScreen().height - 13, 0xFFD966);
    }
    @SubscribeEvent(receiveCanceled = true)
    public static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        HELD_KEYS.remove(InputConstants.getKey(event.getKeyCode(), event.getScanCode()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void press(ScreenEvent.MouseButtonPressed.Pre event) {
        var l = layoutAt(event.getScreen(), event.getMouseX(), event.getMouseY());
        if (l == null || !l.bounds.contains(event.getMouseX(), event.getMouseY())) return;
        int button = event.getButton(); double x = event.getMouseX(), y = event.getMouseY();
        cancel();
        if (l.treeImport.contains(x, y) && button == 0) BookmarkTreeActions.importCurrent();
        else if (l.options.contains(x, y) && button == 0) Minecraft.getInstance().setScreen(new BookmarkOptionsScreen(event.getScreen()));
        else if (l.previous.contains(x, y) && button == 0) page = Math.max(0, l.page - 1);
        else if (l.next.contains(x, y) && button == 0) page = Math.min(l.pages - 1, l.page + 1);
        else if (l.label.contains(x, y)) {
            if (editing()) {
                if (button == 1) collection().chain = !collection().chain;
                else if (button == 0 && Screen.hasAltDown()) collection().collapsed = !collection().collapsed;
                else if (button == 0) collection().mode = collection().mode == BookmarkCollection.Mode.TODO ?
                        BookmarkCollection.Mode.COMPACT : BookmarkCollection.Mode.TODO;
                BookmarkWorkspace.changed();
            }
        } else pressGrid(l, x, y, button, Screen.hasShiftDown(), Screen.hasAltDown());
        event.setCanceled(true);
    }
    private static void pressGrid(BookmarkLayout l, double x, double y, int button, boolean shift, boolean alt) {
            var cell = l.cell(x, y);
            if (cell != null && !cell.blank() && (button == 0 || button == 1)) {
                pressedRecipe = cell.recipe(); pressedSlot = collection().recipes.get(cell.recipeIndex()).icons().get(cell.icon()).id;
                pressedButton = button;
                var record = collection().recipes.get(cell.recipeIndex());
                foldArmed = button == 0 && alt && !shift && BookmarkFolding.available(collection(), record, pressedSlot);
                fillArmed = button == 0 && shift && !alt;
                reorderArmed = button == 0 && shift && BookmarkWorkspace.writable();
                pressX = x; pressY = y;
                grabX = x - cell.artwork().x(); grabY = y - cell.artwork().y();
            } else if (l.gutter(x, y) != null && editing()) {
                var row = l.gutter(x, y);
                if (button == 0 && shift && row.group() != null) {
                    pressedGroup = row.group(); pressedButton = button; reorderArmed = true;
                    pressX = x; pressY = y; grabX = x - row.gutter().x() - BookmarkLayout.GUTTER;
                    var firstRow = l.rows.stream().filter(r -> pressedGroup.equals(r.group())).findFirst().orElseThrow();
                    grabY = y - firstRow.gutter().y();
                } else GESTURES.press(collection(), l, x, y, button, shift, alt, System.currentTimeMillis());
            }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        // Leaving the dock can skip reorder hit testing; it must still cancel a pending fill.
        if (Math.hypot(event.getMouseX() - pressX, event.getMouseY() - pressY) >= 3) fillArmed = false;
        var l = layoutAt(event.getScreen(), event.getMouseX(), event.getMouseY()); if (l == null) return;
        if (reorderArmed && (pressedRecipe != null || pressedGroup != null)) {
            if (REORDER.active() || Math.hypot(event.getMouseX() - pressX, event.getMouseY() - pressY) >= 3) {
                if (!REORDER.active()) {
                    if (pressedGroup != null) REORDER.startGroup(BookmarkWorkspace.collection(), pressedGroup);
                    else REORDER.start(BookmarkWorkspace.collection(), pressedRecipe);
                }
                updateReorder(l, event.getMouseX(), event.getMouseY());
            }
            event.setCanceled(true);
        } else if (foldArmed && pressedRecipe != null) {
            if (Math.hypot(event.getMouseX() - pressX, event.getMouseY() - pressY) >= 3) {
                pressedRecipe = null; pressedSlot = null; foldArmed = false;
            }
            event.setCanceled(true);
        } else if (GESTURES.active()) {
            GESTURES.update(l, event.getMouseX(), event.getMouseY(), System.currentTimeMillis());
            event.setCanceled(true);
        } else if (pressedRecipe != null || l.bounds.contains(event.getMouseX(), event.getMouseY())) {
            pressedRecipe = null; pressedSlot = null; event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        var l = layoutAt(event.getScreen(), event.getMouseX(), event.getMouseY());
        if (l == null) { cancel(); return; }
        if (REORDER.active()) {
            if (event.getButton() != pressedButton) { event.setCanceled(true); return; }
            updateReorder(l, event.getMouseX(), event.getMouseY());
            if (dropAllowed && l.body.contains(event.getMouseX(), event.getMouseY()) && REORDER.commit()) BookmarkWorkspace.changed();
            cancel(); event.setCanceled(true); return;
        }
        if (pressedGroup != null) { cancel(); event.setCanceled(true); return; }
        boolean consumed = GESTURES.release(collection(), l, event.getMouseX(), event.getMouseY(), event.getButton(), System.currentTimeMillis());
        if (consumed) BookmarkWorkspace.changed();
        var cell = l.cell(event.getMouseX(), event.getMouseY());
        if (!consumed && cell != null && !cell.blank() && cell.recipe().equals(pressedRecipe) && event.getButton() == pressedButton) {
            var record = collection().recipes.get(cell.recipeIndex()); var slot = record.icons().get(cell.icon());
            if (slot.id.equals(pressedSlot)) {
                if (foldArmed) {
                    if (editing() && BookmarkFolding.toggle(collection(), record, slot.id)) BookmarkWorkspace.changed();
                } else if (!fillArmed || Math.hypot(event.getMouseX() - pressX, event.getMouseY() - pressY) >= 3 ||
                        !BookmarkRecipeFill.fill(record, slot)) navigate(record, slot, pressedButton);
            }
            consumed = true;
        }
        cancel();
        if (consumed || l.bounds.contains(event.getMouseX(), event.getMouseY())) event.setCanceled(true);
    }
    private static void updateReorder(BookmarkLayout l, double x, double y) {
        var cell = l.cell(x, y);
        if (l.body.contains(x, y) && cell != null && REORDER.members().contains(cell.recipe())) { dropAllowed = true; return; }
        var target = l.drop(x, y, REORDER.members(), REORDER.atomicTargets(), lastDrop);
        dropAllowed = target != null && REORDER.insert(target.target(), target.after());
        if (dropAllowed) lastDrop = target;
    }
    private static void navigate(BookmarkCollection.Recipe record, BookmarkSlot slot, int button) {
        var ingredient = BookmarkCapture.ingredient(slot, record.batches());
        if (button == 0 && record.isRecipe() && slot.role == BookmarkSlot.Role.OUTPUT) {
            var recipe = BookmarkCapture.resolve(record);
            if (recipe != null) EmiApi.displayRecipe(recipe);
            else BookmarkWorkspace.message("The saved recipe is unavailable. Its bookmark snapshot has been retained.");
        } else if (!ingredient.isEmpty()) {
            if (button == 1) EmiApi.displayUses(ingredient); else EmiApi.displayRecipes(ingredient);
        } else BookmarkWorkspace.message("This saved resource is unavailable. Its bookmark snapshot has been retained.");
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        var l = layoutAt(event.getScreen(), event.getMouseX(), event.getMouseY());
        if (l == null || !l.bounds.contains(event.getMouseX(), event.getMouseY())) return;
        if (REORDER.active()) { event.setCanceled(true); return; }
        int direction = event.getScrollDelta() > 0 ? 1 : -1;
        var cell = l.cell(event.getMouseX(), event.getMouseY()); var row = l.gutter(event.getMouseX(), event.getMouseY());
        if (Screen.hasControlDown()) {
            if (editing()) {
                long step = Screen.hasAltDown() ? 64 : 1;
                if (cell != null && !cell.blank()) {
                    var record = collection().recipes.get(cell.recipeIndex()); var group = collection().groupOf(record.id());
                    if (group != null && group.collapsed || group == null && collection().collapsed) adjustGroup(group, direction, step);
                    else record.adjustBatches(direction, step);
                } else if (row != null) adjustGroup(collection().groupById(row.group()), direction, step);
                else if (l.label.contains(event.getMouseX(), event.getMouseY())) adjustGroup(null, direction, step);
                BookmarkWorkspace.changed();
            }
        } else if (Screen.hasShiftDown() && cell != null && !cell.blank()) {
            if (editing()) {
                var record = collection().recipes.get(cell.recipeIndex());
                for (int source : cell.sources()) record.icons().get(source).cycle(direction);
                BookmarkWorkspace.changed();
            }
        } else page = Math.max(0, Math.min(l.pages - 1, l.page - direction));
        event.setCanceled(true);
    }
    private static void adjustGroup(BookmarkCollection.Group group, int direction, long step) {
        boolean chain = group == null ? collection().chain : group.chain;
        var calculation = chain ? BookmarkChains.scope(collection(), group == null ? null : group.id) : null;
        for (var record : collection().recipes)
            if (group == null ? collection().groupOf(record.id()) == null : group.members.contains(record.id()))
                if (calculation == null || !calculation.valid() || calculation.targets().contains(record.id())) record.adjustBatches(direction, step);
    }
    public static void renderDocked(BookmarkLayout l, GuiGraphics g, Screen screen, int mouseX, int mouseY, float partialTick) {
        if (owner != screen) { cancel(); owner = screen; }
        page = l.page; long now = System.currentTimeMillis();
        if (GESTURES.active() || REORDER.active()) {
            if (GESTURES.active()) GESTURES.update(l, mouseX, mouseY, now);
            if (GESTURES.dragging() || REORDER.active()) page = Math.max(0, Math.min(l.pages - 1,
                    page + DRAG_PAGING.update(l, mouseX, mouseY, now)));
        }
        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        boolean customBackground = BookmarkClientConfig.BACKGROUND.get() == BookmarkClientConfig.Background.CUSTOM;
        if (customBackground) fill(g, l.bounds, ((int) Math.round(BookmarkClientConfig.SIDEBAR_OPACITY.get() * 255) << 24) | 0x202020);
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, "<", l.previous.x() + 8, l.previous.y() + 5, 0xFFFFFF);
        g.drawCenteredString(font, ">", l.next.x() + 8, l.next.y() + 5, 0xFFFFFF);
        g.drawCenteredString(font, (l.page + 1) + "/" + l.pages, l.label.x() + l.label.width() / 2, l.label.y() + 5,
                collection().chain ? 0x55DD77 : 0xCCCCCC);
        g.drawCenteredString(font, "...", l.options.x() + 8, l.options.y() + 5, 0xDDDDDD);
        g.drawCenteredString(font, "+", l.treeImport.x() + 8, l.treeImport.y() + 5, 0xFFD966);
        if (l.calculations.values().stream().anyMatch(result -> !result.valid()))
            g.drawString(font, "!", l.footer.x() + 3, l.footer.y() + 5, 0xFFDD88);
        if (collection().recipes.isEmpty()) {
            var lines = font.split(Component.literal("Hover EMI: " + bookmarkKey() + " item; Shift+" + bookmarkKey() + " chosen recipe"), l.bounds.width() - 10);
            int y = l.bounds.y() + BookmarkLayout.HEADER + 4;
            for (var line : lines) {
                if (y + 9 > l.footer.y()) break;
                g.drawString(font, line, l.bounds.x() + 5, y, 0xBBBBBB); y += 10;
            }
        }
        var inspection = l.cell(mouseX, mouseY);
        var inspectionRow = l.gutter(mouseX, mouseY);
        UUID inspectionGroup = inspection == null ? inspectionRow == null ? null : inspectionRow.group() : inspection.group();
        boolean inspectMaterials = Screen.hasShiftDown() && (inspection != null || inspectionRow != null) && !REORDER.active() && !GESTURES.active();
        var inventoryView = Screen.hasShiftDown() && !REORDER.active() && !GESTURES.active() ? new BookmarkInventoryView() : null;
        var inventoryAmounts = new HashMap<BookmarkLayout.Cell, BookmarkInventoryView.Amount>();
        for (var cell : l.cells) {
            if (customBackground && BookmarkClientConfig.SIDEBAR_OPACITY.get() > 0) fill(g, cell.bounds(),
                    ((int) Math.round(BookmarkClientConfig.SIDEBAR_OPACITY.get() * 64) << 24) | 0x2F2F2F);
            if (GESTURES.dragging() && cell.recipeIndex() >= GESTURES.first() && cell.recipeIndex() <= GESTURES.last())
                fill(g, cell.bounds(), 0x605588BB);
            if (cell.blank()) continue;
            var record = collection().recipes.get(cell.recipeIndex()); var slot = record.icons().get(cell.icon());
            var calculation = l.calculations.get(record.id());
            if (inspectMaterials && Objects.equals(inspectionGroup, cell.group()) && calculation != null && calculation.valid()) {
                int color = switch (calculation.kinds().get(slot.id)) {
                    case BASE_INPUT -> 0x6055DD77;
                    case RESULT -> 0x6044DDEE;
                    case REMAINDER -> 0x60BB66DD;
                    case CHANCE -> 0x60DD9955;
                    case INTERMEDIATE -> 0;
                };
                if (color != 0) fill(g, cell.bounds(), color);
            }
            var ingredient = ingredient(record, cell);
            var inventoryAmount = inventoryView == null ? null : inventoryView.amount(slot, ingredient, cell.amount());
            if (inventoryAmount != null) inventoryAmounts.put(cell, inventoryAmount);
            if (ingredient.isEmpty()) g.drawString(font, "?", cell.bounds().x() + 5, cell.bounds().y() + 5, 0xFF9999);
            else if (inventoryAmount != null && inventoryAmount.covered()) {
                // Flush both sides of the alpha change: EMI renderers can buffer item artwork.
                g.flush(); g.setColor(1, 1, 1, 0.5f);
                try { ingredient.render(g, cell.artwork().x(), cell.artwork().y(), partialTick, EmiIngredient.RENDER_ICON); g.flush(); }
                finally { g.setColor(1, 1, 1, 1); }
            } else ingredient.render(g, cell.artwork().x(), cell.artwork().y(), partialTick, EmiIngredient.RENDER_ICON);
            boolean primary = slot.id.equals(record.primaryOutput());
            if (primary && BookmarkClientConfig.MACHINE_BADGE.get()) {
                var recipe = BookmarkCapture.resolve(record);
                if (recipe != null && recipe.getCategory().icon != null) {
                    var badge = cell.badge();
                    g.pose().pushPose(); g.pose().translate(badge.x(), badge.y(), 0); g.pose().scale(0.375f, 0.375f, 1);
                    recipe.getCategory().render(g, 0, 0, partialTick); g.pose().popPose();
                }
            }
        }
        // EMI item/fluid renderers use their own depth offsets and buffered artwork.
        // Flush them, then put every label above the full artwork depth range.
        g.flush();
        g.pose().pushPose(); g.pose().translate(0, 0, 300);
        for (var cell : l.cells) {
            if (cell.blank()) continue;
            var record = collection().recipes.get(cell.recipeIndex()); var slot = record.icons().get(cell.icon());
            boolean primary = slot.id.equals(record.primaryOutput());
            var inventoryAmount = inventoryAmounts.get(cell);
            long shownAmount = inventoryAmount == null ? cell.amount() : inventoryAmount.remaining();
            var unit = BookmarkQuantities.displayUnit(shownAmount, slot.variant().unit());
            var calculation = l.calculations.get(record.id());
            String prefix = calculation != null && calculation.valid() && calculation.kinds().get(slot.id) == BookmarkChain.Kind.CHANCE ? "\u2264" : calculation != null && calculation.valid() && calculation.expected() ? "~" : "";
            if (shownAmount == 0) prefix = "";
            if (inspectMaterials && Objects.equals(inspectionGroup, cell.group()) && calculation != null && calculation.valid()) {
                int color = switch (calculation.kinds().get(slot.id)) {
                    case BASE_INPUT -> 0x55DD77;
                    case RESULT -> 0x44DDEE;
                    case REMAINDER -> 0xBB66DD;
                    case CHANCE -> 0xDD9955;
                    case INTERMEDIATE -> 0;
                };
                if (color != 0) {
                    fill(g, cell.artwork(), 0x30000000 | color);
                    var r = cell.bounds();
                    g.fill(r.x(), r.y(), r.x() + BookmarkLayout.CELL, r.y() + 1, 0xFF000000 | color);
                    g.fill(r.x(), r.y(), r.x() + 1, r.y() + 16, 0xFF000000 | color);
                    g.fill(r.x() + BookmarkLayout.CELL - 1, r.y(), r.x() + BookmarkLayout.CELL, r.y() + 16, 0xFF000000 | color);
                }
            }
            String quantityPrefix = prefix;
            String quantity = quantityPrefix + (shownAmount == 0 ? "0" + unit.symbol() : BookmarkQuantities.overlayLabel(shownAmount, unit,
                    text -> font.width(quantityPrefix + text) * 0.5f <= cell.quantity().width()));
            boolean reduced = inventoryAmount != null && inventoryAmount.reduced();
            if (record.counted() || reduced) quantityLabel(g, quantity, cell.quantity(), reduced ? 0xFFFF00 : 0xFFFFFF);
            if (record.counted() && primary && BookmarkClientConfig.BATCH_LABEL.get())
                halfSizeLabel(g, "\u00d7" + BookmarkQuantities.overlayNumber(displayBatches(l, record), BookmarkSlot.Unit.ITEM), cell.batch().x() * 2, cell.batch().y() * 2);
            if (BookmarkClientConfig.ROLE_ACCENTS.get()) {
                if (slot.role == BookmarkSlot.Role.OUTPUT) fill(g, cell.accent(), 0xFF719BC7);
                else if (slot.role == BookmarkSlot.Role.INPUT) fill(g, cell.accent(), 0xFF75B786);
                else if (slot.role == BookmarkSlot.Role.CATALYST) fill(g, cell.accent(), 0xFFBCA76A);
            }
        }
        if (REORDER.active()) {
            for (var cell : l.cells) if (REORDER.members().contains(cell.recipe())) fill(g, cell.bounds(), 0xA0202020);
            if (lastDrop != null && dropAllowed) {
                var marker = l.marker(lastDrop.target(), lastDrop.after(), REORDER.atomicTargets());
                if (marker != null) fill(g, marker, 0xFF88CCFF);
            }
        }
        g.flush(); g.pose().popPose();
        for (int i = 0; i < l.rows.size(); i++) {
            var row = l.rows.get(i); var group = collection().groupById(row.group()); if (group == null) continue;
            int color = group.chain ? 0xFF55DD77 : 0xFFAAAAAA;
            var bracket = l.bracket(i);
            fill(g, bracket.stem(), color);
            if (bracket.top() != null) fill(g, bracket.top(), color);
            if (bracket.bottom() != null) fill(g, bracket.bottom(), color);
        }
        double mx = mouseX, my = mouseY;
        if (REORDER.active()) {
            dragPreview(g, l, mouseX, mouseY, partialTick);
        } else if (!GESTURES.active() && pressedRecipe == null && pressedGroup == null && l.bounds.contains(mx, my)) tooltip(g, l, (int) mx, (int) my);
        g.pose().popPose();
    }
    private static void dragPreview(GuiGraphics g, BookmarkLayout l, int mouseX, int mouseY, float partialTick) {
        record Icon(BookmarkCollection.Recipe record, BookmarkProjection.Slot projected) {}
        boolean groupPreview = pressedGroup != null;
        var icons = new ArrayList<Icon>();
        for (var record : REORDER.records()) {
            var slots = BookmarkProjection.slots(record, BookmarkClientConfig.COMBINE_INPUTS.get(), l.calculations.get(record.id()));
            if (groupPreview) icons.add(new Icon(record, slots.get(0)));
            else {
                int selected = 0;
                for (int i = 0; i < slots.size(); i++) if (record.icons().get(slots.get(i).representative()).id.equals(pressedSlot)) selected = i;
                for (int i = 0; i < slots.size(); i++) icons.add(new Icon(record, slots.get((i + selected) % slots.size())));
            }
        }
        int shown = Math.min(icons.size(), 18), columns = groupPreview ? 1 : Math.min(shown, Math.min(6, l.columns));
        int width = columns * BookmarkLayout.CELL, height = ((shown + columns - 1) / columns) * BookmarkLayout.ROW;
        int x = Math.max(2, Math.min((int) (mouseX - grabX), Minecraft.getInstance().screen.width - width - 2));
        int y = Math.max(2, Math.min((int) (mouseY - grabY), Minecraft.getInstance().screen.height - height - 12));
        g.pose().pushPose(); g.pose().translate(0, 0, 800);
        g.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xD0202020);
        int border = dropAllowed ? 0xFF88CCFF : 0xFFCC8877;
        g.fill(x - 2, y - 2, x - 1, y + height + 2, border);
        g.fill(x - 2, y - 2, x + width + 2, y - 1, border);
        for (int i = 0; i < shown; i++) {
            var icon = icons.get(i); var slot = icon.record.icons().get(icon.projected.representative());
            var ingredient = BookmarkCapture.ingredient(slot, icon.record.batches()); ingredient.setAmount(icon.projected.amount());
            ingredient.render(g, x + i % columns * BookmarkLayout.CELL + 1, y + i / columns * BookmarkLayout.ROW, partialTick, EmiIngredient.RENDER_ICON);
        }
        g.flush(); g.pose().translate(0, 0, 300);
        for (int i = 0; i < shown; i++) {
            var icon = icons.get(i); var slot = icon.record.icons().get(icon.projected.representative());
            int ix = x + i % columns * BookmarkLayout.CELL + 1, iy = y + i / columns * BookmarkLayout.ROW;
            if (icon.record.counted()) {
                var calculation = l.calculations.get(icon.record.id());
                String prefix = calculation != null && calculation.valid() && calculation.kinds().get(slot.id) == BookmarkChain.Kind.CHANCE ? "\u2264" : calculation != null && calculation.valid() && calculation.expected() ? "~" : "";
                String quantity = prefix + BookmarkQuantities.overlayLabel(icon.projected.amount(), BookmarkQuantities.displayUnit(icon.projected.amount(), slot.variant().unit()),
                        text -> Minecraft.getInstance().font.width(prefix + text) * 0.5 <= 16);
                quantityLabel(g, quantity, new BookmarkLayout.Rect(ix, iy + 8, 16, 8));
                if (slot.id.equals(icon.record.primaryOutput())) halfSizeLabel(g, "\u00d7" + displayBatches(l, icon.record), ix * 2, iy * 2);
            }
        }
        if (groupPreview || icons.size() > shown) halfSizeLabel(g, groupPreview ? icons.size() + (icons.size() == 1 ? " recipe" : " recipes") : "+" + (icons.size() - shown), x * 2, (y + height + 3) * 2);
        g.pose().popPose();
    }
    private static void tooltip(GuiGraphics g, BookmarkLayout l, int x, int y) {
        var font = Minecraft.getInstance().font;
        if (l.treeImport.contains(x, y)) {
            g.renderTooltip(font, List.of(Component.literal("Import current EMI tree into a new group"),
                    Component.literal("Shift+C reopens saved routes for editing")), Optional.empty(), x, y); return;
        }
        if (l.options.contains(x, y)) {
            g.renderTooltip(font, Component.translatable("emibookmarks.options.title"), x, y); return;
        }
        var gutter = l.gutter(x, y);
        boolean label = l.label.contains(x, y);
        if (gutter != null || label) {
            if (!Screen.hasAltDown()) { g.renderTooltip(font, BookmarkHelp.hint(), x, y); return; }
            var group = gutter == null ? null : collection().groupById(gutter.group());
            var lines = new ArrayList<Component>(); lines.add(BookmarkHelp.title());
            boolean enabled = group == null ? collection().chain : group.chain;
            if (group != null || label) {
                var mode = group == null ? collection().mode : group.mode;
                boolean collapsed = group == null ? collection().collapsed : group.collapsed;
                lines.add(BookmarkHelp.action("LMB", mode == BookmarkCollection.Mode.COMPACT ? "Use recipe rows" : "Use compact layout"));
                lines.add(BookmarkHelp.action("RMB", enabled ? "Disable chain calculation" : "Enable chain calculation"));
                lines.add(BookmarkHelp.action("Alt + LMB", collapsed ? "Expand bracket" : "Collapse bracket"));
            }
            if (!label) {
                lines.add(BookmarkHelp.action("LMB drag", "Group whole recipes"));
                lines.add(BookmarkHelp.action("RMB drag", "Exclude recipes"));
                if (group != null) lines.add(BookmarkHelp.action("Shift + LMB drag", "Move whole group"));
            }
            lines.add(BookmarkHelp.action("Ctrl + Wheel", enabled ? "Change requested crafts" : "Change batches"));
            lines.add(BookmarkHelp.action("Shift + " + bookmarkKey(), group == null ? "Remove ungrouped bookmarks" : "Remove whole group"));
            if (enabled) {
                var calculation = BookmarkChains.scope(collection(), group == null ? null : group.id);
                if (!calculation.valid()) lines.add(Component.literal("Chain unavailable: " + calculation.issue()).withStyle(net.minecraft.ChatFormatting.RED));
                else if (calculation.expected()) lines.add(Component.literal("~ Expected yield; chance results are not guaranteed").withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            g.renderTooltip(font, lines, Optional.empty(), x, y); return;
        }
        var cell = l.cell(x, y);
        if (cell != null && !cell.blank()) {
            var record = collection().recipes.get(cell.recipeIndex()); var slot = record.icons().get(cell.icon());
            var ingredient = ingredient(record, cell);
            var lines = new ArrayList<Component>();
            if (!ingredient.isEmpty() && !ingredient.getEmiStacks().isEmpty()) lines.addAll(ingredient.getEmiStacks().get(0).getTooltipText());
            else lines.add(Component.literal(slot.variant().label() + " (unavailable)"));
            if (Screen.hasAltDown()) {
                var group = collection().groupOf(record.id());
                var calculation = l.calculations.get(record.id());
                lines.add(BookmarkHelp.title());
                lines.add(BookmarkHelp.action("LMB", record.isRecipe() && slot.role == BookmarkSlot.Role.OUTPUT ? "Open saved recipe" : "Show recipes"));
                lines.add(BookmarkHelp.action("RMB", "Show uses"));
                if (BookmarkRecipeFill.available(record, slot, ingredient))
                    lines.add(BookmarkHelp.action("Shift + LMB", "Fill crafting grid (as many as fit)"));
                lines.add(BookmarkHelp.action("Shift", "Show inventory shortages; fade covered items"));
                if (BookmarkFolding.available(collection(), record, slot.id))
                    lines.add(BookmarkHelp.action("Alt + LMB", record.collapsed() ? "Unfold this recipe" : "Fold this recipe"));
                lines.add(BookmarkHelp.action(bookmarkKey(), record.isRecipe() ? "Remove recipe" : "Remove item"));
                lines.add(BookmarkHelp.action("Ctrl + Shift + C (in tree)", "Save as new recipe group"));
                lines.add(BookmarkHelp.action(BookmarkKeys.TREE.getTranslatedKeyMessage().getString(), "Reopen saved recipe tree"));
                lines.add(BookmarkHelp.action("Shift + " + bookmarkKey(), group == null ? "Remove ungrouped bookmarks" : "Remove group"));
                lines.add(BookmarkHelp.action("Shift + LMB drag", group != null && group.members.size() == 1 ? "Move this bracket" : group != null ? "Reorder recipe in group" : "Move bookmark"));
                lines.add(BookmarkHelp.action("Ctrl + Wheel", record.isRecipe() ? "Change requested crafts" : "Change quantity"));
                lines.add(BookmarkHelp.action("Ctrl + Alt + Wheel", "Change in steps of 64"));
                if (slot.alternatives.size() > 1) lines.add(BookmarkHelp.action("Shift + Wheel", "Cycle accepted ingredient"));
                if (calculation != null) {
                    if (calculation.valid()) { lines.add(BookmarkHelp.action("Shift", "Highlight inputs / results / surplus")); if (calculation.expected()) lines.add(Component.literal("~ Expected yield; chance results are not guaranteed").withStyle(net.minecraft.ChatFormatting.GOLD)); }
                    else lines.add(Component.literal("Chain unavailable: " + calculation.issue()).withStyle(net.minecraft.ChatFormatting.RED));
                }
            } else lines.add(BookmarkHelp.hint());
            BookmarkRecipePreview.render(g, record, slot, ingredient, lines, x, y); return;
        }
        if (l.previous.contains(x, y) || l.next.contains(x, y)) {
            var lines = Screen.hasAltDown() ? List.of(BookmarkHelp.action("LMB", "Change page"), BookmarkHelp.action("Drag + Hover", "Turn page after a short pause")) : List.of(BookmarkHelp.hint());
            g.renderTooltip(font, lines, Optional.empty(), x, y); return;
        }
        if (l.footer.contains(x, y) && l.calculations.values().stream().anyMatch(result -> !result.valid())) {
            List<Component> lines = Screen.hasAltDown() ? List.of(Component.literal("Chain unavailable. Hover its bracket for the reason.").withStyle(net.minecraft.ChatFormatting.RED)) : List.of(BookmarkHelp.hint());
            g.renderTooltip(font, lines, Optional.empty(), x, y);
        }
    }
    /** Quantities and multipliers share the exact same half-size Minecraft font; neither can auto-shrink. */
    private static void quantityLabel(GuiGraphics g, String text, BookmarkLayout.Rect area) {
        quantityLabel(g, text, area, 0xFFFFFF);
    }
    private static void quantityLabel(GuiGraphics g, String text, BookmarkLayout.Rect area, int color) {
        halfSizeLabel(g, text, area.x() * 2, (area.y() + area.height()) * 2 - 8, color);
    }
    private static void halfSizeLabel(GuiGraphics g, String text, int x, int y) {
        halfSizeLabel(g, text, x, y, 0xFFFFFF);
    }
    private static void halfSizeLabel(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose(); g.pose().translate(0, 0, 1); g.pose().scale(0.5f, 0.5f, 1);
        g.drawString(Minecraft.getInstance().font, text, x, y, color, true); g.pose().popPose();
    }
    private static void fill(GuiGraphics g, BookmarkLayout.Rect r, int color) {
        g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), color);
    }
}
