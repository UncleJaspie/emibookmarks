package com.unclejaspie.emibookmarks.bookmark;

import java.util.*;

/** Transactional live drag layout. Only release commits; cancellation leaves the source untouched. */
public final class BookmarkReorder {
    private BookmarkCollection source, preview;
    private UUID moving, ownership;
    private UUID movingGroup;
    private Set<UUID> members = Set.of();
    public boolean active() { return source != null; }
    public UUID moving() { return moving; }
    public boolean groupMove() { return movingGroup != null; }
    public boolean atomicTargets() { return groupMove() || ownership == null; }
    public Set<UUID> members() { return members; }
    public List<BookmarkCollection.Recipe> records() { return active() ? preview.recipes.stream().filter(r -> members.contains(r.id())).toList() : List.of(); }
    public BookmarkCollection view(BookmarkCollection fallback) { return active() ? preview : fallback; }
    public void start(BookmarkCollection collection, UUID id) {
        if (collection.recipes.stream().noneMatch(r -> r.id().equals(id))) throw new IllegalArgumentException("Missing dragged record");
        var existing = collection.groupOf(id);
        // A one-record bracket has no internal reorder destination: its icon moves the bracket intact.
        if (existing != null && existing.members.size() == 1) { startGroup(collection, existing.id); return; }
        cancel(); source = collection; moving = id;
        var group = collection.groupOf(id); ownership = group == null ? null : group.id;
        members = Set.of(id);
        copy(collection);
    }
    public void startGroup(BookmarkCollection collection, UUID groupId) {
        var group = collection.groupById(groupId);
        if (group == null) throw new IllegalArgumentException("Missing dragged group");
        cancel(); source = collection; movingGroup = group.id; ownership = group.id;
        members = Set.copyOf(group.members); moving = group.members.get(0);
        copy(collection);
    }
    private void copy(BookmarkCollection collection) {
        preview = new BookmarkCollection(collection.id, collection.name);
        preview.mode = collection.mode; preview.chain = collection.chain; preview.collapsed = collection.collapsed;
        preview.recipes.addAll(collection.recipes);
        preview.pageSections.putAll(collection.pageSections);
        for (var old : collection.groups) {
            var copy = new BookmarkCollection.Group(old.id, old.members);
            copy.mode = old.mode; copy.chain = old.chain; copy.collapsed = old.collapsed; copy.treeGoal = old.treeGoal; preview.groups.add(copy);
        }
    }
    public boolean insert(UUID target, boolean after) {
        if (!active()) return false;
        if (members.contains(target)) return true;
        var targetGroup = preview.groupOf(target);
        // Reorder members inside their group. Ungrouped entries stop at group boundaries.
        if (!groupMove() && ownership != null && (targetGroup == null || !ownership.equals(targetGroup.id))) return false;
        if ((groupMove() || ownership == null) && targetGroup != null)
            target = targetGroup.members.get(after ? targetGroup.members.size() - 1 : 0);
        final UUID targetId = target;
        var entries = records();
        var destination = preview.recipes.stream().filter(r -> r.id().equals(targetId)).findFirst().orElse(null);
        if (destination == null || members.contains(destination.id())) return false;
        preview.recipes.removeAll(entries);
        preview.recipes.addAll(preview.recipes.indexOf(destination) + (after ? 1 : 0), entries);
        if (groupMove() || ownership == null) for (var entry : entries)
            preview.pageSections.put(entry.id(), preview.pageSections.getOrDefault(destination.id(), 0));
        for (var group : preview.groups) {
            var members = preview.recipes.stream().map(BookmarkCollection.Recipe::id).filter(group.members::contains).toList();
            group.members.clear(); group.members.addAll(members);
        }
        return true;
    }
    public boolean commit() {
        if (!active()) return false;
        boolean changed = !source.recipes.equals(preview.recipes) || !source.pageSections.equals(preview.pageSections);
        if (changed) {
            source.recipes.clear(); source.recipes.addAll(preview.recipes);
            source.pageSections.clear(); source.pageSections.putAll(preview.pageSections);
            for (var group : source.groups) {
                group.members.clear(); group.members.addAll(preview.groupById(group.id).members);
            }
        }
        cancel(); return changed;
    }
    public void cancel() { source = null; preview = null; moving = null; ownership = null; movingGroup = null; members = Set.of(); }
    public boolean insertPage(BookmarkLayout layout) {
        if (!active() || (!groupMove() && ownership != null)) return false;
        layout.rememberPages(preview);
        int section = layout.destinationSection(preview);
        var entries = records();
        preview.recipes.removeAll(entries);
        int at = 0;
        while (at < preview.recipes.size() && preview.pageSections.getOrDefault(preview.recipes.get(at).id(), 0) <= section) at++;
        preview.recipes.addAll(at, entries);
        for (var entry : entries) preview.pageSections.put(entry.id(), section);
        return true;
    }
}
