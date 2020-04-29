//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.larryTheCoder;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import cn.nukkit.utils.TextFormat;
import com.larryTheCoder.cache.FastCache;
import com.larryTheCoder.cache.inventory.InventorySave;
import com.larryTheCoder.cache.settings.WorldSettings;
import com.larryTheCoder.command.Commands;
import com.larryTheCoder.database.DatabaseManager;
import com.larryTheCoder.database.TableSet;
import com.larryTheCoder.database.config.AbstractConfig;
import com.larryTheCoder.database.config.MySQLConfig;
import com.larryTheCoder.database.config.SQLiteConfig;
import com.larryTheCoder.island.GridManager;
import com.larryTheCoder.island.IslandManager;
import com.larryTheCoder.island.TopTen;
import com.larryTheCoder.listener.ChatHandler;
import com.larryTheCoder.listener.IslandListener;
import com.larryTheCoder.listener.LavaCheck;
import com.larryTheCoder.listener.PlayerEvent;
import com.larryTheCoder.listener.invitation.InvitationHandler;
import com.larryTheCoder.locales.ASlocales;
import com.larryTheCoder.player.TeamManager;
import com.larryTheCoder.player.TeleportLogic;
import com.larryTheCoder.schematic.SchematicHandler;
import com.larryTheCoder.task.LevelCalcTask;
import com.larryTheCoder.task.TaskManager;
import com.larryTheCoder.utils.ConfigManager;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;
import org.sql2o.Query;
import org.sql2o.data.Table;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class ASkyBlock extends ASkyBlockAPI {
    private static ASkyBlock object;
    public final ArrayList<String> loadedLevel = new ArrayList();
    private ArrayList<WorldSettings> level = new ArrayList();
    private Config cfg;
    private Config worldConfig;
    private boolean disabled = false;
    private HashMap<String, ASlocales> availableLocales = new HashMap();
    private Properties pluginGit;
    public HashMap<String, Long> commandCalcCache = new HashMap();

    public ASkyBlock() {
    }

    public static ASkyBlock get() {
        return object;
    }

    public void onLoad() {
        if (object == null) {
            object = this;
        }

        this.initConfig();
        Generator.addGenerator(SkyBlockGenerator.class, "island", 132824063);
        TaskManager.IMP = new TaskManager();
    }

    public void onEnable() {
        if (!this.disabled && this.initDatabase()) {
            this.generateLevel();
            this.getServer().getLogger().info(this.getPrefix() + "§7Loading ASkyBlock - Bedrock Edition (API 30)");
            this.initIslands();
            this.registerObject();
            this.getServer().getLogger().info(this.getPrefix() + "§aASkyBlock has been successfully enabled!");
        }
    }

    public void onDisable() {
        Utils.send("&7Saving all island framework...");
        this.saveLevel(true);
        this.getFastCache().shutdownCache();
        this.getDatabase().shutdownDB();
        this.getMessages().saveMessages();
        TopTen.topTenSave();
        this.getTManager().saveData();
        Utils.send("&cASkyBlock has been successfully disabled. Goodbye!");
    }

    public Config getConfig() {
        return this.cfg;
    }

    private boolean initDatabase() {
        if (this.disabled) {
            return false;
        } else {
            String connectionType = this.cfg.getString("database.connection");
            Object dbConfig;
            if (connectionType.equalsIgnoreCase("mysql")) {
                dbConfig = new MySQLConfig(this.cfg);
            } else {
                dbConfig = new SQLiteConfig(this.cfg);
            }

            try {
                this.database = new DatabaseManager((AbstractConfig) dbConfig);
                return true;
            } catch (ClassNotFoundException | SQLException var4) {
                var4.printStackTrace();
                return false;
            }
        }
    }

    private void initIslands() {
        this.getServer().getCommandMap().register("ASkyBlock", new Commands(this));
        PluginManager pm = this.getServer().getPluginManager();
        this.chatHandler = new ChatHandler(this);
        this.teleportLogic = new TeleportLogic(this);
        this.invitationHandler = new InvitationHandler(this);
        this.panel = new ServerPanel(this);
        this.fastCache = new FastCache(this);
        this.messages = new Messages(this);
        this.messages.loadMessages();
        this.calcTask = new LevelCalcTask(this);
        TopTen.topTenLoad();
        pm.registerEvents(this.chatHandler, this);
        pm.registerEvents(new IslandListener(this), this);
        pm.registerEvents(new LavaCheck(this), this);
        pm.registerEvents(new PlayerEvent(this), this);
        ServerScheduler pd = this.getServer().getScheduler();
        pd.scheduleRepeatingTask(new PluginTask(this), 20);
    }

    private void initGitCheckup() {
        Properties properties = new Properties();

        try {
            properties.load(this.getResource("git-sb.properties"));
        } catch (IOException var3) {
            this.getServer().getLogger().info("§cERROR! We cannot load the git loader for this ASkyBlock build!");
            return;
        }

        Utils.sendDebug("§7ASkyBlock Git Information:");
        Utils.sendDebug("§7Build number: " + properties.getProperty("git.commit.id.describe", ""));
        Utils.sendDebug("§7Commit number: " + properties.getProperty("git.commit.id"));
        this.pluginGit = properties;
    }

    public Properties getGitInfo() {
        return this.pluginGit;
    }

    private void registerObject() {
        Utils.send(TextFormat.GRAY + "Loading all island framework. Please wait...");
        this.schematics = new SchematicHandler(this, new File(this.getDataFolder(), "schematics"));
        this.islandManager = new IslandManager(this);
        this.grid = new GridManager(this);
        this.tManager = new TeamManager(this);
        this.inventory = new InventorySave();
    }

    private void initConfig() {
        this.initGitCheckup();
        Utils.EnsureDirectory(Utils.DIRECTORY);
        Utils.EnsureDirectory(Utils.LOCALES_DIRECTORY);
        Utils.EnsureDirectory(Utils.SCHEMATIC_DIRECTORY);
        Utils.EnsureDirectory(Utils.UPDATES_DIRECTORY);
        this.saveResource("config.yml");
        this.saveResource("worlds.yml");
        this.saveResource("quests.yml");
        this.saveResource("blockvalues.yml");
        this.saveResource("schematics/island.schematic");
        this.saveResource("schematics/featured.schematic");
        this.saveResource("schematics/double.schematic");
        this.saveResource("schematics/harder.schematic");
        this.saveResource("schematics/nether.schematic");
        this.cfg = new Config(new File(this.getDataFolder(), "config.yml"), 2);
        this.worldConfig = new Config(new File(this.getDataFolder(), "worlds.yml"), 2);
        this.recheck();
        ConfigManager.load();
    }

    private void recheck() {
        File file = new File(get().getDataFolder(), "config.yml");
        Config config = new Config(file, 2);
        if (!Utils.isNumeric(config.get("version")) || config.getInt("version", 0) < 1) {
            file.renameTo(new File(get().getDataFolder(), "config.old"));
            get().saveResource("config.yml");
            Utils.send("&cYour configuration file is outdated! We are creating you new one, please wait...");
            Utils.send("&aYour old config will be renamed into config.old!");
        }

        this.cfg.reload();
    }

    private void generateLevel() {
        if (!Server.getInstance().isLevelGenerated("SkyBlock")) {
            Server.getInstance().generateLevel("SkyBlock", 0L, SkyBlockGenerator.class);
        }

        if (!Server.getInstance().isLevelLoaded("SkyBlock")) {
            Server.getInstance().loadLevel("SkyBlock");
        }

        this.database.pushQuery((connection) -> {
            List<String> levels = new ArrayList();
            Query query = connection.createQuery(TableSet.FETCH_WORLDS.getQuery());

            try {
                Table table = query.executeAndFetchTable();
                table.rows().forEach((i) -> {
                    levels.add(i.getString("worldName"));
                });
            } catch (Throwable var11) {
                if (query != null) {
                    try {
                        query.close();
                    } catch (Throwable var10) {
                        var11.addSuppressed(var10);
                    }
                }

                throw var11;
            }

            if (query != null) {
                query.close();
            }

            if (!levels.contains("SkyBlock")) {
                levels.add("SkyBlock");
            }

            ArrayList<WorldSettings> settings = new ArrayList();
            Iterator var13 = levels.iterator();

            while (var13.hasNext()) {
                String levelName = (String) var13.next();
                String levelSafeName = levelName.replace(" ", "_");
                Utils.loadLevelSeed(levelName);
                Level level = this.getServer().getLevelByName(levelName);
                WorldSettings worldSettings;
                if (this.worldConfig.isSection(levelSafeName)) {
                    ConfigSection section = this.worldConfig.getSection(levelSafeName);
                    worldSettings = WorldSettings.builder().setPermission(section.getString("permission")).setPlotMax(section.getInt("maxHome")).setPlotSize(section.getInt("plotSize")).setPlotRange(section.getInt("protectionRange")).isStopTime(section.getBoolean("stopTime")).useDefaultChest(section.getBoolean("useDefaultChest")).setSeaLevel(section.getInt("seaLevel")).setLevel(level).setSignSettings(section.getList("signConfig")).build();
                    worldSettings.verifyWorldSettings();
                } else {
                    worldSettings = new WorldSettings(level);
                    worldSettings.saveConfig(this.cfg);
                }

                settings.add(worldSettings);
                this.loadedLevel.add(levelName);
            }

            this.level = settings;
            this.saveLevel(false);
        });
    }

    public boolean isDebug() {
        return this.cfg.getBoolean("debug");
    }

    public WorldSettings getSettings(String levelName) {
        return (WorldSettings) this.level.stream().filter((i) -> {
            return i.getLevelName().equalsIgnoreCase(levelName);
        }).findFirst().orElse(null);
    }

    public String getPrefix() {
        return this.cfg.getString("Prefix").replace("&", "§");
    }

    public ASlocales getLocale(CommandSender sender) {
        return sender.isPlayer() ? this.getLocale((Player) sender) : this.getLocale("");
    }

    public ASlocales getLocale(Player p) {
        return p == null ? this.getLocale(Settings.defaultLanguage) : this.getLocale(p.getName());
    }

    public ASlocales getLocale(String p) {
        //if (p == null || p.isEmpty()) {
        return getAvailableLocales().get(Settings.defaultLanguage);
        //}
        //return getAvailableLocales().get(getFastCache().getDefaultLocale(p));
    }

    public void saveLevel(boolean showEnd) {
        if (showEnd) {
            Utils.send("&eSaving worlds...");
        }

        this.database.pushQuery((connection) -> {
            try {
                Query queue = connection.createQuery(TableSet.WORLDS_INSERT.getQuery());

                try {
                    Iterator var3 = this.level.iterator();

                    while (var3.hasNext()) {
                        WorldSettings settings = (WorldSettings) var3.next();
                        queue.addParameter("levelName", settings.getLevelName());
                        queue.executeUpdate();
                    }
                } catch (Throwable var6) {
                    if (queue != null) {
                        try {
                            queue.close();
                        } catch (Throwable var5) {
                            var6.addSuppressed(var5);
                        }
                    }

                    throw var6;
                }

                if (queue != null) {
                    queue.close();
                }
            } catch (Exception var7) {
                var7.printStackTrace();
                Utils.send("&cUnable to save the world.");
            }

        });
    }

    public HashMap<String, ASlocales> getAvailableLocales() {
        return this.availableLocales;
    }

    public void setAvailableLocales(HashMap<String, ASlocales> availableLocales) {
        this.availableLocales = availableLocales;
    }

    public ArrayList<String> getLevels() {
        ArrayList<String> level = new ArrayList();
        Iterator var2 = this.level.iterator();

        while (var2.hasNext()) {
            WorldSettings settings = (WorldSettings) var2.next();
            level.add(settings.getLevelName());
        }

        return level;
    }


    public String getDefaultWorld() {
        return "SkyBlock";
    }

    public boolean inIslandWorld(Player p) {
        return this.getIslandManager().checkIslandAt(p.getLevel());
    }

    public ArrayList<WorldSettings> getLevel() {
        return this.level;
    }
}
