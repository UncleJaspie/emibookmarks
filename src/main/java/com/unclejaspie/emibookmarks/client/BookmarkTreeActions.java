package com.unclejaspie.emibookmarks.client;

import com.unclejaspie.emibookmarks.bookmark.*;
import com.unclejaspie.emibookmarks.client.emi.BookmarkDock;
import com.unclejaspie.emibookmarks.client.emi.BookmarkRecipeContext;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.bom.*;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Imports EMI-selected routes without replacing BoM.tree, writing defaults or touching favorites. */
public final class BookmarkTreeActions {
    private record Route(EmiRecipe recipe, EmiIngredient target, MaterialNode node, BookmarkCollection.Recipe saved, int primaryHint) {
        Route(EmiRecipe recipe, EmiIngredient target, MaterialNode node, BookmarkCollection.Recipe saved) { this(recipe, target, node, saved, -1); }
    }
    private record Editing(MaterialTree tree, UUID collection, UUID group, UUID recipe, UUID goal, String fingerprint) {}
    private static Editing editing;
    public static void clearSession() { editing = null; }

    public static void hovered(Screen screen, int x, int y, BookmarkCollection.Recipe saved, BookmarkSlot slot) {
        if (screen instanceof BoMScreen) { importCurrent(); return; }
        if (!BookmarkClientConfig.AUTO_TREE.get()) { openNative(screen, x, y, saved, slot); return; }
        run(() -> {
            if (screen instanceof BoMScreen) return currentPlan();
            var source = new Routes(null);
            Route root;
            if (saved != null && saved.isRecipe() && slot.role == BookmarkSlot.Role.OUTPUT) {
                var recipe = BookmarkCapture.resolve(saved);
                if (recipe == null) throw new IllegalArgumentException("Saved recipe is unavailable; its snapshot was retained");
                root = new Route(recipe, BookmarkCapture.ingredient(slot, 1), null, copy(saved, slot.id));
            } else {
                var ingredient = saved == null ? EmiApi.getHoveredStack(x, y, true).getStack() : BookmarkCapture.ingredient(slot, saved.batches());
                var visible = saved == null ? BookmarkRecipeContext.at(screen, x, y) : null;
                if (visible != null) {
                    var output = BookmarkRecipeContext.outputAt(screen, x, y, visible);
                    if (output.isEmpty() && visible.getOutputs().size() != 1)
                        throw new IllegalArgumentException("Hover the intended recipe output before building its tree");
                    root = new Route(visible, output.isEmpty() ? visible.getOutputs().get(0) : output, null, null);
                } else {
                    var recipe = source.resolve(ingredient);
                    if (recipe == null) throw new IllegalArgumentException("No EMI recipe found; choose a recipe or set its heart default");
                    root = new Route(recipe, ingredient, null, null);
                }
            }
            var rootRecord = source.capture(root);
            if (saved != null && !saved.isRecipe()) {
                long requested = slot.amount(saved.batches());
                long yield = rootRecord.icons().stream().filter(s -> s.id.equals(rootRecord.primaryOutput())).findFirst().orElseThrow().variant().amount();
                long batches = requested / yield + (requested % yield == 0 ? 0 : 1);
                if (batches > 1) rootRecord.adjustBatches(1, batches - 1);
                if (rootRecord.batches() != batches) throw new IllegalArgumentException("Requested tree quantity exceeds the safe range");
            }
            return BookmarkTreePlanner.build(root, source);
        });
    }

    public static void importCurrent() {
        if (editing == null || editing.tree != BoM.tree || !editing.collection.equals(BookmarkWorkspace.collection().id)) { run(BookmarkTreeActions::currentPlan); return; }
        try {
            var context = editing; var plan = currentPlan(); var tree = new BookmarkTreeData.Tree(plan.recipes(), plan.group());
            verify(context);
            Minecraft.getInstance().setScreen(new BookmarkTreeSaveScreen(Minecraft.getInstance().screen, tree.recipes().size(),
                    () -> install(tree, context, true), () -> install(tree, context, false)));
        } catch (RuntimeException | LinkageError failure) { BookmarkWorkspace.message("Could not save edited tree: " + failure.getMessage()); }
    }
    public static void importNew() {
        var collection = BookmarkWorkspace.collection(); int before = collection.groups.size();
        run(BookmarkTreeActions::currentPlan);
        if (collection.groups.size() > before) {
            var group = collection.groups.get(collection.groups.size() - 1);
            editing = new Editing(BoM.tree, collection.id, group.id, null, group.treeGoal,
                    BookmarkTreeData.fingerprint(BookmarkTreeData.selected(collection, group.id, group.treeGoal)));
        }
    }

    public static void openNative(Screen screen, int x, int y, BookmarkCollection.Recipe saved, BookmarkSlot slot) {
        try {
            EmiRecipe recipe; BookmarkTreeData.Tree data = null; UUID targetGroup = null, targetRecipe = null;
            if (saved != null && (saved.isRecipe() || BookmarkWorkspace.collection().groupOf(saved.id()) != null)) {
                var collection = BookmarkWorkspace.collection(); var group = collection.groupOf(saved.id());
                if (group != null) {
                    data = BookmarkTreeData.selected(collection, group.id, group.treeGoal); targetGroup = group.id;
                } else {
                    data = BookmarkTreeData.selected(collection, null, saved.id());
                    if (group == null) targetRecipe = saved.id();
                }
                recipe = BookmarkCapture.resolve(data.goal());
            }
            else {
                recipe = BookmarkRecipeContext.at(screen, x, y);
                var viewerItem = BookmarkRecipeContext.ingredientAt(screen, x, y);
                if (recipe == null || (!viewerItem.isEmpty() && BookmarkRecipeContext.outputAt(screen, x, y, recipe).isEmpty()))
                    recipe = BookmarkRecipeDefaults.preferred(!viewerItem.isEmpty() ? viewerItem : saved == null
                            ? EmiApi.getHoveredStack(x, y, true).getStack() : BookmarkCapture.ingredient(slot, 1));
            }
            if (recipe != null) {
                if (data == null) {
                    var target = BookmarkRecipeContext.outputAt(screen, x, y, recipe);
                    if (target.isEmpty()) target = BookmarkRecipeContext.ingredientAt(screen, x, y);
                    if (target.isEmpty()) target = saved == null ? EmiApi.getHoveredStack(x, y, true).getStack() : BookmarkCapture.ingredient(slot, 1);
                    if (target.isEmpty() && recipe.getOutputs().size() == 1) target = recipe.getOutputs().get(0);
                    int primary = BookmarkCapture.outputIndex(recipe, target);
                    var outputs = recipe.getOutputs().stream().filter(s -> !s.isEmpty()).toList();
                    for (int i = 0; primary < 0 && i < outputs.size(); i++) for (var option : target.getEmiStacks())
                        if (outputs.get(i).isEqual(option) && Objects.equals(outputs.get(i).getNbt(), option.getNbt())) { primary = i; break; }
                    if (primary < 0) throw new IllegalArgumentException("Hover the intended output before opening this tree");
                    var root = BookmarkCapture.recipe(recipe, primary, target);
                    if (saved != null && !saved.isRecipe()) {
                        long count = slot.amount(saved.batches()), yield = outputs.get(primary).getAmount();
                        long batches = count / yield + (count % yield == 0 ? 0 : 1);
                        if (batches > 1) root.adjustBatches(1, batches - 1);
                        if (root.batches() != batches) throw new IllegalArgumentException("Goal quantity exceeds the supported range");
                    }
                    var group = new BookmarkCollection.Group(UUID.randomUUID(), List.of(root.id())); group.treeGoal = root.id();
                    data = new BookmarkTreeData.Tree(List.of(root), group);
                }
                var previousTree = BoM.tree; var previousEditing = editing;
                BoM.tree = controlledTree(data);
                editing = targetGroup != null || targetRecipe != null ? new Editing(BoM.tree, BookmarkWorkspace.collection().id,
                        targetGroup, targetRecipe, data.goal().id(), BookmarkTreeData.fingerprint(data)) : null;
                if (targetGroup != null) {
                    try {
                        var remaining = new ArrayList<>(currentPlan().recipes());
                        for (var record : data.recipes()) {
                            var match = remaining.stream().filter(r -> r.isRecipe() == record.isRecipe()
                                    && Objects.equals(r.recipeKey(), record.recipeKey())).findFirst().orElse(null);
                            if (match == null) throw new IllegalArgumentException("This group contains disconnected or conflicting routes; split them into separate recipe groups before editing");
                            remaining.remove(match);
                        }
                    } catch (RuntimeException | LinkageError failure) {
                        BoM.tree = previousTree; editing = previousEditing; throw failure;
                    }
                }
            }
            else if (saved != null || BoM.tree == null) { BookmarkWorkspace.message("No recipe tree found; hover an item with a recipe"); return; }
            EmiApi.viewRecipeTree();
        } catch (RuntimeException | LinkageError failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not open EMI recipe tree", failure);
            BookmarkWorkspace.message("Could not open this EMI recipe tree: " + failure.getMessage());
        }
    }

    public static BookmarkTreePlanner.Plan currentPlan() {
        var tree = BoM.tree;
        if (tree == null || tree.goal == null || tree.goal.recipe == null) throw new IllegalArgumentException("Open an EMI recipe tree first");
        var source = new Routes(tree);
        var root = source.route(tree.goal);
        if (root == null) throw new IllegalArgumentException("The EMI tree has no craftable goal route");
        if (editing != null && editing.tree == tree && editing.collection.equals(BookmarkWorkspace.collection().id)) {
            var original = BookmarkWorkspace.collection().recipes.stream().filter(r -> r.id().equals(editing.goal)).findFirst().orElse(null);
            if (original != null && original.recipeKey() != null && root.recipe.getId() != null && original.recipeKey().equals(root.recipe.getId().toString())) {
                int index = 0;
                for (var s : original.icons()) if (s.role == BookmarkSlot.Role.OUTPUT) {
                    if (s.id.equals(original.primaryOutput())) { root = new Route(root.recipe, root.target, root.node, null, index); break; } index++;
                }
            }
        }
        var record = source.capture(root);
        if (tree.batches < 1) throw new IllegalArgumentException("Invalid EMI tree quantity");
        if (tree.batches > 1) record.adjustBatches(1, tree.batches - 1);
        if (record.batches() != tree.batches) throw new IllegalArgumentException("EMI tree quantity exceeds the safe range");
        return BookmarkTreePlanner.build(root, source);
    }

    private static MaterialTree controlledTree(BookmarkTreeData.Tree data) {
        var goal = data.goal(); var goalRecipe = BookmarkCapture.resolve(goal);
        if (goalRecipe == null) throw new IllegalArgumentException("Saved goal recipe is unavailable");
        var tree = new MaterialTree(goalRecipe) {
            @Override public EmiRecipe getRecipe(EmiIngredient ingredient) { return resolutions.get(ingredient); }
        };
        var producers = new LinkedHashMap<EmiIngredient, EmiRecipe>();
        for (var record : data.recipes()) if (record.isRecipe()) {
            var recipe = BookmarkCapture.resolve(record);
            if (recipe == null) throw new IllegalArgumentException("A saved tree route is unavailable: " + record.recipeKey());
            for (var s : record.icons()) if (s.role == BookmarkSlot.Role.OUTPUT)
                producers.putIfAbsent(BookmarkCapture.ingredient(s, 1).setAmount(1), recipe);
        }
        for (var record : data.recipes()) if (record.isRecipe()) {
            var primary = record.icons().stream().filter(s -> s.id.equals(record.primaryOutput())).findFirst().orElseThrow();
            producers.put(BookmarkCapture.ingredient(primary, 1).setAmount(1), BookmarkCapture.resolve(record));
        }
        tree.resolutions.putAll(producers);
        for (var record : data.recipes()) if (record.isRecipe()) {
            var recipe = BookmarkCapture.resolve(record); var inputs = recipe.getInputs().stream().filter(i -> !i.isEmpty()).toList(); int i = 0;
            for (var s : record.icons()) if (s.role == BookmarkSlot.Role.INPUT) {
                var input = inputs.get(i++); var selected = BookmarkCapture.ingredient(s, 1).setAmount(1);
                if (input.getEmiStacks().size() > 1) tree.resolutions.put(input, new EmiResolutionRecipe(input, selected.getEmiStacks().get(0)));
                tree.resolutions.putIfAbsent(selected, null);
            }
        }
        var primary = goal.icons().stream().filter(s -> s.id.equals(goal.primaryOutput())).findFirst().orElseThrow();
        tree.goal = new MaterialNode(BookmarkCapture.ingredient(primary, 1)); tree.goal.defineRecipe(goalRecipe);
        tree.batches = goal.batches(); tree.recalculate(); return tree;
    }
    private static void verify(Editing context) {
        var c = BookmarkWorkspace.collection();
        if (!context.collection.equals(c.id) || !context.fingerprint.equals(BookmarkTreeData.fingerprint(BookmarkTreeData.selected(c, context.group, context.goal))))
            throw new IllegalArgumentException("Saved tree changed while it was open; reopen it before updating");
    }
    public static void installShared(BookmarkTreeData.Tree tree) { install(tree, null, false); }
    private static void install(BookmarkTreeData.Tree tree, Editing context, boolean update) {
        if (!BookmarkWorkspace.writable()) { BookmarkWorkspace.message("Editing unavailable; original bookmarks preserved"); return; }
        try {
            if (update) verify(context);
            var c = BookmarkWorkspace.collection();
            var prospective = BookmarkTreeData.stage(c, tree, update ? context.group : null, update ? context.recipe : null);
            var collections = new ArrayList<>(BookmarkWorkspace.document().collections()); collections.set(collections.indexOf(c), prospective);
            var document = new BookmarkCodec.Document(collections, BookmarkWorkspace.document().active());
            var json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(BookmarkCodec.encode(document));
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > BookmarkTreeData.MAX_BYTES) throw new IllegalArgumentException("Tree would exceed the save-size limit");
            BookmarkCodec.decode(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
            c.recipes.clear(); c.recipes.addAll(prospective.recipes); c.groups.clear(); c.groups.addAll(prospective.groups);
            BookmarkWorkspace.changed();
            if (context != null) {
                var installed = update && context.group != null ? c.groupById(context.group) : c.groups.get(c.groups.size() - 1);
                editing = new Editing(BoM.tree, c.id, installed.id, null, installed.treeGoal,
                        BookmarkTreeData.fingerprint(BookmarkTreeData.selected(c, installed.id, installed.treeGoal)));
            }
            BookmarkWorkspace.message((update ? "Updated" : "Imported") + " recipe tree: " + tree.recipes().size() + " recipes");
        } catch (RuntimeException | LinkageError failure) { BookmarkWorkspace.message("Tree was not saved: " + failure.getMessage()); }
    }

    private static BookmarkCollection.Recipe copy(BookmarkCollection.Recipe old, UUID selectedOutput) {
        var slots = new ArrayList<BookmarkSlot>(); UUID primary = null;
        for (var s : old.icons()) {
            var next = new BookmarkSlot(UUID.randomUUID(), s.role, s.alternatives, s.selected()); slots.add(next);
            if (s.id.equals(selectedOutput)) primary = next.id;
        }
        return new BookmarkCollection.Recipe(UUID.randomUUID(), old.isRecipe(), old.recipeKey(), slots, old.batches(), primary);
    }

    private static void run(java.util.function.Supplier<BookmarkTreePlanner.Plan> action) {
        if (!BookmarkWorkspace.writable()) { BookmarkWorkspace.message("Bookmark editing is unavailable; existing save preserved"); return; }
        try {
            var plan = action.get(); var collection = BookmarkWorkspace.collection(); var group = plan.group();
            // Validate the complete prospective document before either list is changed.
            var prospective = new BookmarkCollection(collection.id, collection.name);
            prospective.mode = collection.mode; prospective.chain = collection.chain; prospective.collapsed = collection.collapsed;
            prospective.recipes.addAll(collection.recipes); prospective.recipes.addAll(plan.recipes());
            prospective.groups.addAll(collection.groups); prospective.groups.add(group);
            var collections = new ArrayList<>(BookmarkWorkspace.document().collections());
            collections.set(collections.indexOf(collection), prospective);
            var json = BookmarkCodec.encode(new BookmarkCodec.Document(collections, BookmarkWorkspace.document().active()));
            if (new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16 * 1024 * 1024)
                throw new IllegalArgumentException("Imported tree would exceed the 16 MiB save limit");
            collection.recipes.addAll(plan.recipes()); collection.groups.add(group);
            BookmarkWorkspace.changed();
            try { BookmarkDock.show(); }
            catch (RuntimeException | LinkageError uiFailure) { com.mojang.logging.LogUtils.getLogger().warn("Tree saved but bookmark page could not be shown", uiFailure); }
            String message = "Imported " + plan.recipes().size() + " recipes into a new " + (group.chain ? "chain" : "recipe") + " group";
            if (plan.cycleStops() > 0) message += "; stopped " + plan.cycleStops() + " looping routes";
            if (!group.chain) message += "; calculation unavailable: " + plan.calculation().issue();
            BookmarkWorkspace.message(message);
        } catch (RuntimeException | LinkageError failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Recipe tree import failed; bookmarks unchanged", failure);
            BookmarkWorkspace.message("Could not import recipe tree: " + failure.getMessage());
        }
    }

    private static final class Routes implements BookmarkTreePlanner.Source<Route> {
        final MaterialTree tree;
        final Map<Route, BookmarkCollection.Recipe> captures = new IdentityHashMap<>();
        final Map<String, EmiRecipe> resolved = new HashMap<>();
        final long deadline = System.nanoTime() + 3_000_000_000L;
        Routes(MaterialTree tree) { this.tree = tree; }
        void checkTime() { if (System.nanoTime() > deadline) throw new IllegalArgumentException("Tree expansion took too long; set more EMI defaults"); }
        EmiRecipe resolve(EmiIngredient ingredient) {
            return resolve(ingredient, 0);
        }
        EmiRecipe resolve(EmiIngredient ingredient, int hops) {
            checkTime();
            if (hops > 32) throw new IllegalArgumentException("Looping EMI ingredient resolution");
            if (ingredient == null || ingredient.isEmpty()) return null;
            var recipe = tree == null ? BookmarkRecipeDefaults.preferred(ingredient) : tree.getRecipe(ingredient);
            if (recipe instanceof EmiResolutionRecipe resolution) return resolve(resolution.stack, hops + 1);
            return recipe;
        }
        Route route(MaterialNode node) {
            int hops = 0;
            while (node != null && node.recipe instanceof EmiResolutionRecipe) {
                if (++hops > 32) throw new IllegalArgumentException("Looping EMI ingredient resolution");
                node = node.children == null || node.children.isEmpty() ? null : node.children.get(0);
            }
            return node == null || node.recipe == null ? null : new Route(node.recipe, node.ingredient, node, null);
        }
        @Override public Object key(Route route) {
            var record = capture(route); var key = new ArrayList<Object>();
            key.add(route.recipe.getId() == null ? route.recipe : route.recipe.getId());
            // A multi-output route is captured once even when several products are demanded.
            for (var s : record.icons()) if (s.role != BookmarkSlot.Role.OUTPUT) key.add(List.of(s.role, s.variant()));
            return key;
        }
        @Override public BookmarkCollection.Recipe capture(Route route) {
            checkTime();
            return captures.computeIfAbsent(route, r -> {
                if (r.saved != null) return r.saved;
                int primary = r.primaryHint >= 0 ? r.primaryHint : BookmarkCapture.outputIndex(r.recipe, r.target);
                var outputs = r.recipe.getOutputs().stream().filter(s -> !s.isEmpty()).toList();
                for (int i = 0; primary < 0 && i < outputs.size(); i++) {
                    var out = outputs.get(i);
                    if (r.target.getEmiStacks().stream().anyMatch(t -> out.isEqual(t) && Objects.equals(out.getNbt(), t.getNbt()))) { primary = i; break; }
                }
                if (primary < 0) throw new IllegalArgumentException("Selected route does not produce the required item/variant");
                var record = BookmarkCapture.recipe(r.recipe, primary, r.target);
                var inputs = r.recipe.getInputs().stream().filter(i -> !i.isEmpty()).toList();
                int index = 0;
                for (var slot : record.icons()) if (slot.role == BookmarkSlot.Role.INPUT) {
                    var input = inputs.get(index++);
                    EmiRecipe choice = tree == null ? BookmarkRecipeDefaults.manual(input) : tree.getRecipe(input);
                    if (choice == null && tree == null) choice = BookmarkRecipeDefaults.preferred(input);
                    if (choice instanceof EmiResolutionRecipe resolution) {
                        if (!select(slot, resolution.stack)) throw new IllegalArgumentException("EMI selected an ingredient outside its accepted alternatives");
                    } else if (input.getEmiStacks().size() > 1) {
                        boolean selected = choice != null && choice.getOutputs().stream().anyMatch(out -> select(slot, out));
                        if (!selected && tree == null) for (var stack : input.getEmiStacks())
                            if (BookmarkRecipeDefaults.manual(stack) != null) { select(slot, stack); break; }
                    }
                }
                return record;
            });
        }
        boolean select(BookmarkSlot slot, EmiStack choice) {
            for (int i = 0; i < slot.alternatives.size(); i++) {
                var variant = new BookmarkSlot(slot.id, slot.role, slot.alternatives, i);
                var ingredient = BookmarkCapture.ingredient(variant, 1);
                if (ingredient.getEmiStacks().stream().anyMatch(s -> s.isEqual(choice) && Objects.equals(s.getNbt(), choice.getNbt()))) {
                    while (slot.selected() != i) slot.cycle(1); return true;
                }
            }
            return false;
        }
        @Override public List<Route> children(Route route) {
            checkTime(); var children = new ArrayList<Route>();
            if (route.node != null) {
                if (route.node.children != null) for (var node : route.node.children) {
                    var child = route(node); if (child != null) children.add(child);
                }
            } else for (var slot : capture(route).icons()) if (slot.role == BookmarkSlot.Role.INPUT) {
                var ingredient = BookmarkCapture.ingredient(slot, 1); String key = slot.variant().serialized();
                if (!resolved.containsKey(key)) resolved.put(key, resolve(ingredient));
                var recipe = resolved.get(key);
                if (recipe != null) children.add(new Route(recipe, ingredient, null, null));
            }
            return children;
        }
    }
}
