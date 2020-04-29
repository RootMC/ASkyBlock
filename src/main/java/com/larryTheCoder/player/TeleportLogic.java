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
package com.larryTheCoder.player;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.level.Location;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.TextFormat;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for teleporting (and canceling teleporting) of players.
 *
 * @author larryTheCoder
 * @author tastybento
 */
public class TeleportLogic implements Listener {

    private static final List<String> list = Lists.newArrayList();
    private static final Map<String, Integer> time = Maps.newHashMap();
    private static int teleportDelay;
    private final ASkyBlock plugin;
    private final Map<UUID, PendingTeleport> pendingTPs = new ConcurrentHashMap<>();
    private final double cancelDistance;

    public TeleportLogic(ASkyBlock plugin) {
        this.plugin = plugin;
        teleportDelay = plugin.getConfig().getInt("general.islandTeleportDelay", 0);
        cancelDistance = plugin.getConfig().getDouble("options.island.teleportCancelDistance", 0.6);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static boolean isPlayerMoved(String p) {
        return list.contains(p);
    }

    public static int getPlayerTeleport(String p) {
        return time.get(p);
    }

    public void safeTeleport(final Player player, final Location homeSweetHome, boolean force, int home,String leader) {
        final Location targetLoc = homeSweetHome.clone().add(0.5, 0, 0.5);
        Utils.loadChunkAt(targetLoc);

        if (player.hasPermission("is.bypass.wait") || (teleportDelay == 0) || force) {
            player.teleport(targetLoc);
            CoopData temp = plugin.getTManager().getLeaderCoop(leader);
            if (temp != null){
                player.sendMessage(TextFormat.LIGHT_PURPLE + "✪ Chủ đảo: " + TextFormat.YELLOW + leader);
                player.sendMessage(TextFormat.LIGHT_PURPLE + "✪ Thành viên: " + TextFormat.AQUA + Utils.arrayToString(temp.getMembers()));
            }
        } else {
            player.sendMessage(plugin.getPrefix() + plugin.getLocale(player).teleportDelay.replace("{0}", "" + teleportDelay));
            TaskHandler task = plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                // Save player inventory
                if (Settings.saveInventory) {
                    plugin.getInventory().savePlayerInventory(player);
                }
                pendingTPs.remove(player.getUniqueId());
                if (home == 1) {
                    player.sendMessage(plugin.getPrefix() + TextFormat.GREEN + "Teleported to your island");
                } else {
                    player.sendMessage(plugin.getPrefix() + TextFormat.GREEN + "Teleported to your island #" + home);
                }
                player.teleport(targetLoc.add(0, 0.35)); // Adjust spawn hieght
                // Teleport in default gameMode
                if (Settings.gameMode != -1) {
                    player.setGamemode(Settings.gameMode);
                }
            }, Utils.secondsAsMillis(teleportDelay));
            time.put(player.getName(), Utils.secondsAsMillis(teleportDelay));
            pendingTPs.put(player.getUniqueId(), new PendingTeleport(player.getLocation(), task));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (e.isCancelled() || e.getPlayer() == null || e.getPlayer().getLocation() == null) {
            return;
        }
        UUID uniqueId = e.getPlayer().getUniqueId();
        PendingTeleport pendingTeleport = pendingTPs.get(uniqueId);
        if (pendingTeleport != null) {
            pendingTeleport.playerMoved(e.getPlayer());
        }
    }

    private class PendingTeleport {

        private final Location location;
        private final TaskHandler task;

        private PendingTeleport(Location location, TaskHandler task) {
            this.location = location != null ? location.clone() : null;
            this.task = task;
        }

        public Location getLocation() {
            return location;
        }

        public TaskHandler getTask() {
            return task;
        }

        void playerMoved(Player player) {
            Location newLocation = player.getLocation();
            if (location != null && location.getLevel().equals(newLocation.getLevel())) {
                double distance = location.distance(newLocation);
                if (distance > cancelDistance) {
                    task.cancel();
                    pendingTPs.remove(player.getUniqueId());
                    player.sendMessage(plugin.getPrefix() + plugin.getLocale(player).teleportCancelled);
                    list.add(player.getName());
                }
            }
        }
    }
}
