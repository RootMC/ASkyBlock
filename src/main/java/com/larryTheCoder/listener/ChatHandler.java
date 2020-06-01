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
package com.larryTheCoder.listener;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.utils.TextFormat;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.events.IslandPreLevelEvent;
import com.larryTheCoder.player.CoopData;
import com.larryTheCoder.utils.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tastybento
 * @author larryTheCoder
 */
public class ChatHandler implements Listener {

    private final ASkyBlock plugin;
    private final ConcurrentHashMap<String, Integer> playerLevels;
    private final ConcurrentHashMap<Player, Boolean> teamChatUsers;

    public ChatHandler(ASkyBlock plugin) {
        this.plugin = plugin;
        this.playerLevels = new ConcurrentHashMap<>();
        this.teamChatUsers = new ConcurrentHashMap<>();

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(final PlayerChatEvent event) {
        String format = event.getFormat().replace("{ISLAND_LEVEL}", String.valueOf(getPlayerLevel(event.getPlayer().getName())));
        event.setFormat(format);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onUpgradelevel(IslandPreLevelEvent event){
        Player player = event.getPlayer();
        plugin.getServer().broadcastMessage(plugin.getPrefix()+"§l §aChúc mừng đảo của §c" + event.getPlayer().getName() + " §ađã đạt cấp độ §f"+event.getLevel());

        CoopData coopData = plugin.getTManager().getPlayerCoop(player.getName());
        if (coopData != null){
            coopData.setLevel(event.getLevel());
            coopData.getMembers().forEach((ppp) -> setPlayerLevel(ppp,event.getLevel()));
        }
        setPlayerLevel(player.getName(),event.getLevel());
        //ngoai
        plugin.getFastCache().getPlayerData(event.getPlayer(),pd -> pd.setIslandLevel(event.getLevel()));
    }

    /**
     * @param p adds player to chat
     */
    public void setPlayer(Player p) {
        this.teamChatUsers.put(p, true);
    }


    public void setPlayerLevel(String playerName, int level) {
        playerLevels.put(playerName,level);
    }

    /**
     * Return the player's level for use in chat - async safe
     *
     * @param playerUUID uuid
     * @return Player's level as string
     */
    public Integer getPlayerLevel(String playerUUID) {
        return (playerLevels.getOrDefault(playerUUID, 0));
    }
}
