/*
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
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.biome.Biome;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.cache.IslandData;
import com.larryTheCoder.listener.invitation.Invitation;
import com.larryTheCoder.listener.invitation.InvitationHandler;
import com.larryTheCoder.player.CoopData;
import com.larryTheCoder.player.TeamManager;
import com.larryTheCoder.task.UpdateBiomeTask;

import java.util.Arrays;
import java.util.List;

public class AdminCategory extends SubCategory {

    public AdminCategory(ASkyBlock plugin) {
        super(plugin);
    }

    @Override
    public List<String> getCommands() {
        return Arrays.asList("accept", "invite" ,"kickmember", "quit"/*, "biome","listbio"*/);
    } //, "reject"

    @Override
    public boolean canUse(CommandSender sender, String command) {
        switch (command.toLowerCase()) {
            case "listbio":
            case "biome":
                return sender.hasPermission("is.command.biome") && sender.isPlayer();
            case "accept":
                return sender.hasPermission("is.command.accept") && sender.isPlayer();
            case "invite":
                return sender.hasPermission("is.command.invite") && sender.isPlayer();
            case "kickmember":
                return sender.hasPermission("is.command.kickmember") && sender.isPlayer();
            case "quit":
                return sender.hasPermission("is.command.quit") && sender.isPlayer();
        }
        return false;
    }

    @Override
    public String getDescription(String command) {
        switch (command.toLowerCase()) {
            case "listbio":
                return "Danh sách biome đảo";
            case "biome":
                return "Đổi Biome cho đảo";
            case "accept":
                return "Chấp nhận lời mời vào đảo";
            /*case "deny":
            //case "reject":
                return "Denies an invitation from a player.";*/
            case "invite":
                return "Mời người chơi vào đảo của bạn";
            case "kickmember":
                return "Kick một thành viện khỏi Team CO_OP";
            case "quit":
                return "Rời khỏi Team CO_OP";
        }
        return null;
    }

    @Override
    public String getParameters(String commandName) {
        switch (commandName.toLowerCase()) {
            case "invite":
            case "kickmember":
                return "[PlayerName]";
            case "biome":
                return "[ID]";
            default:
                return "";
        }
    }

    @Override
    public void execute(CommandSender sender, String commandLabel, String[] args) {
        Player p = sender.getServer().getPlayer(sender.getName());
        InvitationHandler pd = getPlugin().getInvitationHandler();

        switch (args[0].toLowerCase()) {
            case "listbio":
                sender.sendMessage("Danh sách biome SkyBlock");
                sender.sendMessage("Mỗi loại đều có một chức năng khác nhau.");
                for (Biome bio : Biome.unorderedBiomes){
                    sender.sendMessage("Biome ID: " + bio.getId() + " - " + bio.getName());
                }
                break;
            case "biome":
                if (args.length != 2) {
                    sender.sendMessage(getPrefix() + "/is biome <ID>");
                    return;
                }
                Biome bio = Biome.getBiome(Integer.parseInt(args[1]));
                if (bio == null){
                    sender.sendMessage(getPrefix() + "Vui lòng thử lại, không có biome ID: " +args[1]);
                    return;
                }
                List<IslandData> islandDataList = getPlugin().getFastCache().getIslandsFrom(p.getName());
                if (islandDataList.size() < 1) {
                    sender.sendMessage(getPrefix() + getLocale(p).errorNoIsland);
                    return;
                }
                islandDataList.get(0).setPlotBiome(bio.getName());
                islandDataList.get(0).saveIslandData();
                Server.getInstance().getScheduler().scheduleTask(new UpdateBiomeTask(getPlugin(),islandDataList.get(0),p));
                break;
            case "accept":
                Invitation invite = pd.getInvitation(p);
                if (invite == null) {
                    sender.sendMessage(getPrefix() + getLocale(p).errorNotPending);
                    break;
                }
                invite.acceptInvitation();
                break;

            case "invite":
                if (args.length != 2) {
                    sender.sendMessage(getPrefix() + "/is invite <playername>");
                    return;
                }
                // Player cannot invite other players when he have no island
                if (!getPlugin().getIslandManager().checkIsland(p)) {
                    sender.sendMessage(getPrefix() + getLocale(p).errorNoIsland);
                    return;
                }
                // The invite player.
                Player inviter = sender.getServer().getPlayer(args[1]);
                if (inviter == null) {
                    sender.sendMessage(getPrefix() + getLocale(p).errorOfflinePlayer);
                    return;
                }
                TeamManager manager = getPlugin().getTManager();
                if (manager.hasTeam(inviter.getName())) {
                    sender.sendMessage(getPrefix() + getLocale(p).errorInTeam.replace("[player]", args[1]));
                    return;
                }
                CoopData coopData = manager.getLeaderCoop(p.getName());
                if (coopData != null){
                    int sizeup = 0;
                    if (p.hasPermission("is.rank.king+")){
                        sizeup = 5;
                    }else if (p.hasPermission("is.rank.king+")){
                        sizeup = 4;
                    }else if (p.hasPermission("is.rank.vip+")){
                        sizeup = 3;
                    }else if (p.hasPermission("is.rank.vip+")){
                        sizeup = 2;
                    }

                    if (coopData.getMembers().size() >= (2 + sizeup)){
                        sender.sendMessage(getPrefix() + " Bạn chỉ được có tối đa 2 thành viên trong đảo, nếu muốn thêm, hãy nâng VIP|KING");
                        return;
                    }
                }
                getPlugin().getInvitationHandler().addInvitation(p, inviter);
                break;
            case "kickmember":
                if (args.length != 2) {
                    sender.sendMessage(getPrefix() + "/is kickmember <playername>");
                    break;
                }
                getPlugin().getTManager().kickMember(p,args[1],"You has been kick from leader");
                break;
            case "quit":
                TeamManager manager2 = getPlugin().getTManager();
                CoopData coopData2 = manager2.getLeaderCoop(p.getName());
                if (coopData2 != null){
                    p.sendMessage(getPrefix() + " Bạn là chủ Team Co-op bạn không thể rời team ...");
                    break;
                }
                coopData2 = manager2.getPlayerCoop(p.getName());
                if (coopData2 != null){
                    getPlugin().getPanel().quitCoopIsland(p);
                    break;
                }
                p.sendMessage(getPrefix() + "Bạn chưa tham gia team coop skyblock nào ...");
                break;
        }
    }
}
