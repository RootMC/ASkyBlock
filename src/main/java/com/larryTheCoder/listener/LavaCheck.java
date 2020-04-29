package com.larryTheCoder.listener;

import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockFromToEvent;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.level.Location;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.math.BlockFace;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.cache.IslandData;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static cn.nukkit.block.BlockID.*;

/**
 * @author tastybento
 * @author larryTheCoder
 */
public class LavaCheck implements Listener {

    private static Map<Integer, Map<Block, Double>> configChances = new HashMap<>();
    private final ASkyBlock plugin;

    public LavaCheck(ASkyBlock plugin) {
        this.plugin = plugin;
    }

    /**
     * Store the configured chances in %
     *
     * @param levelInt The level for the chances
     * @param chances  The block with custom chances
     */
    public static void storeChances(int levelInt, Map<Block, Double> chances) {
        configChances.put(levelInt, chances);
    }

    /**
     * Clear the magic cobble gen chances
     */
    public static void clearChances() {
        configChances.clear();
    }

    /**
     * Return the chances for this level and material
     *
     * @param level    The level for the block
     * @param material The block of the level
     * @return chance, or 0 if the level or material don't exist
     */
    public static double getConfigChances(Integer level, Block material) {
        return configChances.containsKey(level) && configChances.get(level).containsKey(material) ?
                configChances.get(level).get(material) : 0;
    }

    /**
     * Determines if a location is in the island world or not or in the new
     * nether if it is activated
     *
     * @param loc Location of the entity to be checked
     * @return true if in the island world
     */
    private boolean notInWorld(Location loc) {
        return !plugin.getLevels().contains(loc.getLevel().getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCleanstoneGen(BlockFromToEvent e) {
        // If magic cobble gen isn't used
        if (e.isCancelled()) {
            return;
        }
        if (!Settings.useMagicCobbleGen) {
            return;
        }
        // Do this only in the SkyBlock world
        if (notInWorld(e.getBlock().getLocation())) {
            return;
        }

        Block block = e.getTo();
        if (block.getId() == WATER || block.getId() == STILL_WATER) {
            Block flowedFrom = e.getBlock();
            if (!generatesCobble(flowedFrom)) {
                return;
            }

            IslandData pd = plugin.getFastCache().getIslandData(e.getBlock());
            if (pd != null && pd.getPlotOwner() != null) {
                plugin.getFastCache().getPlayerData(pd.getPlotOwner(), pd2 -> {
                    if (pd2 != null) invokeGenerate(e, pd2.getIslandLevel());
                    else invokeGenerate(e, Integer.MIN_VALUE);
                });
                return;
            }
            invokeGenerate(e, Integer.MIN_VALUE);
        }
    }

    public void invokeGenerate(BlockFromToEvent e, int islandLevel) {
        Block flowedFrom = e.getBlock();

        if (!Settings.magicCobbleGenChances.isEmpty()) {
            Map.Entry<Integer, TreeMap<Double, Block>> entry = Settings.magicCobbleGenChances.floorEntry(islandLevel);
            double maxValue = entry.getValue().lastKey();
            double rnd = Utils.randomDouble() * maxValue;
            Map.Entry<Double, Block> en = entry.getValue().ceilingEntry(rnd);

            if (en != null) {
                e.setCancelled();
                flowedFrom.getLevel().setBlock(flowedFrom, en.getValue());
                flowedFrom.getLevel().addSound(flowedFrom.add(0.5, 0.5, 0.5), Sound.RANDOM_FIZZ, 1, 2.6F + (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 0.8F);

                for (int i = 0; i < 8; ++i) {
                    flowedFrom.getLevel().addParticle(new SmokeParticle(flowedFrom.add(Math.random(), 1.2, Math.random())));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void update(BlockUpdateEvent event) {
        if (event.getBlock().getId() == WATER || event.getBlock().getId() == STILL_WATER) {

            if (!generatesCobble(event.getBlock())) {
                return;
            }

            IslandData pd = plugin.getFastCache().getIslandData(event.getBlock());
            if (pd != null && pd.getPlotOwner() != null) {
                plugin.getFastCache().getPlayerData(pd.getPlotOwner(), pd2 -> {
                    if (pd2 != null) invokeGenerate(event, pd2.getIslandLevel());
                    else invokeGenerate(event, Integer.MIN_VALUE);
                });
                return;
            }
            invokeGenerate(event, Integer.MIN_VALUE);
        }
    }

    public void invokeGenerate(BlockUpdateEvent e, int islandLevel) {
        Block flowedFrom = e.getBlock();

        if (!Settings.magicCobbleGenChances.isEmpty()) {
            Map.Entry<Integer, TreeMap<Double, Block>> entry = Settings.magicCobbleGenChances.floorEntry(islandLevel);
            double maxValue = entry.getValue().lastKey();
            double rnd = Utils.randomDouble() * maxValue;
            Map.Entry<Double, Block> en = entry.getValue().ceilingEntry(rnd);

            if (en != null) {
                e.setCancelled();
                flowedFrom.getLevel().setBlock(flowedFrom, en.getValue());
                flowedFrom.getLevel().addSound(flowedFrom.add(0.5, 0.5, 0.5), Sound.RANDOM_FIZZ, 1, 2.6F + (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 0.8F);

                for (int i = 0; i < 8; ++i) {
                    flowedFrom.getLevel().addParticle(new SmokeParticle(flowedFrom.add(Math.random(), 1.2, Math.random())));
                }
            }
        }
    }

    public boolean generatesCobble(Block toBlock) {
        return Stream.of(BlockFace.values())
                .anyMatch(face -> toBlock.getSide(face).getId() == FENCE);
    }

}
