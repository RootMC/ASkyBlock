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

package com.larryTheCoder.command.category;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.TextFormat;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.cache.IslandData;
import com.larryTheCoder.player.CoopData;
import com.larryTheCoder.utils.BlockUtil;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class IslandCategory extends SubCategory {

    public IslandCategory(ASkyBlock plugin) {
        super(plugin);
    }

    @Override
    public List<String> getCommands() {
        return Arrays.asList("reset", "home", "sethome", "teleport", "calc", "info", "upsize"); //"create",
    }

    @Override
    public boolean canUse(CommandSender sender, String command) {
        switch (command) {
            case "upsize":
            case "info":
            case "calc":
                //case "create":
                return sender.hasPermission("is.create") && sender.isPlayer();
            case "reset":
                return sender.hasPermission("is.command.reset") && sender.isPlayer();
            case "home":
            case "sethome":
                return sender.hasPermission("is.command.home") && sender.isPlayer();
            case "teleport":
                return sender.hasPermission("is.command.teleport") && sender.isPlayer();
        }

        return false;
    }

    @Override
    public String getDescription(String commandName) {
        switch (commandName.toLowerCase()) {
            /*case "create":
                return "Start to create a new island.";*/
            case "info":
                return "Xem thông tin của đảo đang đứng";
            case "reset":
                return "Reset your original island.";
            case "home":
            case "sethome":
                return "Set your island main spawn position.";
            case "teleport":
                return "Teleport to your island spawn position.";
            case "calc":
                return "Caculator your island level ";
            case "upsize":
                return "Tăng kích thước đảo của bạn";
            default:
                return null;
        }
    }

    @Override
    public String getParameters(String commandName) {
        if (commandName.toLowerCase().equals("teleport")) {
            return "[Home Number]";
        }
        return "";
    }

    @Override
    public void execute(CommandSender sender, String commandLabel, String[] args) {
        Player p = sender.getServer().getPlayer(sender.getName());

        switch (args[0].toLowerCase()) {
            case "upsize":

                if(getPlugin().getIslandManager().checkIsland(p)){
                    getPlugin().getPanel().upSize(p);
                    return;
                }
                p.sendMessage(getPrefix()+"§cBạn phải là chủ đảo mới có thể upsize đảo");
                break;
            case "info":
                if (!getPlugin().getLevels().contains(p.getLevel().getName())){
                    p.sendMessage(getPlugin().getPrefix()+"§cBạn không đứng trên đảo nào!");
                    return;
                }
                IslandData islandData = getPlugin().getFastCache().getIslandData(p.getLocation());
                if (islandData != null){
                    String text = getPrefix() + "Thông tin đảo bạn đang đứng :\n";
                    text += "✪ Chủ đảo: " + islandData.getPlotOwner() + "\n";
                    CoopData coop = getPlugin().getTManager().getLeaderCoop(islandData.getPlotOwner());
                    if (coop != null){
                        text += TextFormat.LIGHT_PURPLE + "✪ Thành viên: " + TextFormat.AQUA + Utils.arrayToString(coop.getMembers())+"\n";
                    }
                    text += "✪ Kích thước đảo: "+islandData.getIsUpSize()+"x"+islandData.getIsUpSize();
                    p.sendMessage(text);
                    return;
                }
                p.sendMessage(getPlugin().getPrefix()+"§cBạn không đứng trên đảo nào!");
                break;
            case "calc":
                if (!getPlugin().getIslandManager().checkIsland(p)) {
                    sender.sendMessage(getPrefix() + getLocale(p).errorNoIsland);
                    break;
                }
                long current = System.currentTimeMillis();
                if(getPlugin().commandCalcCache.containsKey(p.getName())){
                    long end = getPlugin().commandCalcCache.get(p.getName());
                    if (current >= end){
                        getPlugin().commandCalcCache.remove(p.getName());
                    }else{
                        p.sendMessage(getPrefix()+"§cBạn còn "+ (end - current) / 1000 +" giây nữa để sử dụng lại lệnh nàyp !");
                        return;
                    }
                }

                IslandData is = getPlugin().getFastCache().getIslandData(sender.getName());
                getPlugin().getCalcTask().addUpdateQueue(is,sender);
                getPlugin().commandCalcCache.put(p.getName(), current + 60 * 60 * 1000L);
                break;
            /*case "create":
                getPlugin().getPanel().addIslandFormOverlay(p);
                break;*/
            case "reset":
                getPlugin().getPanel().addDeleteFormOverlay(p);
                break;
            case "home":
                getPlugin().getFastCache().getIslandsFrom(p.getName(), listHome -> {
                    if (listHome == null) {
                        p.sendMessage(getPlugin().getLocale(p).errorFailedCritical);
                        return;
                    }

                    if (listHome.size() == 1) {
                        getPlugin().getGrid().homeTeleport(p);
                        return;
                    }

                    getPlugin().getPanel().addHomeFormOverlay(p, listHome);
                });
                break;
            case "sethome":
                getPlugin().getFastCache().getIslandData(p.getLocation(), pd -> {
                    // Check if the ground is an air
                    if (!BlockUtil.isBreathable(p.clone().add(p.down()).getLevelBlock())) {
                        p.sendMessage(getLocale(p).groundNoAir);
                        return;
                    }
                    // Check if the player on their own island or not
                    if (pd != null && pd.getPlotOwner().equalsIgnoreCase(sender.getName())) {
                        pd.setHomeLocation(p.getLocation());
                        pd.saveIslandData();

                        p.sendMessage(getLocale(p).setHomeSuccess);
                    } else {
                        p.sendMessage(getLocale(p).errorNotOnIsland);
                    }
                });
                break;
            case "teleport":
                if (args.length != 2) {
                    break;
                }
                getPlugin().getIslandManager().teleportPlayer(p, args[1]);
                break;
        }
    }
}
