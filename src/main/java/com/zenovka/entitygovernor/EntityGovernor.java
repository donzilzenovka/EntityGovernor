package com.zenovka.entitygovernor;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import java.util.*;

@Mod(modid = "entitygovernor", name = "EntityGovernor", version = "0.1.0", acceptableRemoteVersions = "*")
public class EntityGovernor {
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CensusCommand());
    }

    public static class CensusCommand extends CommandBase {
        @Override public String getCommandName() { return "eg"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/eg census [all|dimensionId] [top N]"; }
        @Override public int getRequiredPermissionLevel() { return 2; }

        @Override public void processCommand(ICommandSender sender, String[] args) {
            if (args.length == 0 || !"census".equalsIgnoreCase(args[0])) {
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
                return;
            }
            boolean all = false;
            int dimension = sender.getEntityWorld().provider.dimensionId;
            int top = 20;
            for (int i = 1; i < args.length; i++) {
                if ("top".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    try { top = Math.max(1, Math.min(200, Integer.parseInt(args[++i]))); }
                    catch (NumberFormatException e) { sender.addChatMessage(new ChatComponentText("Invalid top count")); return; }
                } else if (i == 1 && "all".equalsIgnoreCase(args[i])) {
                    all = true;
                } else if (i == 1) {
                    try { dimension = Integer.parseInt(args[i]); }
                    catch (NumberFormatException e) { sender.addChatMessage(new ChatComponentText("Invalid dimension ID")); return; }
                } else {
                    sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
                    return;
                }
            }
            WorldServer[] worlds = DimensionManager.getWorlds();
            boolean found = false;
            for (WorldServer world : worlds) {
                if (world == null || (!all && world.provider.dimensionId != dimension)) continue;
                found = true;
                Map<String, Integer> counts = new HashMap<String, Integer>();
                int total = 0;
                for (Object object : world.loadedEntityList) {
                    if (!(object instanceof Entity)) continue;
                    Entity entity = (Entity) object;
                    String id = EntityList.getEntityString(entity);
                    if (id == null) id = "unregistered:" + entity.getClass().getName();
                    Integer previous = counts.get(id);
                    counts.put(id, previous == null ? 1 : previous + 1);
                    total++;
                }
                List<Map.Entry<String, Integer>> sorted = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
                Collections.sort(sorted, new Comparator<Map.Entry<String, Integer>>() {
                    @Override public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                        int result = b.getValue().compareTo(a.getValue());
                        return result != 0 ? result : a.getKey().compareTo(b.getKey());
                    }
                });
                sender.addChatMessage(new ChatComponentText("[EntityGovernor] Dimension " + world.provider.dimensionId + ": " + total + " entities, " + counts.size() + " types"));
                for (int i = 0; i < Math.min(top, sorted.size()); i++) {
                    Map.Entry<String, Integer> entry = sorted.get(i);
                    sender.addChatMessage(new ChatComponentText("  " + entry.getValue() + "  " + entry.getKey()));
                }
            }
            if (!found) sender.addChatMessage(new ChatComponentText("[EntityGovernor] No matching loaded dimension."));
        }
    }
}
