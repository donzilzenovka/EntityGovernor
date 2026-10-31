package com.zenovka.entitygovernor;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mod(modid = "entitygovernor", name = "EntityGovernor", version = "0.1.2", acceptableRemoteVersions = "*")
public class EntityGovernor {
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CensusCommand());
    }

    private static final class Entry {
        final String name;
        final String className;
        int count;
        Entry(String name, String className) {
            this.name = name;
            this.className = className;
        }
    }

    private static final class ItemEntry {
        final String id;
        final int metadata;
        final String javaClass;
        int entities;
        long items;
        ItemEntry(String id, int metadata, String javaClass) {
            this.id = id; this.metadata = metadata; this.javaClass = javaClass;
        }
    }

    public static class CensusCommand extends CommandBase {
        @Override public String getCommandName() { return "eg"; }
        @Override public String getCommandUsage(ICommandSender sender) {
            return "/eg census [export] [all|dimensionId] [top N]";
        }
        @Override public int getRequiredPermissionLevel() { return 2; }

        private void reply(ICommandSender sender, String message) {
            sender.addChatMessage(new ChatComponentText("[EntityGovernor] " + message));
        }

        @Override public void processCommand(ICommandSender sender, String[] args) {
            if (args.length == 0 || !"census".equalsIgnoreCase(args[0])) {
                reply(sender, getCommandUsage(sender));
                return;
            }
            boolean export = false;
            boolean all = false;
            boolean dimensionSpecified = false;
            int dimension = sender.getEntityWorld().provider.dimensionId;
            int top = 20;
            for (int i = 1; i < args.length; i++) {
                if ("export".equalsIgnoreCase(args[i])) {
                    export = true;
                } else if ("all".equalsIgnoreCase(args[i]) && !dimensionSpecified) {
                    all = true;
                } else if ("top".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    try {
                        top = Math.max(1, Math.min(200, Integer.parseInt(args[++i])));
                    } catch (NumberFormatException e) {
                        reply(sender, "Invalid top count");
                        return;
                    }
                } else if (!dimensionSpecified && !all) {
                    try {
                        dimension = Integer.parseInt(args[i]);
                        dimensionSpecified = true;
                    } catch (NumberFormatException e) {
                        reply(sender, getCommandUsage(sender));
                        return;
                    }
                } else {
                    reply(sender, getCommandUsage(sender));
                    return;
                }
            }

            StringBuilder report = new StringBuilder();
            report.append("EntityGovernor census — ")
                  .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()))
                  .append(System.lineSeparator());
            boolean found = false;
            int grandTotal = 0;
            int dimensions = 0;
            for (WorldServer world : DimensionManager.getWorlds()) {
                if (world == null || (!all && world.provider.dimensionId != dimension)) continue;
                found = true;
                dimensions++;
                Map<String, Entry> counts = new HashMap<String, Entry>();
                int total = 0;
                Map<String, ItemEntry> items = export ? new HashMap<String, ItemEntry>() : null;
                for (Object object : world.loadedEntityList) {
                    if (!(object instanceof Entity)) continue;
                    Entity entity = (Entity) object;
                    if (export && entity instanceof EntityItem) {
                        ItemStack stack = ((EntityItem) entity).getEntityItem();
                        if (stack != null && stack.getItem() != null) {
                            Item item = stack.getItem();
                            Object registered = Item.itemRegistry.getNameForObject(item);
                            String id = registered == null ? "unregistered" : registered.toString();
                            int metadata = stack.getItemDamage();
                            String javaClass = item.getClass().getName();
                            String key = id + ":" + metadata + ":" + javaClass;
                            ItemEntry e = items.get(key);
                            if (e == null) {
                                e = new ItemEntry(id, metadata, javaClass);
                                items.put(key, e);
                            }
                            e.entities++;
                            e.items += stack.stackSize;
                        }
                    }
                    String name;
                    if (entity instanceof EntityPlayer) {
                        name = "Player";
                    } else {
                        name = EntityList.getEntityString(entity);
                        if (name == null) name = "unregistered";
                    }
                    String className = entity.getClass().getName();
                    String key = name + "\u0000" + className;
                    Entry entry = counts.get(key);
                    if (entry == null) {
                        entry = new Entry(name, className);
                        counts.put(key, entry);
                    }
                    entry.count++;
                    total++;
                }
                grandTotal += total;
                List<Entry> sorted = new ArrayList<Entry>(counts.values());
                Collections.sort(sorted, new Comparator<Entry>() {
                    @Override public int compare(Entry a, Entry b) {
                        int byCount = Integer.compare(b.count, a.count);
                        if (byCount != 0) return byCount;
                        int byName = a.name.compareTo(b.name);
                        return byName != 0 ? byName : a.className.compareTo(b.className);
                    }
                });
                String heading = "Dimension " + world.provider.dimensionId + ": " + total
                        + " entities, " + sorted.size() + " types";
                report.append(System.lineSeparator()).append(heading).append(System.lineSeparator());
                if (!export) reply(sender, heading);
                int limit = export ? sorted.size() : Math.min(top, sorted.size());
                for (int i = 0; i < limit; i++) {
                    Entry entry = sorted.get(i);
                    String line = entry.count + "  " + entry.name + "  [" + entry.className + "]";
                    report.append(line).append(System.lineSeparator());
                    if (!export) sender.addChatMessage(new ChatComponentText("  " + entry.count + "  " + entry.name));
                }
                if (export) {
                    List<ItemEntry> sortedItems = new ArrayList<ItemEntry>(items.values());
                    Collections.sort(sortedItems, new Comparator<ItemEntry>() {
                        @Override public int compare(ItemEntry a, ItemEntry b) {
                            int c = Integer.compare(b.entities, a.entities);
                            if (c != 0) return c;
                            c = a.id.compareTo(b.id);
                            if (c != 0) return c;
                            c = Integer.compare(a.metadata, b.metadata);
                            return c != 0 ? c : a.javaClass.compareTo(b.javaClass);
                        }
                    });
                    int droppedEntities = 0;
                    long droppedQuantity = 0;
                    for (ItemEntry e : sortedItems) {
                        droppedEntities += e.entities;
                        droppedQuantity += e.items;
                    }
                    report.append(System.lineSeparator()).append("Dropped items — dimension ")
                          .append(world.provider.dimensionId).append(System.lineSeparator())
                          .append(droppedEntities).append(" item entities, ")
                          .append(droppedQuantity).append(" contained items, ")
                          .append(sortedItems.size()).append(" variants").append(System.lineSeparator())
                          .append("Entities  Quantity  Registry ID  Metadata  Java class")
                          .append(System.lineSeparator());
                    for (ItemEntry e : sortedItems) {
                        report.append(e.entities).append("  ").append(e.items).append("  ")
                              .append(e.id).append("  ").append(e.metadata).append("  [")
                              .append(e.javaClass).append("]").append(System.lineSeparator());
                    }
                }
            }
            if (!found) {
                reply(sender, "No matching loaded dimension.");
                return;
            }
            if (export) {
                File directory = MinecraftServer.getServer().getFile("logs/EntityGovernor");
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    reply(sender, "Could not create report directory: " + directory.getPath());
                    return;
                }
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
                File file = new File(directory, "census-" + stamp + ".txt");
                try {
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                    try {
                        writer.write(report.toString());
                        writer.write(System.lineSeparator() + "Total: " + grandTotal
                                + " loaded entities across " + dimensions + " dimensions"
                                + System.lineSeparator());
                    } finally {
                        writer.close();
                    }
                    reply(sender, "Exported " + grandTotal + " entities across " + dimensions
                            + " dimensions to " + file.getPath());
                } catch (IOException e) {
                    reply(sender, "Export failed: " + e.getMessage());
                }
            }
        }
    }
}
