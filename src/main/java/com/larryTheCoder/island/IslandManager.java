/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2016-2020 larryTheCoder and contributors
 *
 * Permission is hereby granted to any persons and/or organizations
 * using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or
 * any derivatives of the work for commercial use or any other means to generate
 * income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing
 * and/or trademarking this software without explicit permission from larryTheCoder.
 *
 * Any persons and/or organizations using this software must disclose their
 * source code and have it publicly available, include this license,
 * provide sufficient credit to the original authors of the project (IE: larryTheCoder),
 * as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,FITNESS FOR A PARTICULAR
 * PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.larryTheCoder.island;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.TextFormat;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.cache.IslandData;
import com.larryTheCoder.cache.builder.IslandDataBuilder;
import com.larryTheCoder.cache.settings.WorldSettings;
import com.larryTheCoder.database.DatabaseManager;
import com.larryTheCoder.database.TableSet;
import com.larryTheCoder.events.IslandCreateEvent;
import com.larryTheCoder.player.CoopData;
import com.larryTheCoder.player.TeamManager;
import com.larryTheCoder.task.DeleteIslandTask;
import com.larryTheCoder.task.SimpleFancyTitle;
import com.larryTheCoder.task.TaskManager;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;
import me.onebone.economyapi.EconomyAPI;
import org.sql2o.Connection;
import org.sql2o.data.Row;
import org.sql2o.data.Table;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.larryTheCoder.database.TableSet.FETCH_ISLAND_UNIQUE;

/**
 * Core management for SkyBlock world and
 * execution.
 *
 * @author larryTheCoder
 */
public class IslandManager {

    private final ASkyBlock plugin;
    private CopyOnWriteArrayList<String> uniqueId = new CopyOnWriteArrayList<>();

    public IslandManager(ASkyBlock plugin) {
        this.plugin = plugin;

        // Saves island UniqueIds into an array.
        plugin.getDatabase().pushQuery((conn) -> {
            Table table = conn.createQuery(TableSet.FETCH_ALL_ISLAND_UNIQUE.getQuery()).executeAndFetchTable();

            for (Row rows : table.rows()) {
                uniqueId.add(rows.getString("islandUniqueId"));
            }
        });
    }

    public void handleIslandCommand(Player pl, boolean reset) {
        if (!reset) {
            //join direction to co-op island without create island

            TeamManager manager = plugin.getTManager();
            CoopData team = manager.getPlayerCoop(pl.getName());
            if (team != null) {
                pl.sendMessage(plugin.getPrefix() + "You have CO-OP island, join direct to your team island");
                teleportPlayer(pl,team.getLeaderName());
                return;
            }
            if (!checkIsland(pl)) {
                plugin.getIslandManager().createIsland(pl, 1, "SkyBlock", pl.getName()+"'s Island", false, EnumBiome.PLAINS, true);
                //plugin.getPanel().addIslandFormOverlay(pl);
                return;
            }

            pl.sendMessage(plugin.getLocale(pl).hangInThere);

            // teleport to grid
            plugin.getGrid().homeTeleport(pl);
            TopTen.topTenAddEntry(pl.getName(), 0);
        } else {
            createIsland(pl);
        }
    }

    void showFancyTitle(Player p) {
        // The problem solved. The task `while` are pushing the CPU far more to load makes it
        // Glitching and corrupted half chunk data. #20 Cannot teleport to island
        TaskManager.runTaskLater(new SimpleFancyTitle(plugin, p), 20);
    }

    public void kickPlayerByName(final Player pOwner, final String victimName) {
        final Location loc = pOwner.getLocation();
        final IslandData pd = plugin.getFastCache().getIslandData(loc);
        if (pd == null || pd.getPlotOwner() == null || !pd.getPlotOwner().equals(pOwner.getName())) {
            pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorNotOnIsland);
            return;
        }
        final int orgKey = generateIslandKey(loc);
        final Player pVictim = Server.getInstance().getPlayer(victimName);
        if (pVictim == null || !pVictim.isOnline()) {
            pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorOfflinePlayer);
            return;
        }
        if (!(pOwner.isOp())) {
            if (pVictim.isOp()) {
                pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorAdminOnly);
                return;
            }
        }
        if (victimName.equalsIgnoreCase(pOwner.getName())) {
            pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorKickOwner);
            return;
        }
        final Location locVict = pVictim.getLocation();
        final int tgtKey = generateIslandKey(locVict);
        if (tgtKey != orgKey) {
            pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorOfflinePlayer);
            return;
        }
        Utils.send("&cAn island owner, " + pOwner.getName() + " attempt to "
                + "execute kick command to " + pVictim.getName() + " At "
                + Utils.locationShorted(locVict));
        pOwner.sendMessage(plugin.getPrefix() + TextFormat.GREEN + "Success! You send " + TextFormat.YELLOW + victimName + TextFormat.GREEN + " to spawn!");
        pVictim.sendMessage(plugin.getPrefix() + plugin.getLocale(pVictim).kickedFromOwner.replace("[name]", pOwner.getName())); //TextFormat.RED + "You were kicked from island owned by " + TextFormat.YELLOW + pOwner.getName());

        pVictim.teleport(plugin.getServer().getDefaultLevel().getSafeSpawn());
    }

    public void kickPlayerByAdmin(CommandSender sender, String arg) {
        Player p = Server.getInstance().getPlayer(arg);
        Player kicker = sender.isPlayer() ? Server.getInstance().getPlayer(sender.getName()) : null;
        if (p == null) {
            sender.sendMessage(plugin.getPrefix() + plugin.getLocale(kicker).errorOfflinePlayer.replace("[player]", arg));
            return;
        }
        Location locVict = p.getLocation();
        for (String lvl : plugin.getLevels()) {
            if (!locVict.getLevel().getName().equalsIgnoreCase(lvl)) {
                sender.sendMessage(plugin.getPrefix() + plugin.getLocale(kicker).errorOfflinePlayer.replace("[player]", arg));
                return;
            }
        }
        sender.sendMessage(plugin.getPrefix() + plugin.getLocale(kicker).kickSuccess.replace("[player]", arg));
        p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).kickedFromAdmin);

        // Teleport
        p.teleport(plugin.getServer().getDefaultLevel().getSafeSpawn());
    }

    public boolean checkIsland(Player p) {
        return plugin.getFastCache().getIslandsFrom(p.getName()).size() > 0;
    }

    private void createIsland(Player p) {
        this.createIsland(p, 1, "", plugin.getDefaultWorld(), false, EnumBiome.PLAINS, false);
    }

    public void createIsland(Player pl, int templateId, String levelName, String home, boolean locked, EnumBiome biome, boolean teleport) {
        if (Settings.useEconomy) {
            // check if the starting island is FREE
            if (plugin.getFastCache().getIslandData(pl.getName()) == null && Settings.firstIslandFree) {
                pl.sendMessage(plugin.getLocale(pl).firstIslandFree);
                pl.sendMessage(plugin.getLocale(pl).nextIslandPrice.replace("[price]", Double.toString(Settings.islandCost)));
            } else {
                if (EconomyAPI.getInstance().reduceMoney(pl,Settings.islandCost) == EconomyAPI.RET_SUCCESS){
                    pl.sendMessage(plugin.getLocale(pl).firstIslandFree);
                    pl.sendMessage(plugin.getLocale(pl).nextIslandPrice.replace("[price]", Double.toString(Settings.islandCost)));
                }else{
                    pl.sendMessage(plugin.getLocale(pl).errorNotEnoughMoney.replace("[price]", Double.toString(Settings.islandCost)));
                    return;
                }
            }
        }

        WorldSettings settings = plugin.getSettings(levelName);
        Level world = Server.getInstance().getLevelByName(levelName);

        // Make sure the search didn't interrupt other processes.
        TaskManager.runTaskAsync(() -> {
            List<IslandData> islands = plugin.getFastCache().getIslandsFrom(pl.getName());

            for (int i = 0; i < Integer.MAX_VALUE; ++i) {
                int width = i * settings.getIslandDistance() * 2;
                int wx = (int) (Math.random() * width);
                int wz = (int) (Math.random() * width);

                int x = wx - wx % settings.getIslandDistance() + settings.getIslandDistance() / 2;
                int z = wz - wz % settings.getIslandDistance() + settings.getIslandDistance() / 2;

                int generatedData = generateIslandKey(wx, wz, levelName);
                boolean uniqueData = uniqueId.contains(Integer.toString(generatedData));
                if (!uniqueData) {
                    Location locIsland = new Location(x, Settings.islandHeight, z, world);

                    if (!checkIslandAt(locIsland.getLevel())) {
                        continue;
                    }

                    final IslandData resultData = new IslandDataBuilder()
                            .setGridCoordinates(new Vector2(x, z))
                            .setIslandHomeId(islands.size() + 1)
                            .setIslandUniquePlotId(generatedData)
                            .setPlotOwner(pl.getName())
                            .setLevelName(levelName)
                            .setProtectionSize(settings.getIslandDistance())
                            .setLocked(locked)
                            .setPlotBiome("Plains")
                            .setIslandName(home).build();

                    // Then we call a task to run them in the main thread.
                    TaskManager.runTask(() -> {
                        // Call an event
                        IslandCreateEvent event = new IslandCreateEvent(pl, templateId, resultData);
                        plugin.getServer().getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                            pl.sendMessage(plugin.getPrefix() + plugin.getLocale(pl).errorBlockedByAPI);
                            return;
                        }

                        plugin.getSchematics().pasteSchematic(pl, locIsland, templateId, biome);

                        // Then apply another async query.
                        ASkyBlock.get().getDatabase().pushQuery(new DatabaseManager.DatabaseImpl() {
                            @Override
                            public void executeQuery(Connection connection) {

                                connection.createQuery(TableSet.ISLAND_INSERT_MAIN.getQuery())
                                        .addParameter("playerName", pl.getName())
                                        .addParameter("islandId", resultData.getHomeCountId())
                                        .addParameter("islandUniqueId", resultData.getIslandUniquePlotId())
                                        .addParameter("gridPos", Utils.getVector2Pair(resultData.getCenter()))
                                        .addParameter("spawnPos", Utils.getVector3Pair(resultData.getHomeCoordinates()))
                                        .addParameter("gridSize", resultData.getProtectionSize())
                                        .addParameter("levelName", resultData.getLevelName())
                                        .addParameter("islandName", pl.getName() + " Island")
                                        .executeUpdate();

                                connection.createQuery(TableSet.ISLAND_INSERT_DATA.getQuery())
                                        .addParameter("islandUniqueId", resultData.getIslandUniquePlotId())
                                        .addParameter("plotBiome", resultData.getPlotBiome())
                                        .addParameter("isLocked", resultData.isLocked() ? 1 : 0)
                                        .addParameter("protectionData", resultData.getIgsSettings().getSettings())
                                        .addParameter("levelHandicap", resultData.getLevelHandicap())
                                        .executeUpdate();
                            }

                            @Override
                            public void onCompletion(Exception err) {
                                if (err != null) {
                                    err.printStackTrace();

                                    pl.sendMessage(plugin.getPrefix() + plugin.getLocale(pl).errorFailedCritical);
                                    return;
                                }

                                uniqueId.add(Integer.toString(resultData.getIslandUniquePlotId()));
                                plugin.getFastCache().addIslandIntoDb(pl.getName(), resultData);

                                pl.sendMessage(plugin.getPrefix() + plugin.getLocale(pl).createSuccess);
                                if (teleport) plugin.getGrid().homeTeleport(pl, resultData.getHomeCountId());
                            }
                        });
                    });

                    break;
                }
            }
        });
    }

    public int generateIslandKey(Location loc) {
        int x = loc.getFloorX();
        int z = loc.getFloorZ();
        return generateIslandKey(x, z, loc.level.getName());
    }

    /**
     * Generates a new public key by checking the vector values
     * with their island distances, this will make sure every key
     * will not be duplicated and will make sure that each key is
     * unique every X distance.
     *
     * @param x     The position of the x vector
     * @param z     The position of the z vector
     * @param level This level will be checked on their distances / level
     * @return A unique island key
     */
    public int generateIslandKey(int x, int z, String level) {
        WorldSettings settings = plugin.getSettings(level);
        return x / settings.getIslandDistance() + z / settings.getIslandDistance() * Integer.MAX_VALUE;
    }

    public void deleteIsland(Player p, IslandData pd) {
        if (pd == null || pd.getPlotOwner() == null) {
            p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorNoIsland);
            return;
        }
        if (!Utils.canBypassTimer(p, p.getName() + pd.getIslandUniquePlotId(), Settings.resetTime)) {
            p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorTooSoon.replace("[secs]", Utils.getPlayerRTime(p, p.getName() + pd.getIslandUniquePlotId(), 0)).replace("[cmds]", "delete"));
            return;
        }

        // Reset then wait :P
        TaskManager.runTask(new DeleteIslandTask(plugin, pd, p.getName()));
        if (plugin.getTManager().getLeaderCoop(p.getName()) != null){
            plugin.getTManager().removeteam(p.getName());
        }

        plugin.getFastCache().getPlayerData(p.getName(), ppd -> {
            ppd.setResetLeft(ppd.getResetLeft() + 1);
            ppd.saveData();

            p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).resetSuccess.replace("[mili]", "" + Settings.resetTime));
        });
    }

    public boolean checkIslandAt(Level level) {
        return plugin.getLevels().contains(level.getName());
    }

    public void islandInfo(Player p, Location loc) {
        if (!checkIslandAt(loc.getLevel())) {
            p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorWrongWorld);
            return;
        }
        final CoopData coopData = plugin.getTManager().getLeaderCoop(p.getName());
        final IslandData pd = plugin.getFastCache().getIslandData(loc);
        if (pd == null) {
            p.sendMessage(TextFormat.LIGHT_PURPLE + plugin.getLocale(p).errorNotOnIsland);
            return;
        }
        p.sendMessage(TextFormat.LIGHT_PURPLE + "- Island Owner: " + TextFormat.YELLOW + pd.getPlotOwner());
        String strMembers;
        if (coopData == null || coopData.getMembers().isEmpty()) {
            strMembers = "none";
        } else {
            strMembers = Utils.arrayToString(coopData.getMembers());
        }

        p.sendMessage(TextFormat.LIGHT_PURPLE + "- Members: " + TextFormat.AQUA + strMembers);
        p.sendMessage(TextFormat.LIGHT_PURPLE + "- Flags: " + TextFormat.GOLD + "Allow Teleport: " + pd.isLocked());
    }

    public void teleportPlayer(Player p, String arg) {
        plugin.getFastCache().getIslandData(arg, pd -> {
            if (pd == null) {
                p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorNoIslandOther);
                return;
            }
            /*if (pd.getPlotOwner() != null) {
                p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorOfflinePlayer.replace("[player]", arg));
                return;
            }*/
            plugin.getGrid().getSafeHomeLocation(pd.getPlotOwner(), pd.getHomeCountId(), home -> {
                    if (home == null) {
                        p.sendMessage(plugin.getPrefix() + TextFormat.RED + "Failed to find your island safe spawn");
                        return;
                    }
                    plugin.getTeleportLogic().safeTeleport(p, home, false, pd.getHomeCountId(),arg);
            });
        });
    }

    /**
     * Check either the location given is the player
     * island
     *
     * @param player The player to be check
     * @param loc    Location to be checked
     * @return true if the location is player's island
     */
    public boolean locationIsOnIsland(Player player, Vector3 loc) {
        if (player == null) {
            return false;
        }
        Location local = new Location(loc.x, loc.y, loc.z, player.getLevel());
        // Get the player's island from the grid if it exists
        IslandData island = plugin.getFastCache().getIslandData(local);
        CoopData pd = plugin.getTManager().getLeaderCoop(island.getPlotOwner());
        return island.onIsland(local) && ((pd == null || pd.getMembers().contains(player.getName())) || island.getPlotOwner().equalsIgnoreCase(player.getName()));
    }


}
