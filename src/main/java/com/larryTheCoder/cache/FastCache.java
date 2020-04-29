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

package com.larryTheCoder.cache;

import cn.nukkit.Player;
import cn.nukkit.level.Position;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import cn.nukkit.utils.TextFormat;
import com.google.common.base.Preconditions;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.database.DatabaseManager;
import com.larryTheCoder.task.TaskManager;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;
import lombok.Getter;
import lombok.NonNull;
import org.sql2o.Connection;
import org.sql2o.data.Row;
import org.sql2o.data.Table;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.larryTheCoder.database.TableSet.*;

/**
 * Caches information for an object.
 */
public class FastCache {

    private final ASkyBlock plugin;

    // Since ArrayList is modifiable, there's no need to replace the cache again into the list.
    private final List<FastCacheData> dataCache = new ArrayList<>();

    // To securely store FastCache timestamp into a .json file.
    public static final ConcurrentLinkedQueue<FastCacheData> storeSchedule = new ConcurrentLinkedQueue<>();
    public final AtomicBoolean isClosed = new AtomicBoolean(true);
    private Config config;
    private String cacheValidationId;

    public FastCache(ASkyBlock plugin) {
        this.plugin = plugin;
        this.loadFastCache();
    }

    public void shutdownCache() {
        isClosed.compareAndSet(true, false);
    }

    private void addAllCacheData(List<FastCacheData> list) {
        dataCache.addAll(list);
    }

    private void loadFastCache() {
        config = new Config(Utils.DIRECTORY + "cache.yml", Config.YAML);

        if (config.getString("cacheVerificationId").isEmpty()) {
            config.set("cacheVerificationId", DatabaseManager.currentCacheId);
            config.save();

            cacheValidationId = DatabaseManager.currentCacheId;
        } else {
            cacheValidationId = config.getString("cacheVerificationId");

            if (!cacheValidationId.equals(DatabaseManager.currentCacheId)) {
                Utils.send(TextFormat.RED + "Cache metadata mismatched with database info.");
                config.setAll(new ConfigSection());
                config.set("cacheVerificationId", DatabaseManager.currentCacheId);

                config.save();
            }
        }

        plugin.getDatabase().pushQuery(new DatabaseManager.DatabaseImpl() {
            private final List<FastCacheData> dataCache = new ArrayList<>();

            @Override
            public void executeQuery(Connection connection) {
                for (String userName : config.getAll().keySet()) {
                    if (userName.equals("cacheVerificationId")) {
                        continue;
                    }

                    FastCacheData data = new FastCacheData(userName);
                    Row plData;
                    Row clData;

                    // Fetch user info
                    Table table = connection.createQuery(FETCH_PLAYER_MAIN.getQuery())
                            .addParameter("plotOwner", userName)
                            .executeAndFetchTable();

                    plData = table.rows().get(0);
                    clData = null;
                    /*table = connection.createQuery(FETCH_PLAYER_DATA.getQuery())
                            .addParameter("playerName", userName)
                            .executeAndFetchTable();

                    if (!table.rows().isEmpty()) {
                        clData = table.rows().get(0);
                    } else {
                        clData = null;
                    }*/

                    data.setPlayerData(PlayerData.fromRows(plData, clData));

                    // Then we fetch IslandData.
                    table = connection.createQuery(FETCH_ISLANDS_PLOT.getQuery())
                            .addParameter("pName", userName)
                            .executeAndFetchTable();

                    for (Row islandRow : table.rows()) {
                        int islandId = islandRow.getInteger("islandUniqueId");

                        table = connection.createQuery(FETCH_ISLAND_DATA.getQuery())
                                .addParameter("islandUniquePlotId", islandId)
                                .executeAndFetchTable();

                        if (table.rows().isEmpty()) {
                            data.addIslandData(IslandData.fromRows(islandRow));
                            continue;
                        }

                        Row dataRow = table.rows().get(0);
                        data.addIslandData(IslandData.fromRows(islandRow, dataRow));
                    }

                    dataCache.add(data);
                }
            }

            @Override
            public void onCompletion(Exception err) {
                if (err != null) {
                    return;
                }
                Utils.sendDebug(String.format("Loaded %s cache data.", dataCache.size()));

                ASkyBlock.get().getFastCache().addAllCacheData(dataCache);
                storeSchedule.clear(); // Remove useless caches.

                TaskManager.runTaskAsync(() -> {
                    while (isClosed.get()) {
                        FastCacheData consumer;
                        while ((consumer = storeSchedule.poll()) != null) {
                            ConfigSection playerSec = new ConfigSection();
                            playerSec.set("lastFetched", consumer.lastUpdatedQuery);
                            playerSec.set("islandIds", new ArrayList<>(consumer.getIslandData().keySet()));

                            config.set(consumer.getDataIdentifier(), playerSec);
                            config.save();
                        }

                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });


            }
        });
    }

    /**
     * Retrieves an information of an island with a given position.
     * This is a thread blocking operation if the information were not found in cache.
     */
    public IslandData getIslandData(Position pos) {
        int id = plugin.getIslandManager().generateIslandKey(pos.getFloorX(), pos.getFloorZ(), pos.getLevel().getName());

        FastCacheData result = dataCache.stream().filter(i -> i.anyIslandUidMatch(id)).findFirst().orElse(null);
        if (result == null) {
            Connection connection = plugin.getDatabase().getConnection();

            List<IslandData> islandList = parseData(connection.createQuery(FETCH_LEVEL_PLOT.getQuery())
                    .addParameter("islandId", id)
                    .addParameter("levelName", pos.getLevel().getName())
                    .executeAndFetchTable().rows(), connection);

            putIslandUnspecified(islandList);

            result = dataCache.stream().filter(i -> i.anyIslandUidMatch(id)).findFirst().orElse(null);

            return result == null ? null : result.getIslandByUId(id);
        }

        return result.getIslandByUId(id);
    }

    public IslandData getIslandData(String playerName) {
        return getIslandData(playerName, 1);
    }

    public IslandData getIslandData(String playerName, int homeNum) {
        FastCacheData result = dataCache.stream().filter(i -> i.anyMatch(playerName) && i.anyIslandMatch(homeNum)).findFirst().orElse(null);
        if (result == null) {
            Connection connection = plugin.getDatabase().getConnection();

            List<Row> data = connection.createQuery(FETCH_ISLAND_PLOT.getQuery())
                    .addParameter("pName", playerName)
                    .addParameter("islandId", homeNum)
                    .executeAndFetchTable().rows();

            if (data == null || data.isEmpty()) {
                return null;
            }

            List<IslandData> islandList = parseData(data, connection);

            saveIntoDb(playerName, islandList);

            return islandList.stream().filter(i -> i.getHomeCountId() == homeNum).findFirst().orElse(null);
        }

        return result.getIslandById(homeNum);
    }

    /**
     * Retrieves all islands from this player.
     * This method is a thread blocking operation if the given name is not found in the cache.
     */
    @NonNull
    public List<IslandData> getIslandsFrom(String plName) {
        FastCacheData result = dataCache.stream().filter(i -> i.anyMatch(plName)).findFirst().orElse(null);
        if (result == null || result.islandData.values().size() == 0) {
            Connection connection = plugin.getDatabase().getConnection();

            List<IslandData> islandList = parseData(connection.createQuery(FETCH_ISLAND_PLOTS.getQuery())
                    .addParameter("pName", plName)
                    .executeAndFetchTable().rows(), connection);

            putIslandUnspecified(islandList);

            return islandList;
        }

        return new ArrayList<>(result.islandData.values());
    }

    /**
     * Retrieves an information of an island with a given position.
     */
    public void getIslandData(Position pos, Consumer<IslandData> resultOutput) {
        int id = plugin.getIslandManager().generateIslandKey(pos.getFloorX(), pos.getFloorZ(), pos.getLevel().getName());

        FastCacheData result = dataCache.stream().filter(i -> i.anyIslandUidMatch(id)).findFirst().orElse(null);
        if (result == null) {
            plugin.getDatabase().pushQuery(new DatabaseManager.DatabaseImpl() {
                List<IslandData> islandList = null;

                @Override
                public void executeQuery(Connection connection) {
                    islandList = parseData(connection.createQuery(FETCH_LEVEL_PLOT.getQuery())
                            .addParameter("islandId", id)
                            .addParameter("levelName", pos.getLevel().getName())
                            .executeAndFetchTable().rows(), connection);
                }

                @Override
                public void onCompletion(Exception err) {
                    if (err != null) {
                        resultOutput.accept(null);
                        return;
                    }
                    putIslandUnspecified(islandList);

                    FastCacheData result = dataCache.stream().filter(i -> i.anyIslandUidMatch(id)).findFirst().orElse(null);

                    IslandData pd = result == null ? null : result.getIslandById(id);
                    resultOutput.accept(pd);
                }
            });
            return;
        }

        resultOutput.accept(result.getIslandByUId(id));
    }

    public void getIslandData(String playerName, Consumer<IslandData> resultOutput) {
        getIslandData(playerName, 1, resultOutput);
    }

    public void getIslandData(String playerName, int homeNum, Consumer<IslandData> resultOutput) {
        FastCacheData result = dataCache.stream().filter(i -> i.anyMatch(playerName) && i.anyIslandMatch(homeNum)).findFirst().orElse(null);
        if (result == null) {
            plugin.getDatabase().pushQuery(new DatabaseManager.DatabaseImpl() {
                private List<IslandData> islandList;

                @Override
                public void executeQuery(Connection connection) {
                    islandList = parseData(connection.createQuery(FETCH_ISLAND_PLOT.getQuery())
                            .addParameter("pName", playerName)
                            .addParameter("islandId", homeNum)
                            .executeAndFetchTable().rows(), connection);
                }

                @Override
                public void onCompletion(Exception exception) {
                    if (exception != null) {
                        resultOutput.accept(null);

                        exception.printStackTrace();
                        return;
                    }
                    saveIntoDb(playerName, islandList);

                    FastCacheData result = dataCache.stream().filter(i -> i.anyMatch(playerName) && i.anyIslandMatch(homeNum)).findFirst().orElse(null);
                    resultOutput.accept(result != null ? result.getIslandById(homeNum) : null);
                }
            });
            return;
        }
        resultOutput.accept(result.getIslandById(homeNum));
    }

    /**
     * Retrieves all islands from this player
     * This method is not a thread-blocking operation.
     */
    public void getIslandsFrom(String plName, Consumer<List<IslandData>> resultOutput) {
        FastCacheData result = dataCache.stream().filter(i -> i.anyMatch(plName)).findFirst().orElse(null);
        if (result == null) {
            plugin.getDatabase().pushQuery(new DatabaseManager.DatabaseImpl() {
                List<IslandData> islandList = null;

                @Override
                public void executeQuery(Connection connection) {
                    islandList = parseData(connection.createQuery(FETCH_ISLAND_PLOTS.getQuery())
                            .addParameter("pName", plName)
                            .executeAndFetchTable().rows(), connection);
                }

                @Override
                public void onCompletion(Exception exception) {
                    if (exception != null) {
                        resultOutput.accept(null);

                        exception.printStackTrace();
                        return;
                    }
                    putIslandUnspecified(islandList);

                    resultOutput.accept(islandList);
                }
            });

            return;
        }

        resultOutput.accept(new ArrayList<>(result.islandData.values()));
    }

    /**
     * Fetch a player data, this is an asynchronous operation.
     * The result output is being stored as it works as a function for the command.
     *
     * @param pl           The target player class
     * @param resultOutput The result
     */
    public void getPlayerData(Player pl, Consumer<PlayerData> resultOutput) {
        getPlayerData(pl.getName(), resultOutput);
    }

    /**
     * Fetch a player data, this is an asynchronous operation.
     * The result output is being stored as it works as a function for the command.
     *
     * @param player       The target player name
     * @param resultOutput The result
     */
    public void getPlayerData(String player, Consumer<PlayerData> resultOutput) {
        FastCacheData result = dataCache.stream().filter(i -> i.anyMatch(player)).findFirst().orElse(null);
        if (result == null || result.getPlayerData() == null) {
            plugin.getDatabase().pushQuery(new DatabaseManager.DatabaseImpl() {
                private PlayerData playerData = null;

                @Override
                public void executeQuery(Connection connection) {
                    Row plData;
                    Row clData;

                    Table data = connection.createQuery(FETCH_PLAYER_MAIN.getQuery())
                            .addParameter("plotOwner", player)
                            .executeAndFetchTable();

                    if (data.rows().isEmpty()) {
                        Utils.send("Du lieu trong");
                        return;
                    }

                    plData = data.rows().get(0);

                    data = connection.createQuery(FETCH_PLAYER_DATA.getQuery())
                            .addParameter("playerName", player)
                            .executeAndFetchTable();

                    if (!data.rows().isEmpty()) {
                        clData = data.rows().get(0);
                    } else {
                        clData = null;
                    }

                    playerData = PlayerData.fromRows(plData, clData);
                }

                @Override
                public void onCompletion(Exception err) {
                    if (err != null) {
                        return;
                    }

                    resultOutput.accept(playerData);
                    saveIntoDb(playerData);
                }
            });

            return;
        }

        resultOutput.accept(result.getPlayerData());
    }

    public void clearSavedCaches() {

        synchronized (dataCache) {
            dataCache.clear();
        }

        synchronized (storeSchedule){
            storeSchedule.clear();

            config.setAll(new ConfigSection());
            config.set("cacheVerificationId", cacheValidationId);

            config.save();
        }
    }

    public String getDefaultLocale(String plName) {
        FastCacheData object = dataCache.stream().filter(i -> i.anyMatch(plName)).findFirst().orElse(null);
        if (object == null || object.getPlayerData() == null) {
            // Forces the query to fetch all available information about this player.
            getPlayerData(plName, o -> {
            });

            return Settings.defaultLanguage;
        }

        return object.getPlayerData().getLocale();
    }

    /**
     * Inserts an island data arrays into the cache.
     * This operation queries all island list given in a variable and create a mutable
     * array which each data will be stored in there.
     * <p>
     * This method <bold>REMOVES</bold> all island data in the cache and <bold>REPLACES</bold> old
     * data with the given array. This method is also be used during startup.
     *
     * @param data The entire list of an island data.
     */
    private void putIslandUnspecified(List<IslandData> data) {
        final String[] playerNames = new String[data.size()]; // (n + 1) for a length
        final FastCacheData[] cacheObject = new FastCacheData[data.size()];

        for (IslandData pd : data) {
            int keyVal = 0;

            if (playerNames[0] == null || playerNames[0].isEmpty()) {
                playerNames[0] = pd.getPlotOwner();
                cacheObject[0] = new FastCacheData(pd.getPlotOwner());
            } else {
                // If the array of this key is null, then there is no player in this value.
                while (playerNames[keyVal] != null) {
                    keyVal++;
                }

                playerNames[keyVal] = pd.getPlotOwner();
                cacheObject[keyVal] = new FastCacheData(pd.getPlotOwner());
            }

            cacheObject[keyVal].addIslandData(pd);
        }

        dataCache.addAll(Arrays.asList(cacheObject));
    }

    /**
     * Adds an island data into this player cache data.
     * This method is used internally. Using this function may lead to severe critical errors.
     */
    public void addIslandIntoDb(String playerName, IslandData newData) {
        FastCacheData object = dataCache.stream().filter(i -> i.anyMatch(playerName)).findFirst().orElse(null);
        if (object == null) {
            object = new FastCacheData(playerName);
            object.addIslandData(newData);

            dataCache.add(object);
            return;
        }

        object.addIslandData(newData);
    }

    /**
     * Stores a data of this player into a database.
     * This field REPLACES this data into the cache.
     *
     * @param playerName The name of the player, or XUID if preferred.
     * @param data       List of IslandData for this player
     */
    public void saveIntoDb(String playerName, List<IslandData> data) {
        FastCacheData object = dataCache.stream().filter(i -> i.anyMatch(playerName)).findFirst().orElse(null);
        if (object == null) {
            object = new FastCacheData(playerName);
            object.setIslandData(data);

            dataCache.add(object);
            return;
        }

        object.setIslandData(data);
    }

    /**
     * Stores a data of this player into a database.
     * This field REPLACES this data into the cache.
     *
     * @param data PlayerData for this player
     */
    public void saveIntoDb(PlayerData data) {
        if (data == null) return;

        FastCacheData object = dataCache.stream().filter(i -> i.anyMatch(data.getPlayerName())).findFirst().orElse(null);
        if (object == null) {
            object = new FastCacheData(data.getPlayerName());
            object.setPlayerData(data);

            dataCache.add(object);
            return;
        }

        // Since ArrayList is modifiable, there is no need to replace the cache again into the list.
        object.setPlayerData(data);
    }

    private List<IslandData> parseData(List<Row> data, Connection connection) {
        List<IslandData> islandList = new ArrayList<>();

        for (Row o : data) {
            List<Row> row = connection.createQuery(FETCH_ISLAND_DATA.getQuery())
                    .addParameter("islandUniquePlotId", o.getInteger("islandUniqueId"))
                    .executeAndFetchTable().rows();

            if (row.isEmpty()) {
                islandList.add(IslandData.fromRows(o));
                continue;
            }

            islandList.add(IslandData.fromRows(o, row.get(0)));
        }

        return islandList;
    }

    public boolean deleteIsland(IslandData pd) {
        final boolean[] result = {false};
        plugin.getDatabase().pushQuery(new DatabaseManager.DatabaseImpl() {
            @Override
            public void executeQuery(Connection connection) {
                connection.createQuery(ISLAND_DELETE_DATA.getQuery())
                        .addParameter("id", pd.getIslandUniquePlotId())
                        .executeUpdate();
                connection.createQuery(ISLAND_DELETE_MAIN.getQuery())
                        .addParameter("id", pd.getIslandUniquePlotId())
                        .addParameter("player", pd.getPlotOwner())
                        .executeUpdate();
                FastCacheData object = dataCache.stream().filter(i -> i.anyMatch(pd.getPlotOwner())).findFirst().orElse(null);
                if (object != null) {
                    if (object.removeIslandByUID(pd.getIslandUniquePlotId())){
                        Utils.send("Delete island thanh cong");
                    }
                }
            }

            @Override
            public void onCompletion(Exception err) {
                if (err != null) {
                    err.printStackTrace();
                    return;
                }
                result[0] = true;
            }
        });
        return result[0];
    }

    private static class FastCacheData {

        private Timestamp lastUpdatedQuery;

        private String ownedBy;

        @Getter
        private Map<Integer, IslandData> islandData = new HashMap<>();
        @Getter
        private PlayerData playerData = null;

        FastCacheData(String playerName) {
            this.ownedBy = playerName;
        }

        void setIslandData(List<IslandData> dataList) {
            Map<Integer, IslandData> islandData = new HashMap<>();
            dataList.forEach(i -> islandData.put(i.getIslandUniquePlotId(), i));

            this.islandData = islandData;
        }

        public void addIslandData(IslandData newData) {
            Preconditions.checkState(!islandData.containsKey(newData.getIslandUniquePlotId()), "IslandData already exists in this cache.");
            updateTime();

            islandData.put(newData.getIslandUniquePlotId(), newData);
            Utils.send("Adding new island info on " + ownedBy);
        }

        void setPlayerData(PlayerData playerData) {
            Preconditions.checkState(this.playerData == null, "PlayerData cannot be null");
            updateTime();

            this.playerData = playerData;
        }

        boolean anyMatch(String pl) {
            updateTime();

            return playerData == null ? ownedBy.equalsIgnoreCase(pl) : playerData.getPlayerName().equalsIgnoreCase(pl);
        }

        boolean anyIslandUidMatch(int islandUId) {
            updateTime();

            return islandData.values().stream().anyMatch(o -> o.getIslandUniquePlotId() == islandUId);
        }

        public boolean removeIslandByUID(int islandUID){
            updateTime();
            if (islandData.containsKey(islandUID)) {
                islandData.remove(islandUID);
                return true;
            }
            return false;
        }

        boolean anyIslandMatch(int islandId) {
            updateTime();

            return islandData.values().stream().anyMatch(o -> o.getHomeCountId() == islandId);
        }

        IslandData getIslandByUId(int homeUIdKey) {
            updateTime();

            return islandData.values().stream().filter(i -> i.getIslandUniquePlotId() == homeUIdKey).findFirst().orElse(null);
        }

        IslandData getIslandById(int homeId) {
            updateTime();

            return islandData.values().stream().filter(i -> i.getHomeCountId() == homeId).findFirst().orElse(null);
        }

        void updateTime() {
            lastUpdatedQuery = Timestamp.from(Instant.now());

            storeSchedule.offer(this);
        }

        public String getDataIdentifier() {
            return ownedBy;
        }
    }
}
